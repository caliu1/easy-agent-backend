package cn.caliu.agent.domain.agent.service.chat;

import cn.caliu.agent.domain.agent.model.entity.ChatCommandEntity;
import cn.caliu.agent.domain.session.model.entity.AgentSessionBindEntity;
import cn.caliu.agent.domain.agent.model.valobj.AiAgentConfigTableVO;
import cn.caliu.agent.domain.agent.model.valobj.AiAgentRegisterVO;
import cn.caliu.agent.domain.agent.model.valobj.properties.AiAgentAutoConfigProperties;
import cn.caliu.agent.domain.session.repository.IAgentSessionBindRepository;
import cn.caliu.agent.domain.session.model.entity.AgentSessionMessageEntity;
import cn.caliu.agent.domain.session.service.ISessionHistoryService;
import cn.caliu.agent.domain.agent.service.IChatService;
import cn.caliu.agent.domain.agent.service.armory.factory.DefaultArmoryFactory;
import cn.caliu.agent.domain.agent.service.runtime.AgentRuntimeRegistry;
import cn.caliu.agent.types.enums.ResponseCode;
import cn.caliu.agent.types.exception.AppException;
import com.google.adk.agents.RunConfig;
import com.google.adk.events.Event;
import com.google.adk.runner.InMemoryRunner;
import com.google.adk.sessions.Session;
import com.google.genai.types.Content;
import com.google.genai.types.Part;
import io.reactivex.rxjava3.core.Flowable;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.util.ArrayList;
import java.util.Collections;
import java.util.concurrent.ConcurrentHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

@Slf4j
@Service
public class IChatServiceImpl implements IChatService {

    @Resource
    private DefaultArmoryFactory defaultArmoryFactory;
    @Resource
    private AiAgentAutoConfigProperties aiAgentAutoConfigProperties;
    @Resource
    private AgentRuntimeRegistry agentRuntimeRegistry;
    @Resource
    private IAgentSessionBindRepository agentSessionBindRepository;
    @Resource
    private ISessionHistoryService sessionHistoryService;

    @Override
    public List<AiAgentConfigTableVO.Agent> queryAiAgentConfigList() {
        /* 杩愯鏃朵紭鍏? yml 鍏滃簳 */
        Map<String, AiAgentConfigTableVO.Agent> activeAgentMap = new LinkedHashMap<>();
        agentRuntimeRegistry.snapshot().values().forEach(slot -> {
            AiAgentRegisterVO registerVO = slot.getRegisterVO();
            if (registerVO == null || StringUtils.isBlank(registerVO.getAgentId())) {
                return;
            }

            AiAgentConfigTableVO.Agent agent = new AiAgentConfigTableVO.Agent();
            agent.setAgentId(registerVO.getAgentId());
            agent.setAgentName(registerVO.getAgentName());
            agent.setAgentDesc(registerVO.getAgentDesc());
            activeAgentMap.put(registerVO.getAgentId(), agent);
        });

        if (!activeAgentMap.isEmpty()) {
            return new ArrayList<>(activeAgentMap.values());
        }

        // 鍏煎鍥為€€鍒?yml
        Map<String, AiAgentConfigTableVO> tables = aiAgentAutoConfigProperties.getTables();
        List<AiAgentConfigTableVO.Agent> agentList = new ArrayList<>();
        if (tables != null) {
            for (AiAgentConfigTableVO tableVO : tables.values()) {
                if (tableVO.getAgent() != null) {
                    agentList.add(tableVO.getAgent());
                }
            }
        }
        return agentList;
    }

    @Override
    public List<String> handleMessage(String agentId, String userId, String sessionId, String message) {
        ResolvedAgentContext resolvedAgentContext = resolveAgentForSession(agentId, userId, sessionId);
        InMemoryRunner runner = resolvedAgentContext.registerVO.getRunner();
        ensureSessionContextRecovered(runner, resolvedAgentContext.registerVO.getAppName(), userId, sessionId, message);

        Content userMsg = Content.fromParts(Part.fromText(message));
        Flowable<Event> events = runner.runAsync(userId, sessionId, userMsg);

        List<String> outputs = new ArrayList<>();
        events.blockingForEach(event -> outputs.add(event.stringifyContent()));
        return outputs;
    }

    @Override
    public Flowable<Event> handleMessageStream(String agentId, String userId, String sessionId, String message) {
        ResolvedAgentContext resolvedAgentContext = resolveAgentForSession(agentId, userId, sessionId);
        InMemoryRunner runner = resolvedAgentContext.registerVO.getRunner();
        ensureSessionContextRecovered(runner, resolvedAgentContext.registerVO.getAppName(), userId, sessionId, message);

        RunConfig runConfig = RunConfig.builder()
                .setStreamingMode(RunConfig.StreamingMode.SSE)
                .build();
        Content userMsg = Content.fromParts(Part.fromText(message));
        return runner.runAsync(userId, sessionId, userMsg, runConfig);
    }

    @Override
    public List<String> handleMessage(ChatCommandEntity chatCommandEntity) {
        ResolvedAgentContext resolvedAgentContext = resolveAgentForSession(
                chatCommandEntity.getAgentId(),
                chatCommandEntity.getUserId(),
                chatCommandEntity.getSessionId()
        );

        Content content = chatCommandEntity.toUserContent();
        InMemoryRunner runner = resolvedAgentContext.registerVO.getRunner();
        ensureSessionContextRecovered(
                runner,
                resolvedAgentContext.registerVO.getAppName(),
                chatCommandEntity.getUserId(),
                chatCommandEntity.getSessionId(),
                extractContentText(content)
        );
        Flowable<Event> events = runner.runAsync(chatCommandEntity.getUserId(), chatCommandEntity.getSessionId(), content);

        List<String> outputs = new ArrayList<>();
        events.blockingForEach(event -> outputs.add(event.stringifyContent()));
        return outputs;
    }

    private void ensureSessionContextRecovered(
            InMemoryRunner runner,
            String appName,
            String userId,
            String sessionId,
            String latestUserInput
    ) {
        if (runner == null || StringUtils.isAnyBlank(appName, userId, sessionId)) {
            return;
        }

        Session existingSession = findSession(runner, appName, userId, sessionId);
        if (existingSession != null) {
            return;
        }

        Session session = createSessionIfAbsent(runner, appName, userId, sessionId);
        replaySessionHistory(runner, session, sessionId, latestUserInput);
        log.info("Recovered session context from persistence, sessionId:{}, replayed", sessionId);
    }

    private Session findSession(InMemoryRunner runner, String appName, String userId, String sessionId) {
        try {
            return runner.sessionService()
                    .getSession(appName, userId, sessionId, Optional.empty())
                    .blockingGet();
        } catch (Exception e) {
            log.warn("Find session failed, sessionId:{}", sessionId, e);
            return null;
        }
    }

    private Session createSessionIfAbsent(InMemoryRunner runner, String appName, String userId, String sessionId) {
        Session session = findSession(runner, appName, userId, sessionId);
        if (session != null) {
            return session;
        }

        try {
            return runner.sessionService()
                    .createSession(appName, userId, new ConcurrentHashMap<>(), sessionId)
                    .blockingGet();
        } catch (Exception e) {
            Session concurrentSession = findSession(runner, appName, userId, sessionId);
            if (concurrentSession != null) {
                return concurrentSession;
            }
            throw e;
        }
    }

    private void replaySessionHistory(
            InMemoryRunner runner,
            Session session,
            String sessionId,
            String latestUserInput
    ) {
        List<AgentSessionMessageEntity> historyMessages = sessionHistoryService.querySessionMessageList(sessionId);
        if (historyMessages == null || historyMessages.isEmpty()) {
            return;
        }

        int replaySize = historyMessages.size();
        if (shouldSkipLatestUserMessage(historyMessages, latestUserInput)) {
            replaySize = replaySize - 1;
        }

        for (int i = 0; i < replaySize; i++) {
            Event replayEvent = toReplayEvent(historyMessages.get(i));
            if (replayEvent == null) {
                continue;
            }

            try {
                runner.sessionService().appendEvent(session, replayEvent).blockingGet();
            } catch (Exception e) {
                log.warn("Append replay event failed, sessionId:{}, eventIndex:{}", sessionId, i, e);
            }
        }
    }

    private boolean shouldSkipLatestUserMessage(List<AgentSessionMessageEntity> historyMessages, String latestUserInput) {
        if (historyMessages == null || historyMessages.isEmpty() || StringUtils.isBlank(latestUserInput)) {
            return false;
        }

        AgentSessionMessageEntity tail = historyMessages.get(historyMessages.size() - 1);
        if (tail == null || !AgentSessionMessageEntity.ROLE_USER.equalsIgnoreCase(tail.getRole())) {
            return false;
        }

        return StringUtils.equals(
                StringUtils.trimToEmpty(tail.getContent()),
                StringUtils.trimToEmpty(latestUserInput)
        );
    }

    private Event toReplayEvent(AgentSessionMessageEntity messageEntity) {
        if (messageEntity == null || StringUtils.isBlank(messageEntity.getRole()) || StringUtils.isBlank(messageEntity.getContent())) {
            return null;
        }

        String role = StringUtils.lowerCase(messageEntity.getRole().trim(), Locale.ROOT);
        if (AgentSessionMessageEntity.ROLE_SYSTEM.equals(role)) {
            return null;
        }
        if (!AgentSessionMessageEntity.ROLE_USER.equals(role)
                && !AgentSessionMessageEntity.ROLE_ASSISTANT.equals(role)
                && !AgentSessionMessageEntity.ROLE_TOOL.equals(role)) {
            return null;
        }

        Content content = Content.builder()
                .role(role)
                .parts(Collections.singletonList(Part.fromText(messageEntity.getContent())))
                .build();

        long timestamp = messageEntity.getCreateTime() == null ? System.currentTimeMillis() : messageEntity.getCreateTime();
        return Event.builder()
                .id(Event.generateEventId())
                .author(role)
                .content(content)
                .partial(false)
                .turnComplete(true)
                .timestamp(timestamp)
                .build();
    }

    private String extractContentText(Content content) {
        if (content == null) {
            return "";
        }

        try {
            return StringUtils.defaultString(content.text());
        } catch (Exception e) {
            log.debug("Extract content text failed", e);
            return "";
        }
    }

    private ResolvedAgentContext resolveAgentForSession(String agentId, String userId, String sessionId) {
        if (StringUtils.isBlank(sessionId)) {
            return resolveActiveAgent(agentId);
        }

        AgentSessionBindEntity bindVO = agentSessionBindRepository.queryBySessionId(sessionId);
        if (bindVO != null && StringUtils.isNotBlank(bindVO.getAgentId())) {
            AiAgentRegisterVO boundRegisterVO = defaultArmoryFactory.getAiAgentRegisterVO(
                    bindVO.getAgentId(),
                    bindVO.getConfigVersion()
            );
            if (boundRegisterVO != null) {
                return new ResolvedAgentContext(
                        boundRegisterVO,
                        bindVO.getAgentId(),
                        defaultVersion(bindVO.getConfigVersion())
                );
            }
        }

        /* 缁戝畾澶辨晥鏃跺洖閫€骞堕噸缁?*/
        ResolvedAgentContext activeContext = resolveActiveAgent(agentId);
        agentSessionBindRepository.bindSession(
                AgentSessionBindEntity.create(
                        sessionId,
                        activeContext.agentId,
                        activeContext.configVersion,
                        userId
                )
        );
        return activeContext;
    }

    private ResolvedAgentContext resolveActiveAgent(String agentId) {
        if (StringUtils.isBlank(agentId)) {
            throw new AppException(ResponseCode.ILLEGAL_PARAMETER.getCode(), "agentId is blank");
        }

        String key = agentId.trim();
        AgentRuntimeRegistry.ActiveAgentSlot activeSlot = agentRuntimeRegistry.getActiveSlot(key).orElse(null);
        if (activeSlot != null && activeSlot.getRegisterVO() != null) {
            return new ResolvedAgentContext(
                    activeSlot.getRegisterVO(),
                    key,
                    defaultVersion(activeSlot.getConfigVersion())
            );
        }

        AiAgentRegisterVO fallbackVO = defaultArmoryFactory.getAiAgentRegisterBean(key);
        if (fallbackVO != null) {
            return new ResolvedAgentContext(fallbackVO, key, 0L);
        }

        throw new AppException(ResponseCode.E0001.getCode(), "agent not found: " + key);
    }

    private Long defaultVersion(Long version) {
        return version == null ? 0L : version;
    }

    private static class ResolvedAgentContext {
        private final AiAgentRegisterVO registerVO;
        private final String agentId;
        private final Long configVersion;

        private ResolvedAgentContext(AiAgentRegisterVO registerVO, String agentId, Long configVersion) {
            this.registerVO = registerVO;
            this.agentId = agentId;
            this.configVersion = configVersion;
        }
    }

}

