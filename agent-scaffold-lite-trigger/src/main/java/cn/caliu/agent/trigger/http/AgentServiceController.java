package cn.caliu.agent.trigger.http;

import com.alibaba.fastjson.JSON;
import cn.caliu.agent.api.IAgentService;
import cn.caliu.agent.api.dto.AiAgentConfigResponseDTO;
import cn.caliu.agent.api.dto.ChatRequestDTO;
import cn.caliu.agent.api.dto.ChatResponseDTO;
import cn.caliu.agent.api.dto.ChatStreamEventResponseDTO;
import cn.caliu.agent.api.dto.CreateSessionRequestDTO;
import cn.caliu.agent.api.dto.CreateSessionResponseDTO;
import cn.caliu.agent.api.response.Response;
import cn.caliu.agent.domain.agent.model.valobj.AiAgentConfigTableVO;
import cn.caliu.agent.domain.agent.service.IChatService;
import cn.caliu.agent.types.common.AgentStreamMarker;
import cn.caliu.agent.types.enums.ResponseCode;
import cn.caliu.agent.types.exception.AppException;
import com.google.adk.events.Event;
import lombok.extern.slf4j.Slf4j;
import io.reactivex.rxjava3.disposables.Disposable;
import io.reactivex.rxjava3.schedulers.Schedulers;
import org.apache.commons.lang3.StringUtils;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.ResponseBodyEmitter;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import javax.annotation.Resource;
import java.util.List;
import java.util.stream.Collectors;

@Slf4j
@RestController
@RequestMapping("/api/v1/")
@CrossOrigin("*")
public class AgentServiceController implements IAgentService {

    @Resource
    private IChatService chatService;

    @RequestMapping(value = "query_ai_agent_config_list", method = RequestMethod.GET)
    @Override
    public Response<List<AiAgentConfigResponseDTO>> queryAiAgentConfigList() {
        try {
            List<AiAgentConfigTableVO.Agent> agentConfigs = chatService.queryAiAgentConfigList();

            List<AiAgentConfigResponseDTO> responseDTOS = agentConfigs.stream().map(agentConfig -> {
                AiAgentConfigResponseDTO responseDTO = new AiAgentConfigResponseDTO();
                responseDTO.setAgentId(agentConfig.getAgentId());
                responseDTO.setAgentName(agentConfig.getAgentName());
                responseDTO.setAgentDesc(agentConfig.getAgentDesc());
                return responseDTO;
            }).collect(Collectors.toList());

            return Response.<List<AiAgentConfigResponseDTO>>builder()
                    .code(ResponseCode.SUCCESS.getCode())
                    .info(ResponseCode.SUCCESS.getInfo())
                    .data(responseDTOS)
                    .build();

        } catch (AppException e) {
            log.error("query ai agent config list failed", e);
            return Response.<List<AiAgentConfigResponseDTO>>builder()
                    .code(e.getCode())
                    .info(e.getInfo())
                    .build();
        } catch (Exception e) {
            log.error("query ai agent config list failed", e);
            return Response.<List<AiAgentConfigResponseDTO>>builder()
                    .code(ResponseCode.UN_ERROR.getCode())
                    .info(ResponseCode.UN_ERROR.getInfo())
                    .build();
        }
    }

    @RequestMapping(value = "create_session", method = RequestMethod.POST)
    @Override
    public Response<CreateSessionResponseDTO> createSession(@RequestBody CreateSessionRequestDTO requestDTO) {
        try {
            String sessionId = chatService.createSession(requestDTO.getAgentId(), requestDTO.getUserId());

            CreateSessionResponseDTO responseDTO = new CreateSessionResponseDTO();
            responseDTO.setSessionId(sessionId);

            return Response.<CreateSessionResponseDTO>builder()
                    .code(ResponseCode.SUCCESS.getCode())
                    .info(ResponseCode.SUCCESS.getInfo())
                    .data(responseDTO)
                    .build();
        } catch (AppException e) {
            log.error("create session failed", e);
            return Response.<CreateSessionResponseDTO>builder()
                    .code(e.getCode())
                    .info(e.getInfo())
                    .build();
        } catch (Exception e) {
            log.error("create session failed", e);
            return Response.<CreateSessionResponseDTO>builder()
                    .code(ResponseCode.UN_ERROR.getCode())
                    .info(ResponseCode.UN_ERROR.getInfo())
                    .build();
        }
    }

    @RequestMapping(value = "chat", method = RequestMethod.POST)
    @Override
    public Response<ChatResponseDTO> chat(@RequestBody ChatRequestDTO requestDTO) {
        try {
            String sessionId = requestDTO.getSessionId();
            if (StringUtils.isBlank(sessionId)) {
                sessionId = chatService.createSession(requestDTO.getAgentId(), requestDTO.getUserId());
            }

            List<String> messages = chatService.handleMessage(requestDTO.getAgentId(), requestDTO.getUserId(), sessionId, requestDTO.getMessage());

            ChatResponseDTO responseDTO = new ChatResponseDTO();
            responseDTO.setContent(String.join("\n", messages));

            return Response.<ChatResponseDTO>builder()
                    .code(ResponseCode.SUCCESS.getCode())
                    .info(ResponseCode.SUCCESS.getInfo())
                    .data(responseDTO)
                    .build();
        } catch (AppException e) {
            log.error("chat failed", e);
            return Response.<ChatResponseDTO>builder()
                    .code(e.getCode())
                    .info(e.getInfo())
                    .build();
        } catch (Exception e) {
            log.error("chat failed", e);
            return Response.<ChatResponseDTO>builder()
                    .code(ResponseCode.UN_ERROR.getCode())
                    .info(ResponseCode.UN_ERROR.getInfo())
                    .build();
        }
    }

    @RequestMapping(
            value = "chat_stream",
            method = RequestMethod.POST,
            produces = MediaType.TEXT_EVENT_STREAM_VALUE
    )
    @Override
    public SseEmitter chatStream(@RequestBody ChatRequestDTO requestDTO) {
        SseEmitter emitter = new SseEmitter(3 * 60 * 1000L);
        try {
            String sessionId = requestDTO.getSessionId();
            if (StringUtils.isBlank(sessionId)) {
                sessionId = chatService.createSession(requestDTO.getAgentId(), requestDTO.getUserId());
            }

            Disposable disposable = chatService
                    .handleMessageStream(requestDTO.getAgentId(), requestDTO.getUserId(), sessionId, requestDTO.getMessage())
                    .subscribeOn(Schedulers.io())
                    .subscribe(
                            event -> {
                                try {
                                    ChatStreamEventResponseDTO streamEvent = mapToStreamEvent(event);
                                    boolean completionSignal =
                                            ("final".equals(streamEvent.getType()) || "reply".equals(streamEvent.getType()))
                                                    && !Boolean.TRUE.equals(streamEvent.getPartial());
                                    if (StringUtils.isBlank(streamEvent.getContent())
                                            && !"route".equals(streamEvent.getType())
                                            && !completionSignal) {
                                        return;
                                    }
                                    emitter.send(SseEmitter.event()
                                            .name("message")
                                            .data(JSON.toJSONString(streamEvent)));
                                } catch (Exception e) {
                                    log.error("chat stream send event failed", e);
                                    emitter.completeWithError(e);
                                }
                            },
                            emitter::completeWithError,
                            emitter::complete
                    );

            emitter.onCompletion(disposable::dispose);
            emitter.onTimeout(() -> {
                disposable.dispose();
                emitter.complete();
            });
            emitter.onError(throwable -> disposable.dispose());
        } catch (Exception e) {
            log.error("chat stream failed", e);
            emitter.completeWithError(e);
        }
        return emitter;
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

        dto.setType(event.finalResponse() ? "final" : "thinking");
        dto.setContent(content);
        return dto;
    }

}
