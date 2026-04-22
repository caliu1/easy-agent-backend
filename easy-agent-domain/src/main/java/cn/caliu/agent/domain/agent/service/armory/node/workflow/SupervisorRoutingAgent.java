package cn.caliu.agent.domain.agent.service.armory.node.workflow;

import cn.caliu.agent.types.common.AgentStreamMarker;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.adk.agents.BaseAgent;
import com.google.adk.agents.InvocationContext;
import com.google.adk.events.Event;
import com.google.genai.types.Content;
import com.google.genai.types.Part;
import io.reactivex.rxjava3.core.BackpressureStrategy;
import io.reactivex.rxjava3.core.Flowable;
import io.reactivex.rxjava3.core.FlowableEmitter;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.ArrayList;
import java.util.concurrent.CancellationException;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

@Slf4j
public class SupervisorRoutingAgent extends BaseAgent {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    private static final String ACTION_ROUTE = "route";
    private static final String ACTION_FINAL = "final";
    private static final int MAX_STAGE_REPLY_LENGTH = 800;
    // Stream main-agent replies in small chunks for smooth UI updates.
    private static final int MAIN_REPLY_CHUNK_SIZE = 8;

    private final BaseAgent routerAgent;
    private final Map<String, BaseAgent> workerAgentGroup;
    private final int maxIterations;

    public SupervisorRoutingAgent(
            String name,
            String description,
            List<? extends BaseAgent> subAgents,
            String routerAgentName,
            Integer maxIterations
    ) {
        super(name, description, subAgents, null, null);

        List<? extends BaseAgent> allSubAgents = subAgents();
        this.routerAgent = allSubAgents.stream()
                .filter(agent -> agent.name().equals(routerAgentName))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("router agent not found: " + routerAgentName));

        this.workerAgentGroup = allSubAgents.stream()
                .filter(agent -> !Objects.equals(agent.name(), routerAgentName))
                .collect(Collectors.toMap(BaseAgent::name, agent -> agent, (left, right) -> left, LinkedHashMap::new));

        if (workerAgentGroup.isEmpty()) {
            throw new IllegalArgumentException("supervisor workflow requires at least one worker agent");
        }

        this.maxIterations = (maxIterations == null || maxIterations <= 0) ? 3 : maxIterations;
    }

    @Override
    protected Flowable<Event> runAsyncImpl(InvocationContext invocationContext) {
        return Flowable.create(emitter -> runSupervisor(invocationContext, emitter), BackpressureStrategy.BUFFER);
    }

    @Override
    protected Flowable<Event> runLiveImpl(InvocationContext invocationContext) {
        return runAsyncImpl(invocationContext);
    }

    private void runSupervisor(InvocationContext invocationContext, FlowableEmitter<Event> emitter) {
        String latestWorkerOutput = "";

        try {
            for (int i = 0; i < maxIterations && !emitter.isCancelled(); i++) {
                RouteResult routeResult = route(invocationContext, emitter);
                if (emitter.isCancelled()) {
                    return;
                }
                RouteDecision routeDecision = routeResult.routeDecision;
                if (routeDecision == null) {
                    emitFinal(emitter, invocationContext, "Route decision parse failed.");
                    emitter.onComplete();
                    return;
                }

                if (!routeResult.thoughtStreamed && StringUtils.isNotBlank(routeDecision.getThought())) {
                    emitThinking(emitter, invocationContext, name(), routeDecision.getThought());
                }

                String action = StringUtils.trimToEmpty(routeDecision.getAction()).toLowerCase();
                if (ACTION_FINAL.equals(action)) {
                    // 主 Agent 最终回复：优先用路由器 reply；为空时回退到上一阶段子 Agent 输出。
                    String finalReply = buildFinalReply(routeDecision.getReply(), latestWorkerOutput);
                    if (StringUtils.isBlank(finalReply)) {
                        finalReply = "任务已完成。";
                    }
                    if (!routeResult.replyStreamed) {
                        emitFinal(emitter, invocationContext, finalReply);
                    }
                    emitter.onComplete();
                    return;
                }

                if (!ACTION_ROUTE.equals(action)) {
                    emitFinal(emitter, invocationContext, "Unsupported route action: " + action);
                    emitter.onComplete();
                    return;
                }

                String nextAgent = StringUtils.trimToEmpty(routeDecision.getNextAgent());
                BaseAgent workerAgent = workerAgentGroup.get(nextAgent);
                if (workerAgent == null) {
                    emitFinal(emitter, invocationContext, "Unknown nextAgent: " + nextAgent);
                    emitter.onComplete();
                    return;
                }
                // 阶段回复：优先用路由器 reply；为空时回退到上一阶段子 Agent 输出。
                String routeReply = buildStageReply(routeDecision.getReply(), latestWorkerOutput);
                if (!routeResult.replyStreamed && StringUtils.isNotBlank(routeReply)) {
                    emitReply(emitter, invocationContext, routeReply);
                }

                emitRoute(emitter, invocationContext, nextAgent);
                String workerOutput = runWorker(workerAgent, invocationContext, emitter);
                if (emitter.isCancelled()) {
                    return;
                }
                if (StringUtils.isNotBlank(workerOutput)) {
                    latestWorkerOutput = workerOutput;
                }
            }

            if (emitter.isCancelled()) {
                return;
            }
            String fallbackFinalReply = buildFinalReply("", latestWorkerOutput);
            if (StringUtils.isBlank(fallbackFinalReply)) {
                fallbackFinalReply = "已达到最大路由轮次，当前结果不足以继续细化。";
            }
            emitFinal(emitter, invocationContext, fallbackFinalReply);
            emitter.onComplete();
        } catch (Exception e) {
            if (isCancellationOrInterruption(e, emitter)) {
                if (hasInterruptedCause(e)) {
                    Thread.currentThread().interrupt();
                }
                log.info("supervisor workflow cancelled/interrupted: {}", e.toString());
                return;
            }
            log.error("supervisor workflow run failed", e);
            if (!emitter.isCancelled()) {
                emitFinal(emitter, invocationContext, "Supervisor run failed: " + e.getMessage());
                emitter.onComplete();
            }
        }
    }

    private RouteResult route(InvocationContext invocationContext, FlowableEmitter<Event> emitter) {
        StringBuilder allRouterOutput = new StringBuilder();
        AtomicInteger thoughtEmittedLength = new AtomicInteger(0);
        AtomicInteger replyEmittedLength = new AtomicInteger(0);
        AtomicInteger replyChunkCount = new AtomicInteger(0);

        routerAgent.runAsync(invocationContext).blockingForEach(event -> {
            if (emitter.isCancelled()) {
                throw new CancellationException("emitter cancelled during route");
            }
            String content = event.stringifyContent();
            if (StringUtils.isBlank(content)) {
                return;
            }

            // Do not inject extra separators between stream chunks, otherwise JSON can be broken.
            allRouterOutput.append(content);

            String partialThought = extractPartialJsonStringField(allRouterOutput.toString(), "thought");
            if (StringUtils.isNotBlank(partialThought) && partialThought.length() > thoughtEmittedLength.get()) {
                String thoughtChunk = partialThought.substring(thoughtEmittedLength.get());
                thoughtEmittedLength.addAndGet(thoughtChunk.length());
                emitThinking(emitter, invocationContext, name(), thoughtChunk);
            }

            String action = StringUtils.trimToEmpty(extractPartialJsonStringField(allRouterOutput.toString(), "action")).toLowerCase();
            if (!ACTION_FINAL.equals(action) && !ACTION_ROUTE.equals(action)) {
                return;
            }

            String partialReply = extractPartialJsonStringField(allRouterOutput.toString(), "reply");
            if (partialReply.length() <= replyEmittedLength.get()) {
                return;
            }

            String replyChunk = partialReply.substring(replyEmittedLength.get());
            replyEmittedLength.addAndGet(replyChunk.length());
            replyChunkCount.incrementAndGet();

            String marker = ACTION_FINAL.equals(action) ? AgentStreamMarker.FINAL : AgentStreamMarker.REPLY;
            emitMarkedEvent(emitter, invocationContext, name(), marker, replyChunk, true);
        });

        RouteDecision routeDecision = parseRouteDecision(allRouterOutput.toString());
        if (routeDecision != null) {
            String action = StringUtils.trimToEmpty(routeDecision.getAction()).toLowerCase();
            if ((ACTION_FINAL.equals(action) || ACTION_ROUTE.equals(action))
                    && replyChunkCount.get() > 0) {
                String finalReply = StringUtils.defaultString(routeDecision.getReply());
                String marker = ACTION_FINAL.equals(action) ? AgentStreamMarker.FINAL : AgentStreamMarker.REPLY;
                if (finalReply.length() > replyEmittedLength.get()) {
                    String tail = finalReply.substring(replyEmittedLength.get());
                    emitMarkedEvent(emitter, invocationContext, name(), marker, tail, false);
                } else {
                    // Completion marker for streamed main-agent reply/final.
                    emitMarkedEvent(emitter, invocationContext, name(), marker, "", false);
                }
            }
        }

        return new RouteResult(
                routeDecision,
                thoughtEmittedLength.get() > 0,
                replyChunkCount.get() > 0
        );
    }

    private String runWorker(BaseAgent workerAgent, InvocationContext invocationContext, FlowableEmitter<Event> emitter) {
        StringBuilder allWorkerOutput = new StringBuilder();

        workerAgent.runAsync(invocationContext).blockingForEach(event -> {
            if (emitter.isCancelled()) {
                throw new CancellationException("emitter cancelled during worker execution");
            }
            String content = event.stringifyContent();
            if (StringUtils.isBlank(content)) {
                return;
            }

            allWorkerOutput.append(content).append('\n');
            emitThinking(emitter, invocationContext, event.author(), content);
        });

        return allWorkerOutput.toString().trim();
    }

    private RouteDecision parseRouteDecision(String rawOutput) {
        if (StringUtils.isBlank(rawOutput)) {
            return null;
        }

        String jsonText = extractJson(rawOutput);
        if (StringUtils.isBlank(jsonText)) {
            return null;
        }

        try {
            RouteDecision routeDecision = OBJECT_MAPPER.readValue(jsonText, RouteDecision.class);
            if (routeDecision == null || StringUtils.isBlank(routeDecision.getAction())) {
                return null;
            }
            return routeDecision;
        } catch (Exception e) {
            log.warn("failed to parse route decision. rawOutput: {}", rawOutput, e);
            return null;
        }
    }

    private String extractJson(String rawOutput) {
        String normalized = rawOutput.trim();

        int firstBrace = normalized.indexOf('{');
        int lastBrace = normalized.lastIndexOf('}');
        if (firstBrace >= 0 && lastBrace > firstBrace) {
            return normalized.substring(firstBrace, lastBrace + 1);
        }

        return normalized;
    }

    /**
     * Best-effort extraction for a JSON string field from possibly incomplete JSON text.
     */
    private String extractPartialJsonStringField(String rawOutput, String fieldName) {
        if (StringUtils.isBlank(rawOutput) || StringUtils.isBlank(fieldName)) {
            return "";
        }

        String keyPattern = "\"" + fieldName + "\"";
        int keyIndex = rawOutput.indexOf(keyPattern);
        if (keyIndex < 0) {
            return "";
        }

        int colonIndex = rawOutput.indexOf(':', keyIndex + keyPattern.length());
        if (colonIndex < 0) {
            return "";
        }

        int quoteStart = rawOutput.indexOf('"', colonIndex + 1);
        if (quoteStart < 0) {
            return "";
        }

        StringBuilder value = new StringBuilder();
        boolean escaped = false;
        for (int i = quoteStart + 1; i < rawOutput.length(); i++) {
            char ch = rawOutput.charAt(i);
            if (escaped) {
                switch (ch) {
                    case 'n' -> value.append('\n');
                    case 'r' -> value.append('\r');
                    case 't' -> value.append('\t');
                    case '"' -> value.append('"');
                    case '\\' -> value.append('\\');
                    default -> value.append(ch);
                }
                escaped = false;
                continue;
            }

            if (ch == '\\') {
                escaped = true;
                continue;
            }

            if (ch == '"') {
                break;
            }

            value.append(ch);
        }

        return value.toString();
    }

    private void emitThinking(
            FlowableEmitter<Event> emitter,
            InvocationContext invocationContext,
            String author,
            String content
    ) {
        emitMarkedEvent(emitter, invocationContext, author, AgentStreamMarker.THINKING, content);
    }

    private void emitRoute(FlowableEmitter<Event> emitter, InvocationContext invocationContext, String nextAgent) {
        emitMarkedEvent(emitter, invocationContext, name(), AgentStreamMarker.ROUTE, nextAgent);
    }

    private void emitReply(FlowableEmitter<Event> emitter, InvocationContext invocationContext, String reply) {
        emitStreamedMarkedEvents(emitter, invocationContext, name(), AgentStreamMarker.REPLY, reply, MAIN_REPLY_CHUNK_SIZE);
    }

    private void emitFinal(FlowableEmitter<Event> emitter, InvocationContext invocationContext, String finalReply) {
        emitStreamedMarkedEvents(emitter, invocationContext, name(), AgentStreamMarker.FINAL, finalReply, MAIN_REPLY_CHUNK_SIZE);
    }

    private void emitMarkedEvent(
            FlowableEmitter<Event> emitter,
            InvocationContext invocationContext,
            String author,
            String marker,
            String content
    ) {
        emitMarkedEvent(emitter, invocationContext, author, marker, content, false);
    }

    private void emitMarkedEvent(
            FlowableEmitter<Event> emitter,
            InvocationContext invocationContext,
            String author,
            String marker,
            String content,
            boolean partial
    ) {
        if (emitter.isCancelled()) {
            return;
        }

        Event markedEvent = Event.builder()
                .id(Event.generateEventId())
                .invocationId(invocationContext.invocationId())
                .author(author)
                .branch(invocationContext.branch().orElse(null))
                .partial(partial)
                .content(Content.fromParts(Part.fromText(marker + StringUtils.defaultString(content))))
                .build();

        emitter.onNext(markedEvent);
    }

    private void emitStreamedMarkedEvents(
            FlowableEmitter<Event> emitter,
            InvocationContext invocationContext,
            String author,
            String marker,
            String content,
            int chunkSize
    ) {
        List<String> chunks = splitForStreaming(content, chunkSize);
        if (chunks.isEmpty()) {
            emitMarkedEvent(emitter, invocationContext, author, marker, "", false);
            return;
        }

        for (int i = 0; i < chunks.size() && !emitter.isCancelled(); i++) {
            boolean partial = i < chunks.size() - 1;
            emitMarkedEvent(emitter, invocationContext, author, marker, chunks.get(i), partial);
        }
    }

    private List<String> splitForStreaming(String content, int chunkSize) {
        String text = StringUtils.defaultString(content);
        if (StringUtils.isBlank(text)) {
            return List.of();
        }

        int safeChunkSize = Math.max(1, chunkSize);
        List<String> chunks = new ArrayList<>();
        int start = 0;
        while (start < text.length()) {
            int end = Math.min(start + safeChunkSize, text.length());

            // Avoid splitting surrogate pairs.
            if (end < text.length()
                    && end > start
                    && Character.isHighSurrogate(text.charAt(end - 1))
                    && Character.isLowSurrogate(text.charAt(end))) {
                end--;
            }

            if (end <= start) {
                end = Math.min(start + 1, text.length());
            }

            chunks.add(text.substring(start, end));
            start = end;
        }
        return chunks;
    }

    private String buildStageReply(String routeReply, String latestWorkerOutput) {
        String candidate = StringUtils.trimToEmpty(routeReply);
        if (StringUtils.isBlank(candidate)) {
            candidate = StringUtils.trimToEmpty(latestWorkerOutput);
        }
        if (StringUtils.isBlank(candidate)) {
            return "";
        }
        if (candidate.length() <= MAX_STAGE_REPLY_LENGTH) {
            return candidate;
        }
        return candidate.substring(0, MAX_STAGE_REPLY_LENGTH) + "...";
    }

    private String buildFinalReply(String routeReply, String latestWorkerOutput) {
        return StringUtils.trimToEmpty(StringUtils.defaultIfBlank(routeReply, latestWorkerOutput));
    }

    private boolean isCancellationOrInterruption(Throwable throwable, FlowableEmitter<Event> emitter) {
        if (emitter != null && emitter.isCancelled()) {
            return true;
        }

        Throwable cursor = throwable;
        while (cursor != null) {
            if (cursor instanceof InterruptedException || cursor instanceof CancellationException) {
                return true;
            }
            cursor = cursor.getCause();
        }

        return Thread.currentThread().isInterrupted();
    }

    private boolean hasInterruptedCause(Throwable throwable) {
        Throwable cursor = throwable;
        while (cursor != null) {
            if (cursor instanceof InterruptedException) {
                return true;
            }
            cursor = cursor.getCause();
        }
        return false;
    }

    @Data
    public static class RouteDecision {
        private String thought;
        private String action;
        private String nextAgent;
        private String reply;
    }

    private static class RouteResult {
        private final RouteDecision routeDecision;
        private final boolean thoughtStreamed;
        private final boolean replyStreamed;

        private RouteResult(RouteDecision routeDecision, boolean thoughtStreamed, boolean replyStreamed) {
            this.routeDecision = routeDecision;
            this.thoughtStreamed = thoughtStreamed;
            this.replyStreamed = replyStreamed;
        }
    }

}
