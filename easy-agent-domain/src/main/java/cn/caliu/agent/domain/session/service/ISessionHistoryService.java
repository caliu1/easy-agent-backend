package cn.caliu.agent.domain.session.service;

import cn.caliu.agent.domain.session.model.entity.AgentSessionHistoryEntity;
import cn.caliu.agent.domain.session.model.entity.AgentSessionMessageEntity;

import java.util.List;

public interface ISessionHistoryService {

    void createSession(String sessionId, String agentId, String userId);

    void appendUserMessage(String sessionId, String agentId, String userId, String content);

    void appendAssistantMessage(String sessionId, String agentId, String userId, String content);

    void appendSystemMessage(String sessionId, String agentId, String userId, String content);

    void appendSessionTokens(String sessionId, String agentId, String userId, Long tokens);

    List<AgentSessionHistoryEntity> querySessionList(String userId, String agentId);

    List<AgentSessionMessageEntity> querySessionMessageList(String sessionId);

    boolean deleteSession(String sessionId, String userId);

}
