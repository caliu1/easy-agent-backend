package cn.caliu.agent.domain.session.service;

import cn.caliu.agent.domain.session.model.entity.AgentSessionHistoryEntity;
import cn.caliu.agent.domain.session.model.entity.AgentSessionMessageEntity;

import java.util.List;

/**
 * 会话历史领域服务接口。
 *
 * 负责：
 * 1. 会话摘要与消息明细落库。
 * 2. 用户/助手/系统消息追加。
 * 3. token 统计累积与历史查询。
 */
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

