package cn.caliu.agent.infrastructure.persistent.repository;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import cn.caliu.agent.domain.session.model.entity.AgentSessionHistoryEntity;
import cn.caliu.agent.domain.session.model.entity.AgentSessionMessageEntity;
import cn.caliu.agent.domain.session.repository.IAgentSessionHistoryRepository;
import cn.caliu.agent.infrastructure.persistent.dao.IAgentSessionHistoryDao;
import cn.caliu.agent.infrastructure.persistent.dao.IAgentSessionMessageDao;
import cn.caliu.agent.infrastructure.persistent.po.AgentSessionHistoryPO;
import cn.caliu.agent.infrastructure.persistent.po.AgentSessionMessagePO;
import org.apache.commons.lang3.StringUtils;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Repository;

import javax.annotation.Resource;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

@Repository
public class AgentSessionHistoryRepository implements IAgentSessionHistoryRepository {

    private static final int SESSION_TITLE_MAX_LENGTH = 80;
    private static final int LATEST_MESSAGE_MAX_LENGTH = 300;

    @Resource
    private IAgentSessionHistoryDao agentSessionHistoryDao;
    @Resource
    private IAgentSessionMessageDao agentSessionMessageDao;

    @Override
    public void createSession(AgentSessionHistoryEntity sessionEntity) {
        if (sessionEntity == null || StringUtils.isBlank(sessionEntity.getSessionId())) {
            return;
        }

        LocalDateTime now = LocalDateTime.now();
        LambdaUpdateWrapper<AgentSessionHistoryPO> updateWrapper = new LambdaUpdateWrapper<AgentSessionHistoryPO>()
                .eq(AgentSessionHistoryPO::getSessionId, sessionEntity.getSessionId())
                .set(StringUtils.isNotBlank(sessionEntity.getAgentId()), AgentSessionHistoryPO::getAgentId, sessionEntity.getAgentId())
                .set(StringUtils.isNotBlank(sessionEntity.getUserId()), AgentSessionHistoryPO::getUserId, sessionEntity.getUserId())
                .set(AgentSessionHistoryPO::getUpdateTime, now);

        int affected = agentSessionHistoryDao.update(null, updateWrapper);
        if (affected > 0) {
            return;
        }

        AgentSessionHistoryPO insertPO = toSessionPO(sessionEntity);
        insertPO.setSessionTitle(trimToLength(insertPO.getSessionTitle(), SESSION_TITLE_MAX_LENGTH));
        insertPO.setLatestMessage(trimToLength(insertPO.getLatestMessage(), LATEST_MESSAGE_MAX_LENGTH));
        insertPO.setMessageCount(defaultLong(insertPO.getMessageCount()));
        insertPO.setTotalTokens(defaultLong(insertPO.getTotalTokens()));
        insertPO.setCreateTime(now);
        insertPO.setUpdateTime(now);
        try {
            agentSessionHistoryDao.insert(insertPO);
        } catch (DuplicateKeyException e) {
            agentSessionHistoryDao.update(null, updateWrapper);
        }
    }

    @Override
    public void appendMessage(AgentSessionMessageEntity messageEntity) {
        if (messageEntity == null
                || StringUtils.isBlank(messageEntity.getSessionId())
                || StringUtils.isBlank(messageEntity.getRole())
                || StringUtils.isBlank(messageEntity.getContent())) {
            return;
        }

        LocalDateTime now = LocalDateTime.now();

        AgentSessionMessagePO messagePO = toMessagePO(messageEntity);
        messagePO.setCreateTime(now);
        agentSessionMessageDao.insert(messagePO);

        upsertSessionOnMessage(messageEntity, now);
    }

    @Override
    public void appendSessionTokens(String sessionId, String agentId, String userId, Long tokens) {
        if (StringUtils.isBlank(sessionId) || tokens == null || tokens <= 0L) {
            return;
        }

        LocalDateTime now = LocalDateTime.now();
        long increment = defaultLong(tokens);

        LambdaUpdateWrapper<AgentSessionHistoryPO> updateWrapper = new LambdaUpdateWrapper<AgentSessionHistoryPO>()
                .eq(AgentSessionHistoryPO::getSessionId, sessionId)
                .set(StringUtils.isNotBlank(agentId), AgentSessionHistoryPO::getAgentId, agentId)
                .set(StringUtils.isNotBlank(userId), AgentSessionHistoryPO::getUserId, userId)
                .setSql("total_tokens = IFNULL(total_tokens, 0) + " + increment)
                .set(AgentSessionHistoryPO::getUpdateTime, now);
        int affected = agentSessionHistoryDao.update(null, updateWrapper);
        if (affected > 0) {
            return;
        }

        AgentSessionHistoryPO insertPO = new AgentSessionHistoryPO();
        insertPO.setSessionId(sessionId);
        insertPO.setAgentId(StringUtils.defaultString(agentId));
        insertPO.setUserId(StringUtils.defaultString(userId));
        insertPO.setSessionTitle("");
        insertPO.setLatestMessage("");
        insertPO.setMessageCount(0L);
        insertPO.setTotalTokens(increment);
        insertPO.setCreateTime(now);
        insertPO.setUpdateTime(now);
        try {
            agentSessionHistoryDao.insert(insertPO);
        } catch (DuplicateKeyException e) {
            agentSessionHistoryDao.update(null, updateWrapper);
        }
    }

    @Override
    public List<AgentSessionHistoryEntity> querySessionList(String userId, String agentId) {
        if (StringUtils.isBlank(userId)) {
            return Collections.emptyList();
        }

        LambdaQueryWrapper<AgentSessionHistoryPO> queryWrapper = new LambdaQueryWrapper<AgentSessionHistoryPO>()
                .eq(AgentSessionHistoryPO::getUserId, userId)
                .eq(StringUtils.isNotBlank(agentId), AgentSessionHistoryPO::getAgentId, agentId)
                .orderByDesc(AgentSessionHistoryPO::getUpdateTime);

        List<AgentSessionHistoryPO> poList = agentSessionHistoryDao.selectList(queryWrapper);
        if (poList == null || poList.isEmpty()) {
            return Collections.emptyList();
        }
        return poList.stream().map(this::toSessionEntity).collect(Collectors.toList());
    }

    @Override
    public List<AgentSessionMessageEntity> querySessionMessageList(String sessionId) {
        if (StringUtils.isBlank(sessionId)) {
            return Collections.emptyList();
        }

        LambdaQueryWrapper<AgentSessionMessagePO> queryWrapper = new LambdaQueryWrapper<AgentSessionMessagePO>()
                .eq(AgentSessionMessagePO::getSessionId, sessionId)
                .orderByAsc(AgentSessionMessagePO::getId);

        List<AgentSessionMessagePO> poList = agentSessionMessageDao.selectList(queryWrapper);
        if (poList == null || poList.isEmpty()) {
            return Collections.emptyList();
        }
        return poList.stream().map(this::toMessageEntity).collect(Collectors.toList());
    }

    @Override
    public boolean deleteSession(String sessionId, String userId) {
        if (StringUtils.isAnyBlank(sessionId, userId)) {
            return false;
        }

        AgentSessionHistoryPO ownedSession = agentSessionHistoryDao.selectOne(
                new LambdaQueryWrapper<AgentSessionHistoryPO>()
                        .eq(AgentSessionHistoryPO::getSessionId, sessionId.trim())
                        .eq(AgentSessionHistoryPO::getUserId, userId.trim())
                        .last("LIMIT 1")
        );
        if (ownedSession == null) {
            return false;
        }

        LambdaUpdateWrapper<AgentSessionMessagePO> messageDeleteWrapper = new LambdaUpdateWrapper<AgentSessionMessagePO>()
                .eq(AgentSessionMessagePO::getSessionId, sessionId.trim());
        agentSessionMessageDao.delete(messageDeleteWrapper);

        LambdaUpdateWrapper<AgentSessionHistoryPO> sessionDeleteWrapper = new LambdaUpdateWrapper<AgentSessionHistoryPO>()
                .eq(AgentSessionHistoryPO::getSessionId, sessionId.trim())
                .eq(AgentSessionHistoryPO::getUserId, userId.trim());
        int affected = agentSessionHistoryDao.delete(sessionDeleteWrapper);
        return affected > 0;
    }

    private void upsertSessionOnMessage(AgentSessionMessageEntity messageEntity, LocalDateTime now) {
        AgentSessionHistoryPO existing = querySessionRecord(messageEntity.getSessionId());

        String latestMessage = trimToLength(messageEntity.getContent(), LATEST_MESSAGE_MAX_LENGTH);
        boolean isUserMessage = AgentSessionMessageEntity.ROLE_USER.equalsIgnoreCase(messageEntity.getRole());
        if (existing == null) {
            AgentSessionHistoryPO insertPO = new AgentSessionHistoryPO();
            insertPO.setSessionId(messageEntity.getSessionId());
            insertPO.setAgentId(messageEntity.getAgentId());
            insertPO.setUserId(messageEntity.getUserId());
            insertPO.setSessionTitle(isUserMessage
                    ? trimToLength(messageEntity.getContent(), SESSION_TITLE_MAX_LENGTH)
                    : "");
            insertPO.setLatestMessage(latestMessage);
            insertPO.setMessageCount(1L);
            insertPO.setTotalTokens(0L);
            insertPO.setCreateTime(now);
            insertPO.setUpdateTime(now);
            try {
                agentSessionHistoryDao.insert(insertPO);
                return;
            } catch (DuplicateKeyException e) {
                existing = querySessionRecord(messageEntity.getSessionId());
            }
        }

        if (existing == null) {
            return;
        }

        String nextTitle = existing.getSessionTitle();
        if (StringUtils.isBlank(nextTitle) && isUserMessage) {
            nextTitle = trimToLength(messageEntity.getContent(), SESSION_TITLE_MAX_LENGTH);
        }

        LambdaUpdateWrapper<AgentSessionHistoryPO> updateWrapper = new LambdaUpdateWrapper<AgentSessionHistoryPO>()
                .eq(AgentSessionHistoryPO::getSessionId, messageEntity.getSessionId())
                .set(StringUtils.isNotBlank(messageEntity.getAgentId()), AgentSessionHistoryPO::getAgentId, messageEntity.getAgentId())
                .set(StringUtils.isNotBlank(messageEntity.getUserId()), AgentSessionHistoryPO::getUserId, messageEntity.getUserId())
                .set(AgentSessionHistoryPO::getSessionTitle, StringUtils.defaultString(nextTitle))
                .set(AgentSessionHistoryPO::getLatestMessage, latestMessage)
                .set(AgentSessionHistoryPO::getMessageCount, defaultLong(existing.getMessageCount()) + 1)
                .set(AgentSessionHistoryPO::getUpdateTime, now);
        agentSessionHistoryDao.update(null, updateWrapper);
    }

    private AgentSessionHistoryPO toSessionPO(AgentSessionHistoryEntity source) {
        if (source == null) {
            return null;
        }
        AgentSessionHistoryPO target = new AgentSessionHistoryPO();
        target.setSessionId(source.getSessionId());
        target.setAgentId(source.getAgentId());
        target.setUserId(source.getUserId());
        target.setSessionTitle(source.getSessionTitle());
        target.setLatestMessage(source.getLatestMessage());
        target.setMessageCount(source.getMessageCount());
        target.setTotalTokens(source.getTotalTokens());
        return target;
    }

    private AgentSessionMessagePO toMessagePO(AgentSessionMessageEntity source) {
        if (source == null) {
            return null;
        }
        AgentSessionMessagePO target = new AgentSessionMessagePO();
        target.setSessionId(source.getSessionId());
        target.setAgentId(source.getAgentId());
        target.setUserId(source.getUserId());
        target.setRole(source.getRole());
        target.setContent(source.getContent());
        return target;
    }

    private AgentSessionHistoryEntity toSessionEntity(AgentSessionHistoryPO source) {
        if (source == null) {
            return null;
        }
        return AgentSessionHistoryEntity.builder()
                .sessionId(source.getSessionId())
                .agentId(source.getAgentId())
                .userId(source.getUserId())
                .sessionTitle(source.getSessionTitle())
                .latestMessage(source.getLatestMessage())
                .messageCount(source.getMessageCount())
                .totalTokens(source.getTotalTokens())
                .createTime(toEpochMilli(source.getCreateTime()))
                .updateTime(toEpochMilli(source.getUpdateTime()))
                .build();
    }

    private AgentSessionHistoryPO querySessionRecord(String sessionId) {
        return agentSessionHistoryDao.selectOne(
                new LambdaQueryWrapper<AgentSessionHistoryPO>()
                        .eq(AgentSessionHistoryPO::getSessionId, sessionId)
                        .last("LIMIT 1")
        );
    }

    private AgentSessionMessageEntity toMessageEntity(AgentSessionMessagePO source) {
        if (source == null) {
            return null;
        }
        return AgentSessionMessageEntity.builder()
                .id(source.getId())
                .sessionId(source.getSessionId())
                .agentId(source.getAgentId())
                .userId(source.getUserId())
                .role(source.getRole())
                .content(source.getContent())
                .createTime(toEpochMilli(source.getCreateTime()))
                .build();
    }

    private Long toEpochMilli(LocalDateTime time) {
        if (time == null) {
            return null;
        }
        return time.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli();
    }

    private long defaultLong(Long value) {
        return value == null ? 0L : value;
    }

    private String trimToLength(String content, int maxLength) {
        String normalized = StringUtils.defaultString(StringUtils.normalizeSpace(content));
        if (normalized.length() <= maxLength) {
            return normalized;
        }
        return normalized.substring(0, maxLength);
    }

}
