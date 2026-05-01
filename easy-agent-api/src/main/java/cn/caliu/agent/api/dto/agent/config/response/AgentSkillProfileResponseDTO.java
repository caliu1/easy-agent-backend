package cn.caliu.agent.api.dto.agent.config.response;

import lombok.Data;

/**
 * Skill 配置响应。
 */
@Data
public class AgentSkillProfileResponseDTO {

    private Long id;
    private String userId;
    private String skillName;
    private String ossPath;
    private Long createTime;
    private Long updateTime;

}
