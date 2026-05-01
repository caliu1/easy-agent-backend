package cn.caliu.agent.domain.agent.repository;

import cn.caliu.agent.domain.agent.model.entity.AgentSkillProfileEntity;

import java.util.List;

/**
 * Skill 配置档案仓储接口。
 */
public interface IAgentSkillProfileRepository {

    List<AgentSkillProfileEntity> queryByUserId(String userId);

    AgentSkillProfileEntity queryById(Long id, String userId);

    boolean existsBySkillName(String userId, String skillName, Long excludeId);

    void insert(AgentSkillProfileEntity entity);

    void update(AgentSkillProfileEntity entity);

    boolean softDelete(Long id, String userId);

}
