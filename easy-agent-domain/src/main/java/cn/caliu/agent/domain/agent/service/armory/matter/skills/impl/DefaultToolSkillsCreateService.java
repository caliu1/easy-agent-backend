package cn.caliu.agent.domain.agent.service.armory.matter.skills.impl;

import cn.caliu.agent.domain.agent.model.valobj.AiAgentConfigTableVO;
import cn.caliu.agent.domain.agent.model.valobj.properties.SkillOssProperties;
import cn.caliu.agent.domain.agent.service.armory.matter.skills.IToolSkillsCreateService;
import com.aliyun.oss.OSS;
import com.aliyun.oss.OSSClientBuilder;
import com.aliyun.oss.model.ListObjectsV2Request;
import com.aliyun.oss.model.ListObjectsV2Result;
import com.aliyun.oss.model.OSSObject;
import com.aliyun.oss.model.OSSObjectSummary;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springaicommunity.agent.tools.SkillsTool;
import org.springaicommunity.agent.utils.MarkdownParser;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.function.FunctionToolCallback;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Skill 工具构造服务。
 * 支持三类来源：
 * 1) resource：classpath 下的资源目录；
 * 2) directory：本机目录；
 * 3) oss：直接从 OSS 读取并解析 SKILL.md（不依赖本地持久化目录）。
 */
@Slf4j
@Service
public class DefaultToolSkillsCreateService implements IToolSkillsCreateService {

    private static final String OSS_SCHEME_PREFIX = "oss://";
    private static final String SKILL_MARKDOWN_NAME = "SKILL.md";

    private static final String TOOL_DESCRIPTION_TEMPLATE = """
            Execute a skill within the main conversation
            
            <skills_instructions>
            When users ask you to perform tasks, check if any of the available skills below can help complete the task more effectively. Skills provide specialized capabilities and domain knowledge.
            
            How to use skills:
            - Invoke skills using this tool with the skill name only (no arguments)
            - When you invoke a skill, you will see <command-message>The "{name}" skill is loading</command-message>
            - The skill's prompt will expand and provide detailed instructions on how to complete the task
            
            NOTE: Response always starts start with the base directory of the skill execution environment. You can use this to retrieve additional files of call shell commands.
            Skill description follows after the base directory line.
            
            Important:
            - Only use skills listed in <available_skills> below
            - Do not invoke a skill that is already running
            </skills_instructions>
            
            <available_skills>
            %s
            </available_skills>
            """;

    @Resource
    private SkillOssProperties skillOssProperties;

    @Override
    public ToolCallback[] buildToolCallback(AiAgentConfigTableVO.Module.ChatModel.ToolSkills toolSkills) throws Exception {
        String type = StringUtils.defaultString(toolSkills.getType()).trim().toLowerCase();
        String path = StringUtils.trimToEmpty(toolSkills.getPath());

        List<ToolCallback> toolCallbackList = new ArrayList<>();

        if ("resource".equals(type)) {
            ToolCallback toolCallback = SkillsTool.builder()
                    .addSkillsResource(new ClassPathResource(path))
                    .build();
            toolCallbackList.add(toolCallback);
        } else if ("directory".equals(type)) {
            ToolCallback toolCallback = SkillsTool.builder()
                    .addSkillsDirectory(path)
                    .build();
            toolCallbackList.add(toolCallback);
        } else if ("oss".equals(type)) {
            ToolCallback toolCallback = buildOssSkillToolCallback(path);
            toolCallbackList.add(toolCallback);
        } else {
            throw new IllegalArgumentException("unsupported skill type: " + toolSkills.getType());
        }

        return toolCallbackList.toArray(new ToolCallback[0]);
    }

    /**
     * 直接从 OSS 读取 skill，并生成与 SkillsTool 等价的 ToolCallback。
     */
    private ToolCallback buildOssSkillToolCallback(String path) {
        if (!skillOssProperties.isEnabled()) {
            throw new IllegalStateException("OSS skill loader is disabled. Please set ai.agent.skill-oss.enabled=true");
        }

        OssLocation location = parseOssLocation(path);
        List<SkillsTool.Skill> skills = loadSkillsFromOss(location);
        if (skills.isEmpty()) {
            throw new IllegalStateException("no SKILL.md found in OSS path: " + path);
        }

        String skillXml = skills.stream()
                .map(SkillsTool.Skill::toXml)
                .collect(Collectors.joining("\n"));

        return FunctionToolCallback.builder("Skill", new SkillsTool.SkillsFunction(toSkillsMap(skills)))
                .description(TOOL_DESCRIPTION_TEMPLATE.formatted(skillXml))
                .inputType(SkillsTool.SkillsInput.class)
                .build();
    }

    private Map<String, SkillsTool.Skill> toSkillsMap(List<SkillsTool.Skill> skills) {
        Map<String, SkillsTool.Skill> skillsMap = new LinkedHashMap<>();
        for (SkillsTool.Skill skill : skills) {
            String name = skill.name();
            if (StringUtils.isBlank(name)) {
                throw new IllegalStateException("skill front matter name is blank");
            }
            // Keep the same behavior as upstream SkillsTool: later duplicate names override earlier ones.
            skillsMap.put(name, skill);
        }
        return skillsMap;
    }

    private List<SkillsTool.Skill> loadSkillsFromOss(OssLocation location) {
        OSS ossClient = null;
        try {
            ossClient = buildOssClient();
            List<String> skillObjectKeys = listSkillMarkdownKeys(ossClient, location);
            List<SkillsTool.Skill> skills = new ArrayList<>();

            for (String objectKey : skillObjectKeys) {
                String markdown = readOssObjectAsUtf8(ossClient, location.getBucket(), objectKey);
                MarkdownParser parser = new MarkdownParser(markdown);

                if (!parser.getFrontMatter().containsKey("name")) {
                    throw new IllegalStateException("SKILL.md front matter missing 'name': oss://" + location.getBucket() + "/" + objectKey);
                }

                String basePath = toOssBasePath(location.getBucket(), objectKey);
                SkillsTool.Skill skill = new SkillsTool.Skill(basePath, parser.getFrontMatter(), parser.getContent());
                skills.add(skill);
            }
            return skills;
        } finally {
            if (ossClient != null) {
                ossClient.shutdown();
            }
        }
    }

    private OSS buildOssClient() {
        String endpoint = normalizeEndpoint(skillOssProperties.getEndpoint());
        String accessKeyId = StringUtils.trimToEmpty(skillOssProperties.getAccessKeyId());
        String accessKeySecret = StringUtils.trimToEmpty(skillOssProperties.getAccessKeySecret());
        String securityToken = StringUtils.trimToNull(skillOssProperties.getSecurityToken());

        if (StringUtils.isBlank(endpoint) || StringUtils.isBlank(accessKeyId) || StringUtils.isBlank(accessKeySecret)) {
            throw new IllegalStateException("OSS skill config invalid. endpoint/accessKeyId/accessKeySecret are required");
        }

        OSSClientBuilder builder = new OSSClientBuilder();
        if (securityToken == null) {
            return builder.build(endpoint, accessKeyId, accessKeySecret);
        }
        return builder.build(endpoint, accessKeyId, accessKeySecret, securityToken);
    }

    private String normalizeEndpoint(String endpoint) {
        String trimmed = StringUtils.trimToEmpty(endpoint);
        if (StringUtils.isBlank(trimmed)) {
            return trimmed;
        }
        if (trimmed.startsWith("http://") || trimmed.startsWith("https://")) {
            return trimmed;
        }
        return "https://" + trimmed;
    }

    private OssLocation parseOssLocation(String path) {
        String raw = StringUtils.trimToEmpty(path);
        if (StringUtils.isBlank(raw)) {
            throw new IllegalArgumentException("oss skill path is blank");
        }

        if (raw.startsWith(OSS_SCHEME_PREFIX)) {
            String bucketAndKey = raw.substring(OSS_SCHEME_PREFIX.length());
            int slashIndex = bucketAndKey.indexOf('/');
            if (slashIndex <= 0) {
                return new OssLocation(bucketAndKey.trim(), "");
            }
            String bucket = bucketAndKey.substring(0, slashIndex).trim();
            String keyPrefix = normalizeObjectKey(bucketAndKey.substring(slashIndex + 1));
            return new OssLocation(bucket, keyPrefix);
        }

        String bucket = StringUtils.trimToEmpty(skillOssProperties.getDefaultBucket());
        if (StringUtils.isBlank(bucket)) {
            throw new IllegalArgumentException("oss skill path must use oss://bucket/prefix when defaultBucket is not configured");
        }
        return new OssLocation(bucket, normalizeObjectKey(raw));
    }

    private List<String> listSkillMarkdownKeys(OSS ossClient, OssLocation location) {
        String keyPrefix = normalizeObjectKey(location.getKeyPrefix());
        if (keyPrefix.endsWith("/" + SKILL_MARKDOWN_NAME) || SKILL_MARKDOWN_NAME.equals(keyPrefix)) {
            return List.of(keyPrefix);
        }

        String listPrefix = StringUtils.isBlank(keyPrefix) ? "" : keyPrefix + "/";
        int maxKeys = clampListMaxKeys(skillOssProperties.getListMaxKeys());

        List<String> skillKeys = new ArrayList<>();
        String continuationToken = null;
        boolean truncated;

        do {
            ListObjectsV2Request request = new ListObjectsV2Request(location.getBucket())
                    .withPrefix(listPrefix)
                    .withMaxKeys(maxKeys)
                    .withContinuationToken(continuationToken);

            ListObjectsV2Result result = ossClient.listObjectsV2(request);
            for (OSSObjectSummary summary : result.getObjectSummaries()) {
                String key = summary.getKey();
                if (StringUtils.endsWithIgnoreCase(key, "/" + SKILL_MARKDOWN_NAME) || SKILL_MARKDOWN_NAME.equalsIgnoreCase(key)) {
                    skillKeys.add(key);
                }
            }

            truncated = result.isTruncated();
            continuationToken = result.getNextContinuationToken();
        } while (truncated);

        return skillKeys.stream().sorted(Comparator.naturalOrder()).collect(Collectors.toList());
    }

    private int clampListMaxKeys(Integer input) {
        int fallback = 1000;
        if (input == null || input <= 0) {
            return fallback;
        }
        return Math.min(input, 1000);
    }

    private String readOssObjectAsUtf8(OSS ossClient, String bucket, String key) {
        try (OSSObject ossObject = ossClient.getObject(bucket, key);
             InputStream inputStream = ossObject.getObjectContent()) {
            return new String(inputStream.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new RuntimeException("read OSS skill failed: oss://" + bucket + "/" + key, e);
        }
    }

    private String toOssBasePath(String bucket, String skillMarkdownKey) {
        int lastSlash = skillMarkdownKey.lastIndexOf('/');
        String directory = lastSlash < 0 ? "" : skillMarkdownKey.substring(0, lastSlash);
        if (StringUtils.isBlank(directory)) {
            return OSS_SCHEME_PREFIX + bucket;
        }
        return OSS_SCHEME_PREFIX + bucket + "/" + directory;
    }

    private String normalizeObjectKey(String rawKey) {
        String normalized = StringUtils.trimToEmpty(rawKey).replace('\\', '/');
        while (normalized.startsWith("/")) {
            normalized = normalized.substring(1);
        }
        while (normalized.endsWith("/")) {
            normalized = normalized.substring(0, normalized.length() - 1);
        }
        return normalized;
    }

    @Data
    @AllArgsConstructor
    private static class OssLocation {
        private String bucket;
        private String keyPrefix;
    }

}
