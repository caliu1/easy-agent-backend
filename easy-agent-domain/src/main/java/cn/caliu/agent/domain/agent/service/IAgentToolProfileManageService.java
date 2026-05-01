package cn.caliu.agent.domain.agent.service;

import cn.caliu.agent.domain.agent.model.entity.AgentMcpProfileEntity;
import cn.caliu.agent.domain.agent.model.entity.AgentSkillProfileEntity;

import java.util.List;

/**
 * MCP / Skill 配置档案管理领域服务接口。
 */
public interface IAgentToolProfileManageService {

    AgentMcpProfileEntity createMcpProfile(AgentMcpProfileEntity request);

    AgentMcpProfileEntity updateMcpProfile(AgentMcpProfileEntity request);

    boolean deleteMcpProfile(Long id, String userId);

    List<AgentMcpProfileEntity> queryMcpProfileList(String userId);

    boolean testMcpProfileConnection(AgentMcpProfileEntity request);

    AgentSkillProfileEntity createSkillProfile(AgentSkillProfileEntity request);

    AgentSkillProfileEntity updateSkillProfile(AgentSkillProfileEntity request);

    boolean deleteSkillProfile(Long id, String userId);

    List<AgentSkillProfileEntity> querySkillProfileList(String userId);

}
