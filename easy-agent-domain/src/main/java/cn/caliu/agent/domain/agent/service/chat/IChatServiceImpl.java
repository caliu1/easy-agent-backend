package cn.caliu.agent.domain.agent.service.chat;

import cn.caliu.agent.domain.agent.model.entity.ChatCommandEntity;
import cn.caliu.agent.domain.session.model.entity.AgentSessionBindEntity;
import cn.caliu.agent.domain.agent.model.valobj.AiAgentConfigTableVO;
import cn.caliu.agent.domain.agent.model.valobj.AiAgentRegisterVO;
import cn.caliu.agent.domain.agent.model.valobj.properties.AiAgentAutoConfigProperties;
import cn.caliu.agent.domain.session.repository.IAgentSessionBindRepository;
import cn.caliu.agent.domain.agent.service.IChatService;
import cn.caliu.agent.domain.agent.service.armory.factory.DefaultArmoryFactory;
import cn.caliu.agent.domain.agent.service.runtime.AgentRuntimeRegistry;
import cn.caliu.agent.types.enums.ResponseCode;
import cn.caliu.agent.types.exception.AppException;
import com.google.adk.agents.RunConfig;
import com.google.adk.events.Event;
import com.google.adk.runner.InMemoryRunner;
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

        Content content = chatCommandEntity.toUserContent();
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

