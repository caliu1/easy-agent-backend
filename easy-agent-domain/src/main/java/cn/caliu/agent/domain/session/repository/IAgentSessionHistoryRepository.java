package cn.caliu.agent.domain.session.repository;

import cn.caliu.agent.domain.session.model.entity.AgentSessionHistoryEntity;
import cn.caliu.agent.domain.session.model.entity.AgentSessionMessageEntity;

import java.util.List;

public interface IAgentSessionHistoryRepository {

    void createSession(AgentSessionHistoryEntity sessionEntity);

    void appendMessage(AgentSessionMessageEntity messageEntity);

    void appendSessionTokens(String sessionId, String agentId, String userId, Long tokens);

    List<AgentSessionHistoryEntity> querySessionList(String userId, String agentId);

    List<AgentSessionMessageEntity> querySessionMessageList(String sessionId);

}
