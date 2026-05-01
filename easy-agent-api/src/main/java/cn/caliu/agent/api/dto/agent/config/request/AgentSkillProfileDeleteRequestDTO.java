package cn.caliu.agent.api.dto.agent.config.request;

import lombok.Data;

/**
 * Skill 配置档案删除请求。
 */
@Data
public class AgentSkillProfileDeleteRequestDTO {

    private Long id;
    private String userId;

}

