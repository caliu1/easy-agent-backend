package cn.caliu.agent.domain.agent.service.chat;

import cn.caliu.agent.domain.agent.model.entity.ChatCommandEntity;
import cn.caliu.agent.domain.agent.model.valobj.AgentSessionBindVO;
import cn.caliu.agent.domain.agent.model.valobj.AiAgentConfigTableVO;
import cn.caliu.agent.domain.agent.model.valobj.AiAgentRegisterVO;
import cn.caliu.agent.domain.agent.model.valobj.properties.AiAgentAutoConfigProperties;
import cn.caliu.agent.domain.agent.repository.IAgentSessionBindRepository;
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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

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

    @Override
    public List<AiAgentConfigTableVO.Agent> queryAiAgentConfigList() {
        /* 运行时优先, yml 兜底 */
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

        // 兼容回退到 yml
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
    public String createSession(String agentId, String userId) {
        ResolvedAgentContext resolvedAgentContext = resolveActiveAgent(agentId);
        AiAgentRegisterVO aiAgentRegisterVO = resolvedAgentContext.registerVO;

        String appName = aiAgentRegisterVO.getAppName();
        InMemoryRunner runner = aiAgentRegisterVO.getRunner();
        Session session = runner.sessionService().createSession(appName, userId).blockingGet();

        /* 创建会话时绑定版本 */
        agentSessionBindRepository.bindSession(AgentSessionBindVO.builder()
                .sessionId(session.id())
                .agentId(aiAgentRegisterVO.getAgentId())
                .configVersion(resolvedAgentContext.configVersion)
                .userId(userId)
                .build());
        return session.id();
    }

    @Override
    public List<String> handleMessage(String agentId, String userId, String message) {
        String sessionId = createSession(agentId, userId);
        return handleMessage(agentId, userId, sessionId, message);
    }

    @Override
    public List<String> handleMessage(String agentId, String userId, String sessionId, String message) {
        ResolvedAgentContext resolvedAgentContext = resolveAgentForSession(agentId, userId, sessionId);

        InMemoryRunner runner = resolvedAgentContext.registerVO.getRunner();
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

        List<Part> parts = new ArrayList<>();

        List<ChatCommandEntity.Content.Text> texts = chatCommandEntity.getTexts();
        if (texts != null && !texts.isEmpty()) {
            for (ChatCommandEntity.Content.Text text : texts) {
                parts.add(Part.fromText(text.getMessage()));
            }
        }

        List<ChatCommandEntity.Content.File> files = chatCommandEntity.getFiles();
        if (files != null && !files.isEmpty()) {
            for (ChatCommandEntity.Content.File file : files) {
                parts.add(Part.fromUri(file.getFileUri(), file.getMimeType()));
            }
        }

        List<ChatCommandEntity.Content.InlineData> inlineDatas = chatCommandEntity.getInlineDatas();
        if (inlineDatas != null && !inlineDatas.isEmpty()) {
            for (ChatCommandEntity.Content.InlineData inlineData : inlineDatas) {
                parts.add(Part.fromBytes(inlineData.getBytes(), inlineData.getMimeType()));
            }
        }

        Content content = Content.builder().role("user").parts(parts).build();
        InMemoryRunner runner = resolvedAgentContext.registerVO.getRunner();
        Flowable<Event> events = runner.runAsync(chatCommandEntity.getUserId(), chatCommandEntity.getSessionId(), content);

        List<String> outputs = new ArrayList<>();
        events.blockingForEach(event -> outputs.add(event.stringifyContent()));
        return outputs;
    }

    private ResolvedAgentContext resolveAgentForSession(String agentId, String userId, String sessionId) {
        if (StringUtils.isBlank(sessionId)) {
            return resolveActiveAgent(agentId);
        }

        AgentSessionBindVO bindVO = agentSessionBindRepository.queryBySessionId(sessionId);
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

        /* 绑定失效时回退并重绑 */
        ResolvedAgentContext activeContext = resolveActiveAgent(agentId);
        agentSessionBindRepository.bindSession(AgentSessionBindVO.builder()
                .sessionId(sessionId)
                .agentId(activeContext.agentId)
                .configVersion(activeContext.configVersion)
                .userId(userId)
                .build());
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
