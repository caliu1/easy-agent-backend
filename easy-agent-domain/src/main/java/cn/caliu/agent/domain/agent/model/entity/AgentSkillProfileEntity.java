package cn.caliu.agent.domain.agent.model.entity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 用户 Skill 配置档案实体。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AgentSkillProfileEntity {

    private Long id;
    private String userId;
    /**
     * Skill 名称（用于用户侧展示与去重）。
     */
    private String skillName;
    /**
     * Skill 在 OSS 下的完整路径（例如：easyagent/skills/drawio-skill）。
     */
    private String ossPath;
    private Long createTime;
    private Long updateTime;

}

