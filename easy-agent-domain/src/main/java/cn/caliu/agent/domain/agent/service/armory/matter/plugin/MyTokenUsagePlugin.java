package cn.caliu.agent.domain.agent.service.armory.matter.plugin;

import cn.caliu.agent.domain.session.model.entity.AgentSessionBindEntity;
import cn.caliu.agent.domain.session.repository.IAgentSessionBindRepository;
import cn.caliu.agent.domain.session.service.ISessionHistoryService;
import com.google.adk.agents.CallbackContext;
import com.google.adk.agents.InvocationContext;
import com.google.adk.models.LlmResponse;
import com.google.adk.plugins.BasePlugin;
import com.google.genai.types.GenerateContentResponseUsageMetadata;
import io.reactivex.rxjava3.core.Completable;
import io.reactivex.rxjava3.core.Maybe;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

@Slf4j
@Service("myTokenUsagePlugin")
public class MyTokenUsagePlugin extends BasePlugin {

    private static final String TOKEN_TOTAL_KEY = "__token_usage_total";
    private static final String TOKEN_EVENT_MAX_KEY = "__token_usage_event_max";

    @Resource
    private ISessionHistoryService sessionHistoryService;
    @Resource
    private IAgentSessionBindRepository agentSessionBindRepository;

    public MyTokenUsagePlugin() {
        super("myTokenUsagePlugin");
    }

    @Override
    public Maybe<LlmResponse> afterModelCallback(CallbackContext callbackContext, LlmResponse llmResponse) {
        return Maybe.fromAction(() -> {
            if (llmResponse != null && llmResponse.partial().orElse(false)) {
                return;
            }

            long tokenCount = extractTokenCount(llmResponse);
            if (tokenCount <= 0L) {
                return;
            }

            InvocationContext invocationContext = callbackContext.invocationContext();
            Map<String, Object> callbackContextData = invocationContext.callbackContextData();
            synchronized (callbackContextData) {
                @SuppressWarnings("unchecked")
                Map<String, Long> eventMaxMap = (Map<String, Long>) callbackContextData.computeIfAbsent(
                        TOKEN_EVENT_MAX_KEY,
                        key -> new HashMap<String, Long>()
                );

                String eventKey = buildEventKey(callbackContext);
                long previousMax = eventMaxMap.getOrDefault(eventKey, 0L);
                if (tokenCount <= previousMax) {
                    return;
                }

                eventMaxMap.put(eventKey, tokenCount);
                long delta = tokenCount - previousMax;
                long total = toLong(callbackContextData.get(TOKEN_TOTAL_KEY)) + delta;
                callbackContextData.put(TOKEN_TOTAL_KEY, total);
            }
        });
    }

    @Override
    public Completable afterRunCallback(InvocationContext invocationContext) {
        return Completable.fromAction(() -> {
            Map<String, Object> callbackContextData = invocationContext.callbackContextData();
            long totalTokens = extractInvocationTotal(callbackContextData);
            if (totalTokens <= 0L) {
                clearInvocationTokenState(callbackContextData);
                return;
            }

            String sessionId = invocationContext.session().id();
            String userId = invocationContext.userId();

            AgentSessionBindEntity bindEntity = agentSessionBindRepository.queryBySessionId(sessionId);
            String agentId = bindEntity == null ? "" : bindEntity.getAgentId();

            sessionHistoryService.appendSessionTokens(sessionId, agentId, userId, totalTokens);

            clearInvocationTokenState(callbackContextData);
            log.info("Token usage persisted by plugin, sessionId:{}, tokens:{}", sessionId, totalTokens);
        });
    }

    private long extractTokenCount(LlmResponse llmResponse) {
        if (llmResponse == null) {
            return 0L;
        }

        GenerateContentResponseUsageMetadata usageMetadata = llmResponse.usageMetadata().orElse(null);
        if (usageMetadata == null) {
            return 0L;
        }

        Long total = toLong(usageMetadata.totalTokenCount());
        if (total != null && total > 0L) {
            return total;
        }

        long prompt = defaultLong(usageMetadata.promptTokenCount());
        long candidates = defaultLong(usageMetadata.candidatesTokenCount());
        long thoughts = defaultLong(usageMetadata.thoughtsTokenCount());
        long toolPrompt = defaultLong(usageMetadata.toolUsePromptTokenCount());
        return Math.max(0L, prompt + candidates + thoughts + toolPrompt);
    }

    private String buildEventKey(CallbackContext callbackContext) {
        String invocationId = StringUtils.defaultString(callbackContext.invocationId());
        String eventId = StringUtils.defaultString(callbackContext.eventId());
        String agentName = StringUtils.defaultString(callbackContext.agentName());
        return invocationId + ":" + eventId + ":" + agentName;
    }

    private long extractInvocationTotal(Map<String, Object> callbackContextData) {
        if (callbackContextData == null) {
            return 0L;
        }
        synchronized (callbackContextData) {
            return toLong(callbackContextData.get(TOKEN_TOTAL_KEY));
        }
    }

    private void clearInvocationTokenState(Map<String, Object> callbackContextData) {
        if (callbackContextData == null) {
            return;
        }
        synchronized (callbackContextData) {
            callbackContextData.remove(TOKEN_TOTAL_KEY);
            callbackContextData.remove(TOKEN_EVENT_MAX_KEY);
        }
    }

    private long defaultLong(Optional<Integer> value) {
        Long longValue = toLong(value);
        return longValue == null ? 0L : longValue;
    }

    private Long toLong(Optional<Integer> value) {
        if (value == null || !value.isPresent()) {
            return null;
        }
        return value.get().longValue();
    }

    private long toLong(Object value) {
        if (value instanceof Number) {
            return ((Number) value).longValue();
        }
        return 0L;
    }
}
