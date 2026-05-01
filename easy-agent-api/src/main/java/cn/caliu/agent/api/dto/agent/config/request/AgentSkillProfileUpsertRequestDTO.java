package cn.caliu.agent.api.dto.agent.config.request;

import lombok.Data;

/**
 * Skill 配置创建/更新请求。
 */
@Data
public class AgentSkillProfileUpsertRequestDTO {

    /**
     * 更新时必填；创建时可空。
     */
    private Long id;

    private String userId;
    private String skillName;
    private String description;
    private String ossPath;

}
