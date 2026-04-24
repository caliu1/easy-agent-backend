package cn.caliu.agent.app.service;

import cn.caliu.agent.api.application.IAgentChatApplicationService;
import cn.caliu.agent.api.dto.agent.chat.response.AiAgentConfigResponseDTO;
import cn.caliu.agent.api.dto.agent.chat.request.ChatRequestDTO;
import cn.caliu.agent.api.dto.agent.chat.response.ChatResponseDTO;
import cn.caliu.agent.api.dto.agent.chat.event.ChatStreamEventResponseDTO;
import cn.caliu.agent.api.dto.agent.chat.request.CreateSessionRequestDTO;
import cn.caliu.agent.api.dto.agent.chat.response.CreateSessionResponseDTO;
import cn.caliu.agent.domain.agent.model.valobj.AiAgentConfigTableVO;
import cn.caliu.agent.domain.agent.service.IChatService;
import cn.caliu.agent.domain.session.service.ISessionService;
import cn.caliu.agent.types.common.AgentStreamMarker;
import com.google.adk.events.Event;
import io.reactivex.rxjava3.core.Flowable;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

@Service
public class AgentChatApplicationService implements IAgentChatApplicationService {

    @Resource
    private IChatService chatService;
    @Resource
    private ISessionService sessionService;

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

        List<String> messages = chatService.handleMessage(
                requestDTO.getAgentId(),
                requestDTO.getUserId(),
                sessionId,
                requestDTO.getMessage()
        );

        ChatResponseDTO responseDTO = new ChatResponseDTO();
        responseDTO.setContent(String.join("\n", messages));
        return responseDTO;
    }

    @Override
    public Flowable<ChatStreamEventResponseDTO> chatStream(ChatRequestDTO requestDTO) {
        String sessionId = requestDTO.getSessionId();
        if (StringUtils.isBlank(sessionId)) {
            sessionId = sessionService.createSession(requestDTO.getAgentId(), requestDTO.getUserId());
        }

        return chatService
                .handleMessageStream(requestDTO.getAgentId(), requestDTO.getUserId(), sessionId, requestDTO.getMessage())
                .map(this::mapToStreamEvent)
                .filter(this::shouldEmitStreamEvent);
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

}

