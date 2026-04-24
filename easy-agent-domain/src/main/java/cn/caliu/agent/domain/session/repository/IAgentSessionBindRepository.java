package cn.caliu.agent.domain.session.repository;

import cn.caliu.agent.domain.session.model.entity.AgentSessionBindEntity;

/**
 * 会话版本绑定仓储接口。
 */
public interface IAgentSessionBindRepository {

    void bindSession(AgentSessionBindEntity bindVO);

    AgentSessionBindEntity queryBySessionId(String sessionId);

    void deleteByAgentId(String agentId);

}
