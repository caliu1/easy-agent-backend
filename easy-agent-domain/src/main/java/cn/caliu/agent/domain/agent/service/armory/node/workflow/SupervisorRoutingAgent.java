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

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CancellationException;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

/**
 * Supervisor 动态路由执行器。
 *
 * 核心职责：
 * 1) 调用 routerAgent 产出结构化路由决策（thought/action/nextAgent/reply）。
 * 2) 当 action=route 时分派到指定 worker，继续下一轮。
 * 3) 当 action=final 时输出最终回复并结束。
 * 4) 把内部过程转换为标准流式事件（thinking/route/reply/final）供前端渲染。
 *
 * 容错策略：
 * - 通过 maxIterations 限制轮次，避免无限路由。
 * - 决策解析失败、动作非法、nextAgent 不存在时，转为可读 final 信息。
 * - reply 为空时，回退到最近 worker 输出，提高收敛能力。
 */
@Slf4j
public class SupervisorRoutingAgent extends BaseAgent {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    private static final String ACTION_ROUTE = "route";
    private static final String ACTION_FINAL = "final";
    private static final int MAX_STAGE_REPLY_LENGTH = 800;
    // 主 Agent 文本按小块流式输出，提升前端增量展示体验。
    private static final int MAIN_REPLY_CHUNK_SIZE = 256;

    /**
     * 负责“决策”的路由 Agent。
     */
    private final BaseAgent routerAgent;

    /**
     * 可被路由执行的 worker 集合（不包含 router 本身）。
     * key=agentName, value=agentInstance
     */
    private final Map<String, BaseAgent> workerAgentGroup;

    /**
     * 最大路由轮次，超过后触发兜底 final。
     */
    private final int maxIterations;

    /**
     * 构造 Supervisor 路由 Agent。
     *
     * @param name            工作流 Agent 名称
     * @param description     工作流描述
     * @param subAgents       子 Agent 列表（router + workers）
     * @param routerAgentName 路由器 Agent 名称，必须在 subAgents 中存在
     * @param maxIterations   最大路由轮次，<=0 时默认 3
     */
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
                .orElseThrow(() -> new IllegalArgumentException("未找到路由Agent：" + routerAgentName));

        this.workerAgentGroup = allSubAgents.stream()
                .filter(agent -> !Objects.equals(agent.name(), routerAgentName))
                .collect(Collectors.toMap(BaseAgent::name, agent -> agent, (left, right) -> left, LinkedHashMap::new));

        if (workerAgentGroup.isEmpty()) {
            throw new IllegalArgumentException("Supervisor 工作流至少需要一个 Worker Agent");
        }

        this.maxIterations = (maxIterations == null || maxIterations <= 0) ? 3 : maxIterations;
    }

    @Override
    protected Flowable<Event> runAsyncImpl(InvocationContext invocationContext) {
        // 使用 BUFFER 背压策略，尽可能保留中间事件，避免丢帧。
        return Flowable.create(emitter -> runSupervisor(invocationContext, emitter), BackpressureStrategy.BUFFER);
    }

    @Override
    protected Flowable<Event> runLiveImpl(InvocationContext invocationContext) {
        // 当前 live/async 共用同一套执行路径。
        return runAsyncImpl(invocationContext);
    }

    /**
     * Supervisor 主循环。
     *
     * 每轮流程：
     * 1) route() 调用 routerAgent 得到决策。
     * 2) action=final: 输出最终回复并结束。
     * 3) action=route: 发 route 事件 -> 执行 worker -> 进入下一轮。
     */
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
                    emitFinal(emitter, invocationContext, "路由决策解析失败。");
                    emitter.onComplete();
                    return;
                }

                // route() 内未流式输出 thought 时，这里补发一次，避免前端无思考信息。
                if (!routeResult.thoughtStreamed && StringUtils.isNotBlank(routeDecision.getThought())) {
                    emitThinking(emitter, invocationContext, name(), routeDecision.getThought());
                }

                String action = StringUtils.trimToEmpty(routeDecision.getAction()).toLowerCase();
                if (ACTION_FINAL.equals(action)) {
                    // 最终回复优先用 router reply；为空时回退到最近 worker 输出。
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
                    emitFinal(emitter, invocationContext, "不支持的路由动作：" + action);
                    emitter.onComplete();
                    return;
                }

                String nextAgent = StringUtils.trimToEmpty(routeDecision.getNextAgent());
                BaseAgent workerAgent = workerAgentGroup.get(nextAgent);
                if (workerAgent == null) {
                    emitFinal(emitter, invocationContext, "未知的 nextAgent：" + nextAgent);
                    emitter.onComplete();
                    return;
                }

                // 阶段回复优先用 router reply；为空时回退到最近 worker 输出。
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

            // 超过最大轮次仍未收敛，使用兜底 final。
            String fallbackFinalReply = buildFinalReply("", latestWorkerOutput);
            if (StringUtils.isBlank(fallbackFinalReply)) {
                fallbackFinalReply = "已达到最大路由轮次，当前结果不足以继续细化。";
            }
            emitFinal(emitter, invocationContext, fallbackFinalReply);
            emitter.onComplete();
        } catch (Exception e) {
            // 取消/中断属于可预期退出，不按错误处理。
            if (isCancellationOrInterruption(e, emitter)) {
                if (hasInterruptedCause(e)) {
                    Thread.currentThread().interrupt();
                }
                log.info("Supervisor 工作流已取消或中断：{}", e.toString());
                return;
            }

            log.error("Supervisor 工作流运行失败", e);
            if (!emitter.isCancelled()) {
                emitFinal(emitter, invocationContext, "Supervisor 运行失败：" + e.getMessage());
                emitter.onComplete();
            }
        }
    }

    /**
     * 路由阶段执行：
     * - 调用 routerAgent 并收集原始流输出；
     * - 在 JSON 尚未闭合时，尽力提取 thought/reply 增量输出；
     * - 收尾后解析完整 RouteDecision。
     */
    private RouteResult route(InvocationContext invocationContext, FlowableEmitter<Event> emitter) {
        StringBuilder allRouterOutput = new StringBuilder();
        AtomicInteger thoughtEmittedLength = new AtomicInteger(0);
        AtomicInteger replyEmittedLength = new AtomicInteger(0);
        AtomicInteger replyChunkCount = new AtomicInteger(0);

        routerAgent.runAsync(invocationContext).blockingForEach(event -> {
            if (emitter.isCancelled()) {
                throw new CancellationException("路由阶段检测到 emitter 已取消");
            }
            String content = event.stringifyContent();
            if (StringUtils.isBlank(content)) {
                return;
            }

            // 不能插入额外分隔符，否则可能破坏 JSON 结构。
            allRouterOutput.append(content);

            String partialThought = extractPartialJsonStringField(allRouterOutput.toString(), "thought");
            if (StringUtils.isNotBlank(partialThought) && partialThought.length() > thoughtEmittedLength.get()) {
                String thoughtChunk = partialThought.substring(thoughtEmittedLength.get());
                thoughtEmittedLength.addAndGet(thoughtChunk.length());
                emitThinking(emitter, invocationContext, name(), thoughtChunk);
            }

            String action = StringUtils.trimToEmpty(extractPartialJsonStringField(allRouterOutput.toString(), "action")).toLowerCase();
            if (!ACTION_FINAL.equals(action) && !ACTION_ROUTE.equals(action)) {
                // action 还不稳定时，先不推 reply，防止误输出。
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
                    // 主 Agent 回复流结束标记（空片段 + partial=false）。
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

    /**
     * 执行指定 worker。
     *
     * 行为说明：
     * - worker 输出会透传为 thinking 事件，供前端展示“子 Agent 思考”。
     * - 返回完整 worker 文本，供主循环在 reply/final 为空时兜底。
     */
    private String runWorker(BaseAgent workerAgent, InvocationContext invocationContext, FlowableEmitter<Event> emitter) {
        StringBuilder allWorkerOutput = new StringBuilder();

        workerAgent.runAsync(invocationContext).blockingForEach(event -> {
            if (emitter.isCancelled()) {
                throw new CancellationException("Worker 执行阶段检测到 emitter 已取消");
            }
            String content = event.stringifyContent();
            if (StringUtils.isBlank(content)) {
                return;
            }

            // 不能在分片之间强行插入换行，否则会把 XML/JSON 结构打碎，前端无法识别为 draw.io 内容。
            allWorkerOutput.append(content);
            emitThinking(emitter, invocationContext, event.author(), content);
        });

        return allWorkerOutput.toString().trim();
    }

    /**
     * 解析路由决策 JSON。
     * 支持纯 JSON 或“包裹文本 + JSON”的输出形态。
     */
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
            log.warn("路由决策解析失败，原始输出：{}", rawOutput, e);
            return null;
        }
    }

    /**
     * 提取最外层 JSON 对象。
     * 若无法定位完整花括号，回退返回 trim 后原文。
     */
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
     * 从“可能尚未完整”的 JSON 文本中，尽力提取指定字符串字段。
     * 用于 route 阶段的增量流式展示，不保证严格 JSON 语义完整。
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

    /**
     * 发 thinking 事件。
     */
    private void emitThinking(
            FlowableEmitter<Event> emitter,
            InvocationContext invocationContext,
            String author,
            String content
    ) {
        emitMarkedEvent(emitter, invocationContext, author, AgentStreamMarker.THINKING, content);
    }

    /**
     * 发 route 事件，content 为 nextAgent 名称。
     */
    private void emitRoute(FlowableEmitter<Event> emitter, InvocationContext invocationContext, String nextAgent) {
        emitMarkedEvent(emitter, invocationContext, name(), AgentStreamMarker.ROUTE, nextAgent);
    }

    /**
     * 发阶段回复（reply）事件，按 chunk 分片。
     */
    private void emitReply(FlowableEmitter<Event> emitter, InvocationContext invocationContext, String reply) {
        emitStreamedMarkedEvents(emitter, invocationContext, name(), AgentStreamMarker.REPLY, reply, MAIN_REPLY_CHUNK_SIZE);
    }

    /**
     * 发最终回复（final）事件，按 chunk 分片。
     */
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

    /**
     * 统一事件发射器：在 content 前拼接 marker，由应用层解码为标准事件类型。
     */
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

    /**
     * 将文本分片后输出：
     * - 中间片段：partial=true
     * - 最后片段：partial=false
     */
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

    /**
     * 按固定大小切分字符串，同时避免拆分 Unicode 代理对。
     */
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

            // 避免拆分 Unicode 代理对字符。
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

    /**
     * 生成阶段回复文本：
     * - 优先 routeReply
     * - 否则回退 latestWorkerOutput
     * - 超长则截断，避免中间态过长
     */
    private String buildStageReply(String routeReply, String latestWorkerOutput) {
        String candidate = StringUtils.trimToEmpty(routeReply);
        if (StringUtils.isBlank(candidate)) {
            return "";
        }
        if (looksLikeXml(candidate)) {
            // 阶段性 reply 只给自然语言摘要，不直接透传 XML。
            return "已完成本阶段处理，正在进入下一阶段。";
        }
        if (candidate.length() <= MAX_STAGE_REPLY_LENGTH) {
            return candidate;
        }
        return candidate.substring(0, MAX_STAGE_REPLY_LENGTH) + "...";
    }

    /**
     * 生成最终回复文本，优先 routeReply，次选 latestWorkerOutput。
     */
    private String buildFinalReply(String routeReply, String latestWorkerOutput) {
        return StringUtils.trimToEmpty(StringUtils.defaultIfBlank(routeReply, latestWorkerOutput));
    }

    private boolean looksLikeXml(String text) {
        if (StringUtils.isBlank(text)) {
            return false;
        }
        String normalized = text.toLowerCase();
        return normalized.contains("<mxfile")
                || normalized.contains("</mxfile>")
                || normalized.contains("<mxgraphmodel")
                || normalized.contains("<mxcell");
    }

    /**
     * 判断是否属于“可预期中断”：
     * - emitter 已取消
     * - 异常链包含 InterruptedException/CancellationException
     * - 当前线程已中断
     */
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

    /**
     * 判断异常链中是否存在 InterruptedException。
     * 若存在，调用方应恢复线程中断标记。
     */
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

    /**
     * Router 的结构化决策对象。
     */
    @Data
    public static class RouteDecision {
        /** 路由器思考摘要（用于 thinking 展示）。 */
        private String thought;
        /** 路由动作：route / final。 */
        private String action;
        /** 下一跳 worker 名称（仅 route 时有效）。 */
        private String nextAgent;
        /** 面向用户的回复文本（阶段或最终）。 */
        private String reply;
    }

    /**
     * route() 阶段执行结果：
     * - routeDecision：最终决策
     * - thoughtStreamed/replyStreamed：是否已做过增量输出，用于主循环兜底
     */
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
