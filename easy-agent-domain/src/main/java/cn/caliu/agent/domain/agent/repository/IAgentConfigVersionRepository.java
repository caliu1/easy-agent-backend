package cn.caliu.agent.domain.agent.repository;

import cn.caliu.agent.domain.agent.model.valobj.AgentConfigVersionVO;

import java.util.List;

/**
 * Agent 配置历史版本仓储接口。
 */
public interface IAgentConfigVersionRepository {

    void insert(AgentConfigVersionVO versionVO);

    void saveOrUpdate(AgentConfigVersionVO versionVO);

    AgentConfigVersionVO queryByAgentIdAndVersion(String agentId, Long version);

    List<AgentConfigVersionVO> queryListByAgentId(String agentId);

}
