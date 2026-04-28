package cn.caliu.agent.app.service;

import com.alibaba.fastjson.JSON;
import cn.caliu.agent.api.application.IAgentChatApplicationService;
import cn.caliu.agent.api.dto.agent.chat.response.AiAgentConfigResponseDTO;
import cn.caliu.agent.api.dto.agent.chat.request.ChatRequestDTO;
import cn.caliu.agent.api.dto.agent.chat.response.ChatResponseDTO;
import cn.caliu.agent.api.dto.agent.chat.event.ChatStreamEventResponseDTO;
import cn.caliu.agent.api.dto.agent.chat.request.CreateSessionRequestDTO;
import cn.caliu.agent.api.dto.agent.chat.request.DeleteSessionRequestDTO;
import cn.caliu.agent.api.dto.agent.chat.response.CreateSessionResponseDTO;
import cn.caliu.agent.api.dto.agent.chat.response.SessionHistoryMessageResponseDTO;
import cn.caliu.agent.api.dto.agent.chat.response.SessionHistorySummaryResponseDTO;
import cn.caliu.agent.domain.agent.model.valobj.AiAgentConfigTableVO;
import cn.caliu.agent.domain.agent.service.IChatService;
import cn.caliu.agent.domain.session.model.entity.AgentSessionHistoryEntity;
import cn.caliu.agent.domain.session.model.entity.AgentSessionMessageEntity;
import cn.caliu.agent.domain.session.service.ISessionHistoryService;
import cn.caliu.agent.domain.session.service.ISessionService;
import cn.caliu.agent.types.common.AgentStreamMarker;
import com.google.adk.events.Event;
import io.reactivex.rxjava3.core.Flowable;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 聊天应用服务实现。
 *
 * 主要职责：
 * 1. 编排会话创建与消息收发。
 * 2. 将 ADK 原始 Event 映射为前端可识别的流式事件类型。
 * 3. 按事件粒度落库存储（thinking/route/reply/final），支撑历史还原。
 */
@Service
public class AgentChatApplicationService implements IAgentChatApplicationService {

    @Resource
    private IChatService chatService;
    @Resource
    private ISessionService sessionService;
    @Resource
    private ISessionHistoryService sessionHistoryService;

    @Override
    public List<AiAgentConfigResponseDTO> queryAiAgentConfigList() {
        List<AiAgentConfigTableVO.Agent> agentConfigs = chatService.queryAiAgentConfigList();
        if (agentConfigs == null || agentConfigs.isEmpty()) {
            return Collections.emptyList();
        }

        return agentConfigs.stream().map(agentConfig -> {
            AiAgentConfigResponseDTO responseDTO = new AiAgentConfigResponseDTO();
            responseDTO.setAgentId(agentConfig.getAgentId());
            responseDTO.setAgentName(agentConfig.getAgentName());
            responseDTO.setAgentDesc(agentConfig.getAgentDesc());
            return responseDTO;
        }).collect(Collectors.toList());
    }

    @Override
    public CreateSessionResponseDTO createSession(CreateSessionRequestDTO requestDTO) {
        String sessionId = sessionService.createSession(requestDTO.getAgentId(), requestDTO.getUserId());
        CreateSessionResponseDTO responseDTO = new CreateSessionResponseDTO();
        responseDTO.setSessionId(sessionId);
        return responseDTO;
    }

    @Override
    public ChatResponseDTO chat(ChatRequestDTO requestDTO) {
        String sessionId = requestDTO.getSessionId();
        if (StringUtils.isBlank(sessionId)) {
            sessionId = sessionService.createSession(requestDTO.getAgentId(), requestDTO.getUserId());
        }
        sessionHistoryService.appendUserMessage(sessionId, requestDTO.getAgentId(), requestDTO.getUserId(), requestDTO.getMessage());

        List<String> messages = chatService.handleMessage(
                requestDTO.getAgentId(),
                requestDTO.getUserId(),
                sessionId,
                requestDTO.getMessage()
        );
        String assistantReply = String.join("\n", messages);
        sessionHistoryService.appendAssistantMessage(sessionId, requestDTO.getAgentId(), requestDTO.getUserId(), assistantReply);

        ChatResponseDTO responseDTO = new ChatResponseDTO();
        responseDTO.setContent(assistantReply);
        return responseDTO;
    }

    @Override
    public Flowable<ChatStreamEventResponseDTO> chatStream(ChatRequestDTO requestDTO) {
        String sessionId = requestDTO.getSessionId();
        if (StringUtils.isBlank(sessionId)) {
            sessionId = sessionService.createSession(requestDTO.getAgentId(), requestDTO.getUserId());
        }
        sessionHistoryService.appendUserMessage(sessionId, requestDTO.getAgentId(), requestDTO.getUserId(), requestDTO.getMessage());

        String stableSessionId = sessionId;
        String stableAgentId = requestDTO.getAgentId();
        String stableUserId = requestDTO.getUserId();
        StringBuilder replyBuilder = new StringBuilder();
        StringBuilder finalBuilder = new StringBuilder();
        StringBuilder thinkingSegmentBuilder = new StringBuilder();
        StringBuilder replySegmentBuilder = new StringBuilder();
        String[] thinkingAgentRef = new String[1];
        String[] replyAgentRef = new String[1];
        boolean[] hasIntermediateEventsRef = new boolean[1];

        return chatService
                .handleMessageStream(requestDTO.getAgentId(), requestDTO.getUserId(), stableSessionId, requestDTO.getMessage())
                .map(this::mapToStreamEvent)
                .doOnNext(event -> {
                    collectAssistantReply(event, replyBuilder, finalBuilder);
                    if (isIntermediateStreamEvent(event)) {
                        hasIntermediateEventsRef[0] = true;
                    }
                    persistStreamEvent(
                            stableSessionId,
                            stableAgentId,
                            stableUserId,
                            event,
                            thinkingSegmentBuilder,
                            replySegmentBuilder,
                            thinkingAgentRef,
                            replyAgentRef
                    );
                })
                .doOnComplete(() -> {
                    flushThinkingSegment(stableSessionId, stableAgentId, stableUserId, thinkingSegmentBuilder, thinkingAgentRef);
                    flushReplySegment(stableSessionId, stableAgentId, stableUserId, replySegmentBuilder, replyAgentRef);
                    persistStreamAssistantReply(
                            stableSessionId,
                            stableAgentId,
                            stableUserId,
                            replyBuilder,
                            finalBuilder,
                            hasIntermediateEventsRef[0]
                    );
                })
                .filter(this::shouldEmitStreamEvent);
    }

    @Override
    public List<SessionHistorySummaryResponseDTO> querySessionHistoryList(String userId, String agentId) {
        return sessionHistoryService.querySessionList(userId, agentId)
                .stream()
                .map(this::toSessionHistorySummaryResponse)
                .collect(Collectors.toList());
    }

    @Override
    public List<SessionHistoryMessageResponseDTO> querySessionMessageList(String sessionId) {
        return sessionHistoryService.querySessionMessageList(sessionId)
                .stream()
                .map(this::toSessionHistoryMessageResponse)
                .collect(Collectors.toList());
    }

    @Override
    public Boolean deleteSession(DeleteSessionRequestDTO requestDTO) {
        if (requestDTO == null) {
            return false;
        }
        return sessionService.deleteSession(requestDTO.getSessionId(), requestDTO.getUserId());
    }

    private ChatStreamEventResponseDTO mapToStreamEvent(Event event) {
        String content = StringUtils.defaultString(event.stringifyContent());
        ChatStreamEventResponseDTO dto = new ChatStreamEventResponseDTO();

        dto.setAgentName(event.author());
        dto.setPartial(event.partial().orElse(false));
        dto.setFinalResponse(event.finalResponse());

        if (content.startsWith(AgentStreamMarker.THINKING)) {
            dto.setType("thinking");
            dto.setContent(content.substring(AgentStreamMarker.THINKING.length()));
            dto.setFinalResponse(false);
            return dto;
        }

        if (content.startsWith(AgentStreamMarker.ROUTE)) {
            String nextAgent = StringUtils.trimToEmpty(content.substring(AgentStreamMarker.ROUTE.length()));
            dto.setType("route");
            dto.setRouteTarget(nextAgent);
            dto.setContent(nextAgent);
            dto.setFinalResponse(false);
            return dto;
        }

        if (content.startsWith(AgentStreamMarker.REPLY)) {
            dto.setType("reply");
            dto.setContent(StringUtils.defaultString(content.substring(AgentStreamMarker.REPLY.length())));
            dto.setFinalResponse(false);
            return dto;
        }

        if (content.startsWith(AgentStreamMarker.FINAL)) {
            dto.setType("final");
            dto.setContent(StringUtils.defaultString(content.substring(AgentStreamMarker.FINAL.length())));
            dto.setFinalResponse(!dto.getPartial());
            return dto;
        }

        dto.setType(event.finalResponse() ? "final" : "reply");
        dto.setContent(content);
        return dto;
    }

    private boolean shouldEmitStreamEvent(ChatStreamEventResponseDTO streamEvent) {
        if (streamEvent == null) {
            return false;
        }

        boolean completionSignal =
                ("final".equals(streamEvent.getType()) || "reply".equals(streamEvent.getType()))
                        && !Boolean.TRUE.equals(streamEvent.getPartial());

        return !StringUtils.isBlank(streamEvent.getContent())
                || "route".equals(streamEvent.getType())
                || completionSignal;
    }

    private void collectAssistantReply(
            ChatStreamEventResponseDTO streamEvent,
            StringBuilder replyBuilder,
            StringBuilder finalBuilder
    ) {
        if (streamEvent == null || StringUtils.isBlank(streamEvent.getContent())) {
            return;
        }

        if ("final".equals(streamEvent.getType())) {
            finalBuilder.append(streamEvent.getContent());
            return;
        }

        if ("reply".equals(streamEvent.getType())) {
            replyBuilder.append(streamEvent.getContent());
        }
    }

    private void persistStreamEvent(
            String sessionId,
            String agentId,
            String userId,
            ChatStreamEventResponseDTO streamEvent,
            StringBuilder thinkingSegmentBuilder,
            StringBuilder replySegmentBuilder,
            String[] thinkingAgentRef,
            String[] replyAgentRef
    ) {
        if (streamEvent == null) {
            return;
        }

        String eventType = StringUtils.lowerCase(StringUtils.trimToEmpty(streamEvent.getType()), Locale.ROOT);
        String eventContent = StringUtils.defaultString(streamEvent.getContent());
        boolean partial = Boolean.TRUE.equals(streamEvent.getPartial());
        String currentAgentName = StringUtils.defaultString(streamEvent.getAgentName());

        if ("thinking".equals(eventType)) {
            if (StringUtils.isBlank(eventContent)) {
                return;
            }

            if (StringUtils.isNotBlank(replySegmentBuilder == null ? "" : replySegmentBuilder.toString())) {
                flushReplySegment(sessionId, agentId, userId, replySegmentBuilder, replyAgentRef);
            }

            if (thinkingAgentRef != null
                    && StringUtils.isNotBlank(thinkingAgentRef[0])
                    && !StringUtils.equals(thinkingAgentRef[0], currentAgentName)) {
                flushThinkingSegment(sessionId, agentId, userId, thinkingSegmentBuilder, thinkingAgentRef);
            }
            if (thinkingAgentRef != null) {
                thinkingAgentRef[0] = currentAgentName;
            }
            thinkingSegmentBuilder.append(eventContent);
            return;
        }

        if ("reply".equals(eventType)) {
            flushThinkingSegment(sessionId, agentId, userId, thinkingSegmentBuilder, thinkingAgentRef);

            if (replyAgentRef != null
                    && StringUtils.isNotBlank(replyAgentRef[0])
                    && !StringUtils.equals(replyAgentRef[0], currentAgentName)
                    && StringUtils.isNotBlank(replySegmentBuilder == null ? "" : replySegmentBuilder.toString())) {
                flushReplySegment(sessionId, agentId, userId, replySegmentBuilder, replyAgentRef);
            }
            if (replyAgentRef != null) {
                replyAgentRef[0] = currentAgentName;
            }

            if (StringUtils.isBlank(eventContent)) {
                if (!partial) {
                    flushReplySegment(sessionId, agentId, userId, replySegmentBuilder, replyAgentRef);
                }
                return;
            }

            replySegmentBuilder.append(eventContent);
            if (partial) {
                return;
            }

            flushReplySegment(sessionId, agentId, userId, replySegmentBuilder, replyAgentRef);
            return;
        }

        if ("final".equals(eventType)) {
            flushThinkingSegment(sessionId, agentId, userId, thinkingSegmentBuilder, thinkingAgentRef);
            flushReplySegment(sessionId, agentId, userId, replySegmentBuilder, replyAgentRef);
            return;
        }

        if ("route".equals(eventType)) {
            flushThinkingSegment(sessionId, agentId, userId, thinkingSegmentBuilder, thinkingAgentRef);
            flushReplySegment(sessionId, agentId, userId, replySegmentBuilder, replyAgentRef);
            String routeTarget = StringUtils.defaultIfBlank(streamEvent.getRouteTarget(), eventContent);
            if (StringUtils.isBlank(routeTarget)) {
                return;
            }
            appendHistoryEvent(sessionId, agentId, userId, "route", streamEvent.getAgentName(), routeTarget, routeTarget);
            return;
        }

        flushThinkingSegment(sessionId, agentId, userId, thinkingSegmentBuilder, thinkingAgentRef);
        flushReplySegment(sessionId, agentId, userId, replySegmentBuilder, replyAgentRef);

        if (StringUtils.isBlank(eventContent)) {
            return;
        }
        appendHistoryEvent(sessionId, agentId, userId, "system", streamEvent.getAgentName(), eventContent, null);
    }

    private void flushThinkingSegment(
            String sessionId,
            String agentId,
            String userId,
            StringBuilder thinkingSegmentBuilder,
            String[] thinkingAgentRef
    ) {
        String thinkingSegment = thinkingSegmentBuilder == null ? "" : thinkingSegmentBuilder.toString();
        if (StringUtils.isBlank(thinkingSegment)) {
            return;
        }
        if (thinkingSegmentBuilder != null) {
            thinkingSegmentBuilder.setLength(0);
        }

        String thinkingAgent = thinkingAgentRef == null ? null : thinkingAgentRef[0];
        appendHistoryEvent(sessionId, agentId, userId, "thinking", thinkingAgent, thinkingSegment, null);
        if (thinkingAgentRef != null) {
            thinkingAgentRef[0] = null;
        }
    }

    private void flushReplySegment(
            String sessionId,
            String agentId,
            String userId,
            StringBuilder replySegmentBuilder,
            String[] replyAgentRef
    ) {
        String replySegment = replySegmentBuilder == null ? "" : replySegmentBuilder.toString();
        if (StringUtils.isBlank(replySegment)) {
            return;
        }
        if (replySegmentBuilder != null) {
            replySegmentBuilder.setLength(0);
        }
        String replyAgent = replyAgentRef == null ? null : replyAgentRef[0];
        appendHistoryEvent(sessionId, agentId, userId, "reply", replyAgent, replySegment, null);
        if (replyAgentRef != null) {
            replyAgentRef[0] = null;
        }
    }

    private void appendHistoryEvent(
            String sessionId,
            String agentId,
            String userId,
            String eventType,
            String agentName,
            String content,
            String routeTarget
    ) {
        if (StringUtils.isBlank(sessionId) || StringUtils.isBlank(eventType) || StringUtils.isBlank(content)) {
            return;
        }

        Map<String, Object> payload = buildHistoryEventPayload(eventType, agentName, content, routeTarget);
        String eventJson = JSON.toJSONString(payload);
        sessionHistoryService.appendSystemMessage(
                sessionId,
                agentId,
                userId,
                AgentStreamMarker.HISTORY_EVENT + eventJson
        );
    }

    private Map<String, Object> buildHistoryEventPayload(
            String eventType,
            String agentName,
            String content,
            String routeTarget
    ) {
        Map<String, Object> payload = new HashMap<>(8);
        payload.put("type", StringUtils.lowerCase(StringUtils.trimToEmpty(eventType), Locale.ROOT));
        payload.put("agentName", StringUtils.defaultString(agentName));
        payload.put("content", StringUtils.defaultString(content));
        if (StringUtils.isNotBlank(routeTarget)) {
            payload.put("routeTarget", routeTarget);
        }
        return payload;
    }

    private void persistStreamAssistantReply(
            String sessionId,
            String agentId,
            String userId,
            StringBuilder replyBuilder,
            StringBuilder finalBuilder,
            boolean hasIntermediateEvents
    ) {
        String finalText = finalBuilder == null ? "" : finalBuilder.toString();
        String replyText = replyBuilder == null ? "" : replyBuilder.toString();
        String assistantContent;
        if (hasIntermediateEvents) {
            // 多智能体/多阶段：优先 final，保持现有行为
            assistantContent = StringUtils.isNotBlank(finalText) ? finalText : replyText;
        } else {
            // 单智能体：优先完整 reply，避免 final 仅尾片段覆盖整段内容
            assistantContent = StringUtils.isNotBlank(replyText) ? replyText : finalText;
        }
        sessionHistoryService.appendAssistantMessage(sessionId, agentId, userId, assistantContent);
    }

    private boolean isIntermediateStreamEvent(ChatStreamEventResponseDTO streamEvent) {
        if (streamEvent == null) {
            return false;
        }
        String eventType = StringUtils.lowerCase(StringUtils.trimToEmpty(streamEvent.getType()), Locale.ROOT);
        if ("thinking".equals(eventType) || "route".equals(eventType)) {
            return true;
        }

        return !"reply".equals(eventType)
                && !"final".equals(eventType)
                && StringUtils.isNotBlank(streamEvent.getContent());
    }

    private SessionHistorySummaryResponseDTO toSessionHistorySummaryResponse(AgentSessionHistoryEntity source) {
        SessionHistorySummaryResponseDTO dto = new SessionHistorySummaryResponseDTO();
        dto.setSessionId(source.getSessionId());
        dto.setAgentId(source.getAgentId());
        dto.setUserId(source.getUserId());
        dto.setSessionTitle(source.getSessionTitle());
        dto.setLatestMessage(source.getLatestMessage());
        dto.setMessageCount(source.getMessageCount());
        dto.setTotalTokens(source.getTotalTokens());
        dto.setCreateTime(source.getCreateTime());
        dto.setUpdateTime(source.getUpdateTime());
        return dto;
    }

    private SessionHistoryMessageResponseDTO toSessionHistoryMessageResponse(AgentSessionMessageEntity source) {
        SessionHistoryMessageResponseDTO dto = new SessionHistoryMessageResponseDTO();
        dto.setId(source.getId());
        dto.setSessionId(source.getSessionId());
        dto.setAgentId(source.getAgentId());
        dto.setUserId(source.getUserId());
        dto.setRole(source.getRole());
        dto.setContent(source.getContent());
        dto.setCreateTime(source.getCreateTime());
        return dto;
    }

}

