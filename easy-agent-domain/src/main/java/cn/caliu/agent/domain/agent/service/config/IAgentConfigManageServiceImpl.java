package cn.caliu.agent.domain.agent.service.config;

import cn.caliu.agent.domain.agent.model.entity.AgentConfigEntity;
import cn.caliu.agent.domain.agent.model.valobj.AgentConfigPageQueryVO;
import cn.caliu.agent.domain.agent.model.valobj.AgentConfigPageQueryResult;
import cn.caliu.agent.domain.agent.model.valobj.AgentConfigVersionVO;
import cn.caliu.agent.domain.agent.model.valobj.AiAgentConfigTableVO;
import cn.caliu.agent.domain.agent.model.valobj.AiAgentRegisterVO;
import cn.caliu.agent.domain.agent.model.valobj.SkillAssetEntryVO;
import cn.caliu.agent.domain.agent.model.valobj.SkillAssetsResultVO;
import cn.caliu.agent.domain.agent.model.valobj.SkillImportResultVO;
import cn.caliu.agent.domain.agent.model.valobj.properties.AiAgentAutoConfigProperties;
import cn.caliu.agent.domain.agent.model.valobj.properties.SkillOssProperties;
import cn.caliu.agent.domain.agent.repository.IAgentConfigRepository;
import cn.caliu.agent.domain.agent.repository.IAgentConfigVersionRepository;
import cn.caliu.agent.domain.session.repository.IAgentSessionBindRepository;
import cn.caliu.agent.domain.agent.service.IAgentConfigManageService;
import cn.caliu.agent.domain.agent.service.runtime.AgentRuntimeAssembler;
import cn.caliu.agent.domain.agent.service.runtime.AgentRuntimeRegistry;
import cn.caliu.agent.types.enums.ResponseCode;
import cn.caliu.agent.types.exception.AppException;
import com.aliyun.oss.OSS;
import com.aliyun.oss.OSSClientBuilder;
import com.aliyun.oss.model.ListObjectsV2Request;
import com.aliyun.oss.model.ListObjectsV2Result;
import com.aliyun.oss.model.ObjectMetadata;
import com.aliyun.oss.model.OSSObject;
import com.aliyun.oss.model.OSSObjectSummary;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import javax.annotation.Resource;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ThreadLocalRandom;
import java.util.stream.Collectors;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/**
 * Agent 配置领域服务实现。
 *
 * 负责：
 * 1. 配置生命周期管理（草稿、发布、下线、回滚、删除）。
 * 2. 配置版本快照写入与恢复。
 * 3. 配置变更后运行时激活/下线编排（事务提交后执行）。
 */
@Slf4j
@Service
public class IAgentConfigManageServiceImpl implements IAgentConfigManageService {

    private static final String SYSTEM_OPERATOR = "system";
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    private static final int AGENT_ID_GENERATE_RETRY_TIMES = 10;
    private static final String AGENT_ID_PREFIX = "2";
    private static final String OSS_SCHEME_PREFIX = "oss://";
    private static final String SKILL_ROOT_PREFIX = "easyagent/skills/";
    private static final String LEGACY_SKILL_ROOT_PREFIX = "skills/";
    private static final long MAX_ZIP_SIZE_BYTES = 20L * 1024L * 1024L;
    private static final long MAX_ZIP_ENTRY_SIZE_BYTES = 10L * 1024L * 1024L;
    private static final long MAX_ZIP_TOTAL_UNCOMPRESSED_BYTES = 200L * 1024L * 1024L;
    private static final int MAX_ZIP_ENTRY_COUNT = 4000;
    private static final int MAX_SKILL_ASSET_COUNT = 500;
    private static final long MAX_SKILL_ASSET_TOTAL_SIZE = 20L * 1024L * 1024L;

    @Resource
    private IAgentConfigRepository agentConfigRepository;
    @Resource
    private IAgentConfigVersionRepository agentConfigVersionRepository;
    @Resource
    private IAgentSessionBindRepository agentSessionBindRepository;
    @Resource
    private AgentRuntimeRegistry agentRuntimeRegistry;
    @Resource
    private AgentRuntimeAssembler agentRuntimeAssembler;
    @Resource
    private AiAgentAutoConfigProperties aiAgentAutoConfigProperties;
    @Resource
    private SkillOssProperties skillOssProperties;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public synchronized AgentConfigEntity createAgentConfig(AgentConfigEntity request) {
        validateCreateRequest(request);

        String agentId = generateAvailableAgentId();
        String normalizedConfigJson = normalizeCreateConfigJson(request.getConfigJson(), agentId);

        ConfigMeta configMeta = resolveConfigMeta(
                agentId,
                normalizedConfigJson,
                request.getAppName(),
                request.getAgentName(),
                request.getAgentDesc()
        );

        long now = System.currentTimeMillis();
        AgentConfigEntity created = AgentConfigEntity.createUserDraft(
                agentId,
                configMeta.appName,
                configMeta.agentName,
                configMeta.agentDesc,
                normalizedConfigJson,
                request.getOperator(),
                request.getOwnerUserId(),
                now
        );

        agentConfigRepository.insert(created);
        insertVersionSnapshot(created);
        return requireConfig(agentId);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public synchronized AgentConfigEntity updateAgentConfig(AgentConfigEntity request) {
        validateUpdateRequest(request);

        String agentId = request.getAgentId().trim();
        AgentConfigEntity existed = requireConfig(agentId);

        String mergedConfigJson = choose(request.getConfigJson(), existed.getConfigJson());
        if (StringUtils.isBlank(mergedConfigJson)) {
            throw new AppException(ResponseCode.ILLEGAL_PARAMETER.getCode(), "configJson is blank");
        }

        ConfigMeta configMeta = resolveConfigMeta(
                agentId,
                mergedConfigJson,
                choose(request.getAppName(), existed.getAppName()),
                choose(request.getAgentName(), existed.getAgentName()),
                choose(request.getAgentDesc(), existed.getAgentDesc())
        );

        // 更新配置后自动启用：先装配校验，确保新版本可被运行时加载。
        AgentConfigEntity publishCandidate = AgentConfigEntity.builder()
                .agentId(agentId)
                .appName(configMeta.appName)
                .agentName(configMeta.agentName)
                .agentDesc(configMeta.agentDesc)
                .configJson(mergedConfigJson)
                .build();
        AiAgentRegisterVO registerVO = agentRuntimeAssembler.assemble(publishCandidate);

        long nextVersion = defaultLong(existed.getCurrentVersion(), 1L) + 1;
        AgentConfigEntity updated = existed.toPublishedUpdate(
                configMeta.appName,
                configMeta.agentName,
                configMeta.agentDesc,
                mergedConfigJson,
                nextVersion,
                request.getOperator(),
                System.currentTimeMillis()
        );

        agentConfigRepository.update(updated);
        insertVersionSnapshot(updated);
        runAfterCommit(() -> agentRuntimeRegistry.activate(agentId, nextVersion, registerVO));
        return requireConfig(agentId);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public synchronized boolean deleteAgentConfig(String agentId, String operator) {
        if (isBlank(agentId)) {
            throw new AppException(ResponseCode.ILLEGAL_PARAMETER.getCode(), "agentId is blank");
        }

        String key = agentId.trim();
        boolean deleted = agentConfigRepository.softDelete(key, trimToEmpty(operator));
        if (deleted) {
            // Delete DB bindings first, then clear runtime after transaction commit.
            agentSessionBindRepository.deleteByAgentId(key);
            runAfterCommit(() -> agentRuntimeRegistry.remove(key));
        }
        return deleted;
    }

    @Override
    public AgentConfigEntity queryAgentConfigDetail(String agentId) {
        if (isBlank(agentId)) {
            throw new AppException(ResponseCode.ILLEGAL_PARAMETER.getCode(), "agentId is blank");
        }
        return requireConfig(agentId.trim());
    }

    @Override
    public List<AgentConfigEntity> queryAgentPlazaList() {
        Map<String, AgentConfigEntity> merged = new LinkedHashMap<>();

        // Put official agents first, then allow user-published agents to override by id.
        for (AgentConfigEntity official : buildOfficialPlazaAgents()) {
            merged.put(official.getAgentId(), official);
        }
        for (AgentConfigEntity userAgent : agentConfigRepository.queryPlazaList()) {
            merged.put(userAgent.getAgentId(), userAgent);
        }
        return new ArrayList<>(merged.values());
    }

    @Override
    public AgentConfigPageQueryResult queryAgentConfigPage(AgentConfigPageQueryVO queryVO) {
        AgentConfigPageQueryVO safeQuery = queryVO == null
                ? AgentConfigPageQueryVO.builder().build()
                : queryVO;
        return agentConfigRepository.queryPage(safeQuery);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public synchronized AgentConfigEntity publishAgentConfig(String agentId, String operator) {
        if (isBlank(agentId)) {
            throw new AppException(ResponseCode.ILLEGAL_PARAMETER.getCode(), "agentId is blank");
        }

        String key = agentId.trim();
        AgentConfigEntity existed = requireConfig(key);
        if (isBlank(existed.getConfigJson())) {
            throw new AppException(ResponseCode.ILLEGAL_PARAMETER.getCode(), "configJson is blank");
        }

        ConfigMeta configMeta = resolveConfigMeta(
                key,
                existed.getConfigJson(),
                existed.getAppName(),
                existed.getAgentName(),
                existed.getAgentDesc()
        );
        AgentConfigEntity publishCandidate = AgentConfigEntity.builder()
                .agentId(key)
                .appName(configMeta.appName)
                .agentName(configMeta.agentName)
                .agentDesc(configMeta.agentDesc)
                .configJson(existed.getConfigJson())
                .build();

        // Validate assembly before publish to ensure runtime availability.
        AiAgentRegisterVO registerVO = agentRuntimeAssembler.assemble(publishCandidate);

        Long publishedVersion = defaultLong(existed.getCurrentVersion(), 1L);
        AgentConfigEntity published = existed.toPublished(
                configMeta.appName,
                configMeta.agentName,
                configMeta.agentDesc,
                publishedVersion,
                operator,
                System.currentTimeMillis()
        );

        agentConfigRepository.update(published);
        saveOrUpdateVersionSnapshot(published);
        runAfterCommit(() -> agentRuntimeRegistry.activate(key, publishedVersion, registerVO));
        return requireConfig(key);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public synchronized AgentConfigEntity offlineAgentConfig(String agentId, String operator) {
        if (isBlank(agentId)) {
            throw new AppException(ResponseCode.ILLEGAL_PARAMETER.getCode(), "agentId is blank");
        }

        String key = agentId.trim();
        AgentConfigEntity existed = requireConfig(key);

        AgentConfigEntity offline = existed.toOffline(operator, System.currentTimeMillis());

        agentConfigRepository.update(offline);
        saveOrUpdateVersionSnapshot(offline);
        // Switch runtime state after commit to keep DB/memory consistent.
        runAfterCommit(() -> agentRuntimeRegistry.deactivate(key));
        return requireConfig(key);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public synchronized AgentConfigEntity rollbackAgentConfig(String agentId, Long targetVersion, String operator) {
        if (isBlank(agentId)) {
            throw new AppException(ResponseCode.ILLEGAL_PARAMETER.getCode(), "agentId is blank");
        }
        if (targetVersion == null || targetVersion <= 0) {
            throw new AppException(ResponseCode.ILLEGAL_PARAMETER.getCode(), "targetVersion is invalid");
        }

        String key = agentId.trim();
        AgentConfigVersionVO targetSnapshot = agentConfigVersionRepository.queryByAgentIdAndVersion(key, targetVersion);
        if (targetSnapshot == null) {
            throw new AppException(ResponseCode.E0001.getCode(), "target version not found: " + targetVersion);
        }

        AgentConfigEntity existed = requireConfig(key);
        long nextVersion = defaultLong(existed.getCurrentVersion(), 1L) + 1;

        ConfigMeta configMeta = resolveConfigMeta(
                key,
                targetSnapshot.getConfigJson(),
                existed.getAppName(),
                existed.getAgentName(),
                existed.getAgentDesc()
        );

        AgentConfigEntity rollbackConfig = existed.toRollbackPublished(
                configMeta.appName,
                configMeta.agentName,
                configMeta.agentDesc,
                targetSnapshot.getConfigJson(),
                nextVersion,
                choose(operator, targetSnapshot.getOperator()),
                System.currentTimeMillis()
        );

        AiAgentRegisterVO registerVO = agentRuntimeAssembler.assemble(rollbackConfig);

        agentConfigRepository.update(rollbackConfig);
        insertVersionSnapshot(rollbackConfig);
        runAfterCommit(() -> agentRuntimeRegistry.activate(key, nextVersion, registerVO));
        return requireConfig(key);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public synchronized AgentConfigEntity publishAgentToPlaza(String agentId, String operator) {
        if (isBlank(agentId)) {
            throw new AppException(ResponseCode.ILLEGAL_PARAMETER.getCode(), "agentId is blank");
        }

        String key = agentId.trim();
        AgentConfigEntity existed = requireConfig(key);
        existed.assertCanOperatePlaza(operator);
        existed.assertCanBeListedInPlaza();

        long now = System.currentTimeMillis();
        AgentConfigEntity updated = existed.toPlazaPublished(operator, now, now);
        agentConfigRepository.update(updated);
        return requireConfig(key);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public synchronized AgentConfigEntity unpublishAgentFromPlaza(String agentId, String operator) {
        if (isBlank(agentId)) {
            throw new AppException(ResponseCode.ILLEGAL_PARAMETER.getCode(), "agentId is blank");
        }

        String key = agentId.trim();
        AgentConfigEntity existed = requireConfig(key);
        existed.assertCanOperatePlaza(operator);

        AgentConfigEntity updated = existed.toPlazaUnpublished(operator, System.currentTimeMillis());
        agentConfigRepository.update(updated);
        return requireConfig(key);
    }

    @Override
    public synchronized SkillImportResultVO importSkillZip(String operator, String fileName, byte[] zipBytes) {
        validateSkillZipInput(fileName, zipBytes);
        ensureSkillOssEnabled();

        List<ZipEntryPayload> zipEntries = readZipEntries(zipBytes);
        if (zipEntries.isEmpty()) {
            throw new AppException(ResponseCode.ILLEGAL_PARAMETER.getCode(), "zip has no files");
        }

        Set<String> skillDirectories = collectSkillDirectories(zipEntries);
        if (skillDirectories.isEmpty()) {
            throw new AppException(ResponseCode.ILLEGAL_PARAMETER.getCode(), "zip has no SKILL.md");
        }

        String bucket = StringUtils.trimToEmpty(skillOssProperties.getDefaultBucket());
        if (StringUtils.isBlank(bucket)) {
            throw new AppException(ResponseCode.ILLEGAL_PARAMETER.getCode(), "OSS default bucket is blank");
        }

        String uploadPrefix = buildUploadPrefix(fileName);
        OSS ossClient = buildOssClient();
        try {
            for (ZipEntryPayload zipEntry : zipEntries) {
                String objectKey = uploadPrefix + zipEntry.path;
                ObjectMetadata metadata = new ObjectMetadata();
                metadata.setContentLength(zipEntry.content.length);
                ossClient.putObject(bucket, objectKey, new ByteArrayInputStream(zipEntry.content), metadata);
            }
        } catch (Exception e) {
            throw new AppException(ResponseCode.UN_ERROR.getCode(), "upload skill zip to OSS failed: " + e.getMessage());
        } finally {
            try {
                ossClient.shutdown();
            } catch (Exception ignored) {
            }
        }

        List<SkillImportResultVO.ToolSkillLocationVO> toolSkillsList = new ArrayList<>();
        for (String skillDirectory : skillDirectories) {
            String locationPrefix = StringUtils.isBlank(skillDirectory)
                    ? uploadPrefix
                    : uploadPrefix + skillDirectory + "/";

            // 统一返回相对 default bucket 的路径，格式固定在 easyagent/skills/ 下。
            String ossPath = trimTailSlash(locationPrefix);
            String skillName = StringUtils.isBlank(skillDirectory)
                    ? removeZipExtension(fileName)
                    : skillDirectory.substring(skillDirectory.lastIndexOf('/') + 1);

            toolSkillsList.add(SkillImportResultVO.ToolSkillLocationVO.builder()
                    .type("oss")
                    .path(ossPath)
                    .skillName(skillName)
                    .build());
        }

        return SkillImportResultVO.builder()
                .bucket(bucket)
                .prefix(trimTailSlash(uploadPrefix))
                .fileCount(zipEntries.size())
                .skillCount(toolSkillsList.size())
                .toolSkillsList(toolSkillsList)
                .build();
    }

    @Override
    public synchronized SkillImportResultVO saveSkillAssets(String operator, String rootFolder, List<SkillAssetEntryVO> entries) {
        ensureSkillOssEnabled();

        String normalizedRootFolder = normalizeSkillRootFolder(rootFolder);
        if (StringUtils.isBlank(normalizedRootFolder)) {
            throw new AppException(ResponseCode.ILLEGAL_PARAMETER.getCode(), "rootFolder is blank");
        }

        List<SkillAssetEntryVO> normalizedEntries = normalizeSkillAssetEntries(entries);
        validateSkillAssetEntries(normalizedEntries);

        String bucket = StringUtils.trimToEmpty(skillOssProperties.getDefaultBucket());
        if (StringUtils.isBlank(bucket)) {
            throw new AppException(ResponseCode.ILLEGAL_PARAMETER.getCode(), "OSS default bucket is blank");
        }

        String prefix = SKILL_ROOT_PREFIX + normalizedRootFolder + "/";
        OSS ossClient = buildOssClient();
        try {
            cleanOssPrefix(ossClient, bucket, prefix);
            uploadSkillAssets(ossClient, bucket, prefix, normalizedEntries);
        } catch (AppException e) {
            throw e;
        } catch (Exception e) {
            throw new AppException(ResponseCode.UN_ERROR.getCode(), "save skill assets failed: " + e.getMessage());
        } finally {
            try {
                ossClient.shutdown();
            } catch (Exception ignored) {
            }
        }

        List<SkillImportResultVO.ToolSkillLocationVO> locations = List.of(
                SkillImportResultVO.ToolSkillLocationVO.builder()
                        .type("oss")
                        .path(trimTailSlash(prefix))
                        .skillName(normalizedRootFolder)
                        .build()
        );

        return SkillImportResultVO.builder()
                .bucket(bucket)
                .prefix(trimTailSlash(prefix))
                .fileCount((int) normalizedEntries.stream().filter(this::isFileEntry).count())
                .skillCount(1)
                .toolSkillsList(locations)
                .build();
    }

    @Override
    public synchronized SkillAssetsResultVO querySkillAssets(String ossPath) {
        ensureSkillOssEnabled();

        OssLocation location = parseOssLocation(ossPath);
        String normalizedPrefix = normalizeObjectKey(location.getKeyPrefix());
        if (StringUtils.isBlank(normalizedPrefix)) {
            throw new AppException(ResponseCode.ILLEGAL_PARAMETER.getCode(), "ossPath is blank");
        }
        if (normalizedPrefix.startsWith(LEGACY_SKILL_ROOT_PREFIX)) {
            throw new AppException(ResponseCode.ILLEGAL_PARAMETER.getCode(),
                    "legacy skill path is not supported, use easyagent/skills/{skillName}");
        }
        if (!normalizedPrefix.startsWith(SKILL_ROOT_PREFIX)) {
            normalizedPrefix = SKILL_ROOT_PREFIX + normalizedPrefix;
        }
        String listPrefix = normalizedPrefix.endsWith("/") ? normalizedPrefix : normalizedPrefix + "/";

        OSS ossClient = buildOssClient();
        try {
            List<SkillAssetEntryVO> entries = readSkillAssetsFromOss(ossClient, location.getBucket(), listPrefix);
            int fileCount = (int) entries.stream().filter(this::isFileEntry).count();
            int folderCount = entries.size() - fileCount;

            return SkillAssetsResultVO.builder()
                    .bucket(location.getBucket())
                    .prefix(trimTailSlash(listPrefix))
                    .fileCount(fileCount)
                    .folderCount(folderCount)
                    .entries(entries)
                    .build();
        } catch (AppException e) {
            throw e;
        } catch (Exception e) {
            throw new AppException(ResponseCode.UN_ERROR.getCode(), "query skill assets failed: " + e.getMessage());
        } finally {
            try {
                ossClient.shutdown();
            } catch (Exception ignored) {
            }
        }
    }

    @Override
    public synchronized int reloadPublishedAgentRuntime() {
        List<AgentConfigEntity> publishedConfigs = agentConfigRepository.queryPublishedList();
        Set<String> systemAgentIds = collectSystemAgentIds();

        if (publishedConfigs == null || publishedConfigs.isEmpty()) {
            return 0;
        }

        int loaded = 0;
        for (AgentConfigEntity config : publishedConfigs) {
            if (systemAgentIds.contains(config.getAgentId())) {
                log.info("skip db runtime activation because system config takes precedence. agentId={}", config.getAgentId());
                continue;
            }

            try {
                ConfigMeta configMeta = resolveConfigMeta(
                        config.getAgentId(),
                        config.getConfigJson(),
                        config.getAppName(),
                        config.getAgentName(),
                        config.getAgentDesc()
                );

                AgentConfigEntity candidate = AgentConfigEntity.builder()
                        .agentId(config.getAgentId())
                        .appName(configMeta.appName)
                        .agentName(configMeta.agentName)
                        .agentDesc(configMeta.agentDesc)
                        .configJson(config.getConfigJson())
                        .build();

                AiAgentRegisterVO registerVO = agentRuntimeAssembler.assemble(candidate);
                long fallbackVersion = defaultLong(config.getCurrentVersion(), 1L);
                Long activeVersion = defaultLong(config.getPublishedVersion(), fallbackVersion);
                agentRuntimeRegistry.activate(config.getAgentId(), activeVersion, registerVO);
                loaded++;
            } catch (Exception e) {
                log.error("reload published runtime failed. agentId={}", config.getAgentId(), e);
            }
        }
        return loaded;
    }

    /**
     * 收集内置（yml）Agent 的 id，用于运行时装配时做优先级保护。
     */
    private Set<String> collectSystemAgentIds() {
        Set<String> systemAgentIds = new LinkedHashSet<>();
        Map<String, AiAgentConfigTableVO> tables = aiAgentAutoConfigProperties.getTables();
        if (tables == null || tables.isEmpty()) {
            return systemAgentIds;
        }

        for (AiAgentConfigTableVO table : tables.values()) {
            if (table == null || table.getAgent() == null) {
                continue;
            }
            String agentId = StringUtils.trimToEmpty(table.getAgent().getAgentId());
            if (StringUtils.isNotBlank(agentId)) {
                systemAgentIds.add(agentId);
            }
        }
        return systemAgentIds;
    }

    private AgentConfigEntity requireConfig(String agentId) {
        AgentConfigEntity config = agentConfigRepository.queryByAgentId(agentId);
        if (config == null) {
            throw new AppException(ResponseCode.E0001.getCode(), "agent config not found: " + agentId);
        }
        return config;
    }

    private void insertVersionSnapshot(AgentConfigEntity config) {
        agentConfigVersionRepository.insert(AgentConfigVersionVO.builder()
                .agentId(config.getAgentId())
                .version(config.getCurrentVersion())
                .status(config.getStatus())
                .configJson(config.getConfigJson())
                .operator(config.getOperator())
                .createTime(System.currentTimeMillis())
                .build());
    }

    private void saveOrUpdateVersionSnapshot(AgentConfigEntity config) {
        agentConfigVersionRepository.saveOrUpdate(AgentConfigVersionVO.builder()
                .agentId(config.getAgentId())
                .version(config.getCurrentVersion())
                .status(config.getStatus())
                .configJson(config.getConfigJson())
                .operator(config.getOperator())
                .createTime(System.currentTimeMillis())
                .build());
    }

    private void runAfterCommit(Runnable runnable) {
        // Defer runtime mutation until transaction commit when possible.
        if (TransactionSynchronizationManager.isActualTransactionActive()
                && TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    runnable.run();
                }
            });
            return;
        }
        // Execute directly when there is no active transaction.
        runnable.run();
    }

    private List<AgentConfigEntity> buildOfficialPlazaAgents() {
        Map<String, AiAgentConfigTableVO> tables = aiAgentAutoConfigProperties.getTables();
        if (tables == null || tables.isEmpty()) {
            return new ArrayList<>();
        }

        List<AgentConfigEntity> officialAgents = new ArrayList<>();
        for (AiAgentConfigTableVO table : tables.values()) {
            if (table == null || table.getAgent() == null || isBlank(table.getAgent().getAgentId())) {
                continue;
            }

            AiAgentConfigTableVO.Agent agent = table.getAgent();
            officialAgents.add(AgentConfigEntity.builder()
                    .agentId(agent.getAgentId())
                    .appName(StringUtils.defaultString(table.getAppName()))
                    .agentName(StringUtils.defaultString(agent.getAgentName()))
                    .agentDesc(StringUtils.defaultString(agent.getAgentDesc()))
                    .configJson(null)
                    .status(AgentConfigEntity.STATUS_PUBLISHED)
                    .currentVersion(0L)
                    .publishedVersion(0L)
                    .operator(SYSTEM_OPERATOR)
                    .ownerUserId(SYSTEM_OPERATOR)
                    .sourceType(AgentConfigEntity.SOURCE_OFFICIAL)
                    .plazaStatus(AgentConfigEntity.PLAZA_ON)
                    .plazaPublishTime(null)
                    .createTime(null)
                    .updateTime(null)
                    .build());
        }
        return officialAgents;
    }

    private void validateSkillZipInput(String fileName, byte[] zipBytes) {
        if (StringUtils.isBlank(fileName) || !StringUtils.endsWithIgnoreCase(fileName.trim(), ".zip")) {
            throw new AppException(ResponseCode.ILLEGAL_PARAMETER.getCode(), "file must be a .zip package");
        }
        if (zipBytes == null || zipBytes.length == 0) {
            throw new AppException(ResponseCode.ILLEGAL_PARAMETER.getCode(), "zip file is empty");
        }
        if (zipBytes.length > MAX_ZIP_SIZE_BYTES) {
            throw new AppException(ResponseCode.ILLEGAL_PARAMETER.getCode(), "zip file is too large (max 20MB)");
        }
    }

    private void ensureSkillOssEnabled() {
        if (!skillOssProperties.isEnabled()) {
            throw new AppException(ResponseCode.ILLEGAL_PARAMETER.getCode(), "OSS skill import is disabled");
        }
    }

    private List<ZipEntryPayload> readZipEntries(byte[] zipBytes) {
        List<ZipEntryPayload> payloads = new ArrayList<>();
        long totalUncompressedSize = 0L;
        int entryCount = 0;

        try (ZipInputStream zipInputStream = new ZipInputStream(new ByteArrayInputStream(zipBytes))) {
            ZipEntry zipEntry;
            while ((zipEntry = zipInputStream.getNextEntry()) != null) {
                if (zipEntry.isDirectory()) {
                    continue;
                }

                entryCount++;
                if (entryCount > MAX_ZIP_ENTRY_COUNT) {
                    throw new AppException(ResponseCode.ILLEGAL_PARAMETER.getCode(), "zip entry count exceeds limit");
                }

                String normalizedPath = normalizeZipEntryPath(zipEntry.getName());
                byte[] content = readZipEntryContent(zipInputStream);
                totalUncompressedSize += content.length;
                if (totalUncompressedSize > MAX_ZIP_TOTAL_UNCOMPRESSED_BYTES) {
                    throw new AppException(ResponseCode.ILLEGAL_PARAMETER.getCode(), "zip content is too large after unzip");
                }

                payloads.add(new ZipEntryPayload(normalizedPath, content));
            }
        } catch (AppException e) {
            throw e;
        } catch (Exception e) {
            throw new AppException(ResponseCode.ILLEGAL_PARAMETER.getCode(), "invalid zip file: " + e.getMessage());
        }

        return payloads;
    }

    private String normalizeZipEntryPath(String originalName) {
        String normalized = StringUtils.trimToEmpty(originalName).replace('\\', '/');
        while (normalized.startsWith("./")) {
            normalized = normalized.substring(2);
        }
        normalized = StringUtils.stripStart(normalized, "/");

        if (StringUtils.isBlank(normalized)) {
            throw new AppException(ResponseCode.ILLEGAL_PARAMETER.getCode(), "zip contains empty file path");
        }

        String[] segments = normalized.split("/");
        for (String segment : segments) {
            if (StringUtils.isBlank(segment) || ".".equals(segment) || "..".equals(segment)) {
                throw new AppException(ResponseCode.ILLEGAL_PARAMETER.getCode(), "zip contains unsafe file path: " + originalName);
            }
        }

        return String.join("/", Arrays.asList(segments));
    }

    private byte[] readZipEntryContent(ZipInputStream zipInputStream) throws Exception {
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        byte[] buffer = new byte[8192];
        long currentEntrySize = 0L;

        int readSize;
        while ((readSize = zipInputStream.read(buffer)) != -1) {
            currentEntrySize += readSize;
            if (currentEntrySize > MAX_ZIP_ENTRY_SIZE_BYTES) {
                throw new AppException(ResponseCode.ILLEGAL_PARAMETER.getCode(), "single file in zip is too large");
            }
            outputStream.write(buffer, 0, readSize);
        }

        return outputStream.toByteArray();
    }

    private Set<String> collectSkillDirectories(List<ZipEntryPayload> zipEntries) {
        Set<String> directories = new LinkedHashSet<>();
        for (ZipEntryPayload zipEntry : zipEntries) {
            String normalizedPath = zipEntry.path;
            String lowerPath = normalizedPath.toLowerCase();
            if (!"skill.md".equals(lowerPath) && !lowerPath.endsWith("/skill.md")) {
                continue;
            }

            int slashIndex = normalizedPath.lastIndexOf('/');
            String directory = slashIndex < 0 ? "" : normalizedPath.substring(0, slashIndex);
            directories.add(directory);
        }
        return directories;
    }

    private String normalizeSkillRootFolder(String rootFolder) {
        String normalized = StringUtils.trimToEmpty(rootFolder).replace('\\', '/');
        while (normalized.startsWith("/")) {
            normalized = normalized.substring(1);
        }
        while (normalized.endsWith("/")) {
            normalized = normalized.substring(0, normalized.length() - 1);
        }
        if (normalized.startsWith(SKILL_ROOT_PREFIX)) {
            normalized = normalized.substring(SKILL_ROOT_PREFIX.length());
        } else if (normalized.startsWith(LEGACY_SKILL_ROOT_PREFIX)) {
            throw new AppException(ResponseCode.ILLEGAL_PARAMETER.getCode(),
                    "legacy skill path is not supported, use easyagent/skills/{skillName}");
        }

        if (StringUtils.isBlank(normalized) || normalized.contains("/") || ".".equals(normalized) || "..".equals(normalized)) {
            throw new AppException(ResponseCode.ILLEGAL_PARAMETER.getCode(), "invalid rootFolder");
        }
        return normalized;
    }

    private List<SkillAssetEntryVO> normalizeSkillAssetEntries(List<SkillAssetEntryVO> entries) {
        if (entries == null || entries.isEmpty()) {
            return new ArrayList<>();
        }

        List<SkillAssetEntryVO> normalized = new ArrayList<>();
        for (SkillAssetEntryVO entry : entries) {
            if (entry == null) continue;
            String kind = StringUtils.defaultString(entry.getKind()).trim().toLowerCase();
            if (!"file".equals(kind) && !"folder".equals(kind)) {
                continue;
            }
            String path = normalizeRelativeAssetPath(entry.getPath(), "folder".equals(kind));
            if (StringUtils.isBlank(path)) continue;

            normalized.add(SkillAssetEntryVO.builder()
                    .kind(kind)
                    .path(path)
                    .content("file".equals(kind) ? StringUtils.defaultString(entry.getContent()) : "")
                    .build());
        }
        return normalized;
    }

    private void validateSkillAssetEntries(List<SkillAssetEntryVO> entries) {
        if (entries.isEmpty()) {
            throw new AppException(ResponseCode.ILLEGAL_PARAMETER.getCode(), "skill entries is empty");
        }
        if (entries.size() > MAX_SKILL_ASSET_COUNT) {
            throw new AppException(ResponseCode.ILLEGAL_PARAMETER.getCode(), "too many skill entries");
        }

        boolean hasRootSkillMarkdown = false;
        long totalSize = 0L;
        Set<String> dedupKeys = new LinkedHashSet<>();

        for (SkillAssetEntryVO entry : entries) {
            String dedupKey = entry.getKind() + ":" + entry.getPath();
            if (dedupKeys.contains(dedupKey)) {
                continue;
            }
            dedupKeys.add(dedupKey);

            if (isFileEntry(entry)) {
                String content = StringUtils.defaultString(entry.getContent());
                totalSize += content.getBytes().length;
                if (totalSize > MAX_SKILL_ASSET_TOTAL_SIZE) {
                    throw new AppException(ResponseCode.ILLEGAL_PARAMETER.getCode(), "skill content is too large");
                }
                if ("SKILL.md".equals(entry.getPath())) {
                    hasRootSkillMarkdown = true;
                }
            }
        }

        if (!hasRootSkillMarkdown) {
            throw new AppException(ResponseCode.ILLEGAL_PARAMETER.getCode(), "first level must contain SKILL.md");
        }
    }

    private boolean isFileEntry(SkillAssetEntryVO entry) {
        return "file".equals(StringUtils.defaultString(entry.getKind()).trim().toLowerCase());
    }

    private String normalizeRelativeAssetPath(String rawPath, boolean isFolder) {
        String normalized = StringUtils.trimToEmpty(rawPath).replace('\\', '/');
        while (normalized.startsWith("./")) {
            normalized = normalized.substring(2);
        }
        while (normalized.startsWith("/")) {
            normalized = normalized.substring(1);
        }
        while (normalized.endsWith("/")) {
            normalized = normalized.substring(0, normalized.length() - 1);
        }
        if (StringUtils.isBlank(normalized)) {
            return "";
        }

        String[] segments = normalized.split("/");
        for (String segment : segments) {
            if (StringUtils.isBlank(segment) || ".".equals(segment) || "..".equals(segment)) {
                throw new AppException(ResponseCode.ILLEGAL_PARAMETER.getCode(), "invalid asset path: " + rawPath);
            }
        }

        if (isFolder && "SKILL.md".equalsIgnoreCase(normalized)) {
            throw new AppException(ResponseCode.ILLEGAL_PARAMETER.getCode(), "SKILL.md must be a file");
        }
        return String.join("/", Arrays.asList(segments));
    }

    private OssLocation parseOssLocation(String path) {
        String raw = StringUtils.trimToEmpty(path);
        if (StringUtils.isBlank(raw)) {
            throw new AppException(ResponseCode.ILLEGAL_PARAMETER.getCode(), "ossPath is blank");
        }

        if (raw.startsWith(OSS_SCHEME_PREFIX)) {
            String bucketAndKey = raw.substring(OSS_SCHEME_PREFIX.length());
            int slashIndex = bucketAndKey.indexOf('/');
            if (slashIndex <= 0) {
                throw new AppException(ResponseCode.ILLEGAL_PARAMETER.getCode(), "ossPath is invalid");
            }
            String bucket = bucketAndKey.substring(0, slashIndex).trim();
            String keyPrefix = normalizeObjectKey(bucketAndKey.substring(slashIndex + 1));
            if (StringUtils.isBlank(bucket) || StringUtils.isBlank(keyPrefix)) {
                throw new AppException(ResponseCode.ILLEGAL_PARAMETER.getCode(), "ossPath is invalid");
            }
            return new OssLocation(bucket, keyPrefix);
        }

        String defaultBucket = StringUtils.trimToEmpty(skillOssProperties.getDefaultBucket());
        if (StringUtils.isBlank(defaultBucket)) {
            throw new AppException(ResponseCode.ILLEGAL_PARAMETER.getCode(),
                    "ossPath must use oss://bucket/prefix when defaultBucket is not configured");
        }
        return new OssLocation(defaultBucket, normalizeObjectKey(raw));
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

    private List<SkillAssetEntryVO> readSkillAssetsFromOss(OSS ossClient, String bucket, String listPrefix) {
        Map<String, SkillAssetEntryVO> entryMap = new LinkedHashMap<>();
        String continuationToken = null;
        boolean truncated;
        long totalBytes = 0L;
        int maxKeys = clampListMaxKeys(skillOssProperties.getListMaxKeys());

        do {
            ListObjectsV2Request request = new ListObjectsV2Request(bucket)
                    .withPrefix(listPrefix)
                    .withMaxKeys(maxKeys)
                    .withContinuationToken(continuationToken);
            ListObjectsV2Result result = ossClient.listObjectsV2(request);
            for (OSSObjectSummary summary : result.getObjectSummaries()) {
                String key = summary.getKey();
                if (StringUtils.equals(key, listPrefix) || !StringUtils.startsWith(key, listPrefix)) {
                    continue;
                }

                String relativePath = key.substring(listPrefix.length());
                boolean isFolder = key.endsWith("/");
                relativePath = isFolder ? StringUtils.stripEnd(relativePath, "/") : relativePath;
                if (StringUtils.isBlank(relativePath)) {
                    continue;
                }

                if (isFolder) {
                    addFolderPathAndParents(entryMap, relativePath);
                } else {
                    addFolderParentsForFile(entryMap, relativePath);
                    String content = readOssObjectAsUtf8(ossClient, bucket, key);
                    totalBytes += content.getBytes(StandardCharsets.UTF_8).length;
                    if (totalBytes > MAX_SKILL_ASSET_TOTAL_SIZE) {
                        throw new AppException(ResponseCode.ILLEGAL_PARAMETER.getCode(), "skill content is too large");
                    }
                    entryMap.put("file:" + relativePath, SkillAssetEntryVO.builder()
                            .kind("file")
                            .path(relativePath)
                            .content(content)
                            .build());
                }

                if (entryMap.size() > MAX_SKILL_ASSET_COUNT) {
                    throw new AppException(ResponseCode.ILLEGAL_PARAMETER.getCode(), "too many skill entries");
                }
            }

            truncated = result.isTruncated();
            continuationToken = result.getNextContinuationToken();
        } while (truncated);

        return entryMap.values().stream()
                .sorted(Comparator
                        .comparing((SkillAssetEntryVO item) -> "folder".equals(item.getKind()) ? 0 : 1)
                        .thenComparing(SkillAssetEntryVO::getPath))
                .collect(Collectors.toList());
    }

    private void addFolderParentsForFile(Map<String, SkillAssetEntryVO> entryMap, String filePath) {
        int lastSlash = filePath.lastIndexOf('/');
        if (lastSlash <= 0) {
            return;
        }
        addFolderPathAndParents(entryMap, filePath.substring(0, lastSlash));
    }

    private void addFolderPathAndParents(Map<String, SkillAssetEntryVO> entryMap, String folderPath) {
        String normalized = normalizeObjectKey(folderPath);
        if (StringUtils.isBlank(normalized)) {
            return;
        }
        String[] segments = normalized.split("/");
        StringBuilder current = new StringBuilder();
        for (String segment : segments) {
            if (StringUtils.isBlank(segment)) continue;
            if (current.length() > 0) {
                current.append('/');
            }
            current.append(segment);
            String path = current.toString();
            entryMap.put("folder:" + path, SkillAssetEntryVO.builder()
                    .kind("folder")
                    .path(path)
                    .content("")
                    .build());
        }
    }

    private String readOssObjectAsUtf8(OSS ossClient, String bucket, String key) {
        try (OSSObject ossObject = ossClient.getObject(bucket, key);
             InputStream inputStream = ossObject.getObjectContent()) {
            return new String(inputStream.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new AppException(ResponseCode.UN_ERROR.getCode(), "read OSS object failed: oss://" + bucket + "/" + key);
        }
    }

    private int clampListMaxKeys(Integer input) {
        int fallback = 1000;
        if (input == null || input <= 0) {
            return fallback;
        }
        return Math.min(input, 1000);
    }

    private void cleanOssPrefix(OSS ossClient, String bucket, String prefix) {
        String continuationToken = null;
        boolean truncated;
        do {
            ListObjectsV2Request request = new ListObjectsV2Request(bucket)
                    .withPrefix(prefix)
                    .withMaxKeys(1000)
                    .withContinuationToken(continuationToken);
            ListObjectsV2Result result = ossClient.listObjectsV2(request);
            for (OSSObjectSummary summary : result.getObjectSummaries()) {
                ossClient.deleteObject(bucket, summary.getKey());
            }
            truncated = result.isTruncated();
            continuationToken = result.getNextContinuationToken();
        } while (truncated);
    }

    private void uploadSkillAssets(OSS ossClient, String bucket, String prefix, List<SkillAssetEntryVO> entries) {
        Set<String> uploaded = new LinkedHashSet<>();
        for (SkillAssetEntryVO entry : entries) {
            String key = prefix + entry.getPath();
            if (uploaded.contains(entry.getKind() + ":" + key)) {
                continue;
            }

            if (isFileEntry(entry)) {
                byte[] bytes = StringUtils.defaultString(entry.getContent()).getBytes();
                ObjectMetadata metadata = new ObjectMetadata();
                metadata.setContentLength(bytes.length);
                ossClient.putObject(bucket, key, new ByteArrayInputStream(bytes), metadata);
            } else {
                String folderKey = key.endsWith("/") ? key : key + "/";
                ossClient.putObject(bucket, folderKey, new ByteArrayInputStream(new byte[0]));
            }
            uploaded.add(entry.getKind() + ":" + key);
        }
    }

    private String buildUploadPrefix(String fileName) {
        String packageSegment = sanitizePathSegment(removeZipExtension(fileName));
        long now = System.currentTimeMillis();
        int random = ThreadLocalRandom.current().nextInt(1000, 10000);
        // 统一落到 OSS easyagent/skills 根目录，避免分散到其他前缀。
        return SKILL_ROOT_PREFIX + packageSegment + "-" + now + "-" + random + "/";
    }

    private String sanitizePathSegment(String raw) {
        String normalized = StringUtils.defaultString(raw).trim();
        if (normalized.isEmpty()) {
            return "default";
        }
        normalized = normalized.replaceAll("[^a-zA-Z0-9._-]", "-");
        normalized = normalized.replaceAll("-{2,}", "-");
        normalized = normalized.replaceAll("^[.-]+", "");
        normalized = normalized.replaceAll("[.-]+$", "");
        return normalized.isEmpty() ? "default" : normalized;
    }

    private String removeZipExtension(String fileName) {
        String normalized = StringUtils.defaultString(fileName).trim();
        if (normalized.toLowerCase().endsWith(".zip")) {
            return normalized.substring(0, normalized.length() - 4);
        }
        return normalized;
    }

    private OSS buildOssClient() {
        String endpoint = StringUtils.trimToEmpty(skillOssProperties.getEndpoint());
        String accessKeyId = StringUtils.trimToEmpty(skillOssProperties.getAccessKeyId());
        String accessKeySecret = StringUtils.trimToEmpty(skillOssProperties.getAccessKeySecret());
        String securityToken = StringUtils.trimToEmpty(skillOssProperties.getSecurityToken());

        if (StringUtils.isAnyBlank(endpoint, accessKeyId, accessKeySecret)) {
            throw new AppException(
                    ResponseCode.ILLEGAL_PARAMETER.getCode(),
                    "OSS config invalid: endpoint/accessKeyId/accessKeySecret required"
            );
        }

        OSSClientBuilder builder = new OSSClientBuilder();
        if (StringUtils.isNotBlank(securityToken)) {
            return builder.build(endpoint, accessKeyId, accessKeySecret, securityToken);
        }
        return builder.build(endpoint, accessKeyId, accessKeySecret);
    }

    private String trimTailSlash(String value) {
        String normalized = StringUtils.trimToEmpty(value);
        while (normalized.endsWith("/")) {
            normalized = normalized.substring(0, normalized.length() - 1);
        }
        return normalized;
    }

    /**
     * 解析并归一化 Agent 配置元信息（appName / agentName / agentDesc）。
     *
     * 设计目的：
     * 1. 统一处理“请求参数”与“configJson 内字段”的优先级合并，避免在 create/update/publish/rollback/reload 多处重复逻辑。
     * 2. 在进入装配与持久化前，提前完成关键一致性校验（例如 agentId 一致性）。
     * 3. 返回一个小型只读元对象，作为后续构建 AgentConfigEntity 的标准输入。
     *
     * 字段优先级规则：
     * - appName: appNameCandidate > configJson.table.appName
     * - agentName: agentNameCandidate > configJson.agent.agentName
     * - agentDesc: agentDescCandidate > configJson.agent.agentDesc
     *
     * 注意：
     * - choose(...) 会做 trim，并保证不会返回 null（最差返回空字符串）。
     */
    private ConfigMeta resolveConfigMeta(
            String expectedAgentId,
            String configJson,
            String appNameCandidate,
            String agentNameCandidate,
            String agentDescCandidate
    ) {
        // 先把 JSON 字符串解析为结构化配置对象，后续从中提取元信息字段。
        AiAgentConfigTableVO tableVO = agentRuntimeAssembler.parseConfigJson(configJson);

        // 先定义为 null，便于处理“agent 节点缺失”的场景。
        String jsonAgentId = null;
        String jsonAgentName = null;
        String jsonAgentDesc = null;

        // 安全提取 configJson.agent 下的字段。
        if (tableVO.getAgent() != null) {
            jsonAgentId = tableVO.getAgent().getAgentId();
            jsonAgentName = tableVO.getAgent().getAgentName();
            jsonAgentDesc = tableVO.getAgent().getAgentDesc();
        }

        // 关键校验：若 JSON 里显式带了 agentId，必须与外部期望 agentId 一致。
        // 这样可以防止“请求 agentId 与配置内容不一致”导致装配到错误 Agent。
        if (StringUtils.isNotBlank(jsonAgentId) && !expectedAgentId.equals(jsonAgentId.trim())) {
            throw new AppException(
                    ResponseCode.ILLEGAL_PARAMETER.getCode(),
                    "agentId mismatch between request and configJson"
            );
        }

        // appName 为必填业务字段：优先使用请求传入值，否则回退 configJson。
        String appName = choose(appNameCandidate, tableVO.getAppName());
        if (StringUtils.isBlank(appName)) {
            throw new AppException(ResponseCode.ILLEGAL_PARAMETER.getCode(), "appName is blank");
        }

        // agentName/agentDesc 允许为空：通过 defaultString 统一收敛为非 null 字符串。
        return new ConfigMeta(
                appName,
                StringUtils.defaultString(choose(agentNameCandidate, jsonAgentName)),
                StringUtils.defaultString(choose(agentDescCandidate, jsonAgentDesc))
        );
    }

    private void validateCreateRequest(AgentConfigEntity request) {
        if (request == null) {
            throw new AppException(ResponseCode.ILLEGAL_PARAMETER.getCode(), "request is null");
        }
        if (isBlank(request.getConfigJson())) {
            throw new AppException(ResponseCode.ILLEGAL_PARAMETER.getCode(), "configJson is blank");
        }
    }

    private String generateAvailableAgentId() {
        for (int index = 0; index < AGENT_ID_GENERATE_RETRY_TIMES; index++) {
            String candidate = AGENT_ID_PREFIX + System.currentTimeMillis() + ThreadLocalRandom.current().nextInt(100, 1000);
            if (!agentConfigRepository.exists(candidate)) {
                return candidate;
            }
        }
        throw new AppException(ResponseCode.UN_ERROR.getCode(), "failed to generate unique agentId");
    }

    private String normalizeCreateConfigJson(String rawConfigJson, String agentId) {
        AiAgentConfigTableVO tableVO = agentRuntimeAssembler.parseConfigJson(rawConfigJson);
        if (tableVO.getAgent() == null) {
            tableVO.setAgent(new AiAgentConfigTableVO.Agent());
        }
        tableVO.getAgent().setAgentId(agentId);
        try {
            return OBJECT_MAPPER.writeValueAsString(tableVO);
        } catch (Exception e) {
            throw new AppException(ResponseCode.UN_ERROR.getCode(), "configJson normalize failed: " + e.getMessage());
        }
    }

    private void validateUpdateRequest(AgentConfigEntity request) {
        if (request == null) {
            throw new AppException(ResponseCode.ILLEGAL_PARAMETER.getCode(), "request is null");
        }
        if (isBlank(request.getAgentId())) {
            throw new AppException(ResponseCode.ILLEGAL_PARAMETER.getCode(), "agentId is blank");
        }
    }

    /**
     * 二选一取值工具：
     * - candidate 有效（非 null / 非空 / 非全空格）时优先使用 candidate
     * - 否则回退 fallback
     * - 返回值统一做 trim，且保证不为 null（fallback 为空时返回 ""）
     */
    private String choose(String candidate, String fallback) {
        return StringUtils.isNotBlank(candidate) ? candidate.trim() : StringUtils.trimToEmpty(fallback);
    }

    private long defaultLong(Long value, long fallback) {
        return value == null ? fallback : value;
    }

    private String trimToEmpty(String value) {
        return value == null ? "" : value.trim();
    }

    private boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }

    /**
     * 配置元信息聚合对象。
     * 用于承载 resolveConfigMeta 的多返回值，避免返回 Map/数组造成语义不清。
     */
    private static class ConfigMeta {
        private final String appName;
        private final String agentName;
        private final String agentDesc;

        private ConfigMeta(String appName, String agentName, String agentDesc) {
            this.appName = appName;
            this.agentName = agentName;
            this.agentDesc = agentDesc;
        }
    }

    private static class ZipEntryPayload {
        private final String path;
        private final byte[] content;

        private ZipEntryPayload(String path, byte[] content) {
            this.path = path;
            this.content = content;
        }
    }

    private static class OssLocation {
        private final String bucket;
        private final String keyPrefix;

        private OssLocation(String bucket, String keyPrefix) {
            this.bucket = bucket;
            this.keyPrefix = keyPrefix;
        }

        private String getBucket() {
            return bucket;
        }

        private String getKeyPrefix() {
            return keyPrefix;
        }
    }

}


