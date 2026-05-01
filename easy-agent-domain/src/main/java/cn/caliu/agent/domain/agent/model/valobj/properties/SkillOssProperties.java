package cn.caliu.agent.domain.agent.model.valobj.properties;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * OSS Skill 读取配置。
 * 用于支持 tool-skills-list.type=oss 时，直接从 OSS 读取 SKILL.md。
 */
@Data
@Component
@ConfigurationProperties(prefix = "ai.agent.skill-oss", ignoreInvalidFields = true)
public class SkillOssProperties {

    /**
     * 是否启用 OSS Skill 直读能力。
     */
    private boolean enabled = false;

    /**
     * OSS endpoint，例如 oss-cn-hangzhou.aliyuncs.com
     * 也可带协议：https://oss-cn-hangzhou.aliyuncs.com
     */
    private String endpoint;

    /**
     * AccessKey ID。
     */
    private String accessKeyId;

    /**
     * AccessKey Secret。
     */
    private String accessKeySecret;

    /**
     * STS 临时 Token（可选）。
     */
    private String securityToken;

    /**
     * 默认 bucket（当 path 不包含 oss://bucket 前缀时使用）。
     */
    private String defaultBucket;

    /**
     * OSS 列举对象时每页大小。
     */
    private Integer listMaxKeys = 1000;

}

