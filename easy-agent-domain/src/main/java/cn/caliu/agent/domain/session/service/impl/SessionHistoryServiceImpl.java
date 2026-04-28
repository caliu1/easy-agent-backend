package cn.caliu.agent.domain.session.service.impl;

import cn.caliu.agent.domain.session.model.entity.AgentSessionHistoryEntity;
import cn.caliu.agent.domain.session.model.entity.AgentSessionMessageEntity;
import cn.caliu.agent.domain.session.repository.IAgentSessionHistoryRepository;
import cn.caliu.agent.domain.session.service.ISessionHistoryService;
import cn.caliu.agent.types.enums.ResponseCode;
import cn.caliu.agent.types.exception.AppException;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.util.List;

/**
 * 会话历史领域服务实现。
 *
 * 负责消息与会话摘要的写入、查询、删除，以及 token 统计累积。
 */
@Service
public class SessionHistoryServiceImpl implements ISessionHistoryService {

    @Resource
    private IAgentSessionHistoryRepository agentSessionHistoryRepository;

    @Override
    public void createSession(String sessionId, String agentId, String userId) {
        if (StringUtils.isBlank(sessionId) || StringUtils.isBlank(agentId) || StringUtils.isBlank(userId)) {
            return;
        }

        agentSessionHistoryRepository.createSession(
                AgentSessionHistoryEntity.createEmpty(sessionId, agentId, userId)
        );
    }

    @Override
    public void appendUserMessage(String sessionId, String agentId, String userId, String content) {
        appendMessage(sessionId, agentId, userId, AgentSessionMessageEntity.ROLE_USER, content);
    }

    @Override
    public void appendAssistantMessage(String sessionId, String agentId, String userId, String content) {
        appendMessage(sessionId, agentId, userId, AgentSessionMessageEntity.ROLE_ASSISTANT, content);
    }

    @Override
    public void appendSystemMessage(String sessionId, String agentId, String userId, String content) {
        appendMessage(sessionId, agentId, userId, AgentSessionMessageEntity.ROLE_SYSTEM, content);
    }

    @Override
    public void appendSessionTokens(String sessionId, String agentId, String userId, Long tokens) {
        if (StringUtils.isBlank(sessionId) || tokens == null || tokens <= 0L) {
            return;
        }

        agentSessionHistoryRepository.appendSessionTokens(
                sessionId.trim(),
                StringUtils.trimToEmpty(agentId),
                StringUtils.trimToEmpty(userId),
                tokens
        );
    }

    @Override
    public List<AgentSessionHistoryEntity> querySessionList(String userId, String agentId) {
        validateUserId(userId);
        return agentSessionHistoryRepository.querySessionList(userId.trim(), StringUtils.trimToEmpty(agentId));
    }

    @Override
    public List<AgentSessionMessageEntity> querySessionMessageList(String sessionId) {
        validateSessionKey(sessionId);
        return agentSessionHistoryRepository.querySessionMessageList(sessionId.trim());
    }

    @Override
    public boolean deleteSession(String sessionId, String userId) {
        validateSessionKey(sessionId);
        validateUserId(userId);
        return agentSessionHistoryRepository.deleteSession(sessionId.trim(), userId.trim());
    }

    private void appendMessage(String sessionId, String agentId, String userId, String role, String content) {
        if (StringUtils.isBlank(sessionId)) {
            return;
        }

        String normalizedContent = StringUtils.defaultString(content);
        if (StringUtils.isBlank(role) || StringUtils.isBlank(normalizedContent)) {
            return;
        }

        agentSessionHistoryRepository.appendMessage(
                AgentSessionMessageEntity.create(sessionId, agentId, userId, role, normalizedContent)
        );
    }

    private void validateSessionKey(String sessionId) {
        if (StringUtils.isBlank(sessionId)) {
            throw new AppException(ResponseCode.ILLEGAL_PARAMETER.getCode(), "sessionId is blank");
        }
    }

    private void validateUserId(String userId) {
        if (StringUtils.isBlank(userId)) {
            throw new AppException(ResponseCode.ILLEGAL_PARAMETER.getCode(), "userId is blank");
        }
    }

}
