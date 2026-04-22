package cn.caliu.agent.domain.agent.repository;

import cn.caliu.agent.domain.agent.model.valobj.AgentSessionBindVO;

/**
 * 会话版本绑定仓储接口。
 */
public interface IAgentSessionBindRepository {

    void bindSession(AgentSessionBindVO bindVO);

    AgentSessionBindVO queryBySessionId(String sessionId);

    void deleteByAgentId(String agentId);

}

