package cn.caliu.agent.domain.agent.repository;

import cn.caliu.agent.domain.agent.model.entity.AgentMcpProfileEntity;

import java.util.List;

/**
 * MCP 配置档案仓储接口。
 */
public interface IAgentMcpProfileRepository {

    List<AgentMcpProfileEntity> queryByUserId(String userId);

    AgentMcpProfileEntity queryById(Long id, String userId);

    boolean existsByMcpName(String userId, String mcpName, String mcpType, Long excludeId);

    void insert(AgentMcpProfileEntity entity);

    void update(AgentMcpProfileEntity entity);

    boolean softDelete(Long id, String userId);

}
