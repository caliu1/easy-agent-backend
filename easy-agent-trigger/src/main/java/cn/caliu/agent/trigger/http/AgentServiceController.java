package cn.caliu.agent.trigger.http;

import com.alibaba.fastjson.JSON;
import cn.caliu.agent.api.IAgentService;
import cn.caliu.agent.api.application.IAgentChatApplicationService;
import cn.caliu.agent.api.dto.agent.chat.response.AiAgentConfigResponseDTO;
import cn.caliu.agent.api.dto.agent.chat.request.ChatRequestDTO;
import cn.caliu.agent.api.dto.agent.chat.response.ChatResponseDTO;
import cn.caliu.agent.api.dto.agent.chat.request.CreateSessionRequestDTO;
import cn.caliu.agent.api.dto.agent.chat.request.DeleteSessionRequestDTO;
import cn.caliu.agent.api.dto.agent.chat.response.CreateSessionResponseDTO;
import cn.caliu.agent.api.dto.agent.chat.response.SessionHistoryMessageResponseDTO;
import cn.caliu.agent.api.dto.agent.chat.response.SessionHistorySummaryResponseDTO;
import cn.caliu.agent.api.response.Response;
import cn.caliu.agent.types.enums.ResponseCode;
import cn.caliu.agent.types.exception.AppException;
import io.reactivex.rxjava3.disposables.Disposable;
import io.reactivex.rxjava3.schedulers.Schedulers;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import javax.annotation.Resource;
import java.util.List;

@Slf4j
@RestController
@RequestMapping("/api/v1/")
@CrossOrigin("*")
public class AgentServiceController implements IAgentService {

    private static final long CHAT_STREAM_TIMEOUT_MS = 20 * 60 * 1000L;


    @Resource
    private IAgentChatApplicationService agentChatApplicationService;

    @RequestMapping(value = "query_ai_agent_config_list", method = RequestMethod.GET)
    @Override
    public Response<List<AiAgentConfigResponseDTO>> queryAiAgentConfigList() {
        try {
            List<AiAgentConfigResponseDTO> responseDTOS = agentChatApplicationService.queryAiAgentConfigList();
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
            CreateSessionResponseDTO responseDTO = agentChatApplicationService.createSession(requestDTO);
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
            ChatResponseDTO responseDTO = agentChatApplicationService.chat(requestDTO);
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

    @RequestMapping(value = "query_session_history_list", method = RequestMethod.GET)
    @Override
    public Response<List<SessionHistorySummaryResponseDTO>> querySessionHistoryList(
            @RequestParam("userId") String userId,
            @RequestParam(value = "agentId", required = false) String agentId
    ) {
        try {
            List<SessionHistorySummaryResponseDTO> sessionList =
                    agentChatApplicationService.querySessionHistoryList(userId, agentId);
            return Response.<List<SessionHistorySummaryResponseDTO>>builder()
                    .code(ResponseCode.SUCCESS.getCode())
                    .info(ResponseCode.SUCCESS.getInfo())
                    .data(sessionList)
                    .build();
        } catch (AppException e) {
            log.error("query session history list failed", e);
            return Response.<List<SessionHistorySummaryResponseDTO>>builder()
                    .code(e.getCode())
                    .info(e.getInfo())
                    .build();
        } catch (Exception e) {
            log.error("query session history list failed", e);
            return Response.<List<SessionHistorySummaryResponseDTO>>builder()
                    .code(ResponseCode.UN_ERROR.getCode())
                    .info(ResponseCode.UN_ERROR.getInfo())
                    .build();
        }
    }

    @RequestMapping(value = "query_session_message_list", method = RequestMethod.GET)
    @Override
    public Response<List<SessionHistoryMessageResponseDTO>> querySessionMessageList(@RequestParam("sessionId") String sessionId) {
        try {
            List<SessionHistoryMessageResponseDTO> messageList =
                    agentChatApplicationService.querySessionMessageList(sessionId);
            return Response.<List<SessionHistoryMessageResponseDTO>>builder()
                    .code(ResponseCode.SUCCESS.getCode())
                    .info(ResponseCode.SUCCESS.getInfo())
                    .data(messageList)
                    .build();
        } catch (AppException e) {
            log.error("query session message list failed", e);
            return Response.<List<SessionHistoryMessageResponseDTO>>builder()
                    .code(e.getCode())
                    .info(e.getInfo())
                    .build();
        } catch (Exception e) {
            log.error("query session message list failed", e);
            return Response.<List<SessionHistoryMessageResponseDTO>>builder()
                    .code(ResponseCode.UN_ERROR.getCode())
                    .info(ResponseCode.UN_ERROR.getInfo())
                    .build();
        }
    }

    @RequestMapping(value = "delete_session", method = RequestMethod.POST)
    @Override
    public Response<Boolean> deleteSession(@RequestBody DeleteSessionRequestDTO requestDTO) {
        try {
            Boolean deleted = agentChatApplicationService.deleteSession(requestDTO);
            return Response.<Boolean>builder()
                    .code(ResponseCode.SUCCESS.getCode())
                    .info(ResponseCode.SUCCESS.getInfo())
                    .data(Boolean.TRUE.equals(deleted))
                    .build();
        } catch (AppException e) {
            log.error("delete session failed", e);
            return Response.<Boolean>builder()
                    .code(e.getCode())
                    .info(e.getInfo())
                    .build();
        } catch (Exception e) {
            log.error("delete session failed", e);
            return Response.<Boolean>builder()
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
    public SseEmitter chatStream(@RequestBody ChatRequestDTO requestDTO) {
        SseEmitter emitter = new SseEmitter(CHAT_STREAM_TIMEOUT_MS);
        try {
            Disposable disposable = agentChatApplicationService
                    .chatStream(requestDTO)
                    .subscribeOn(Schedulers.io())
                    .subscribe(
                            streamEvent -> {
                                try {
                                    emitter.send(SseEmitter.event()
                                            .name("message")
                                            .data(JSON.toJSONString(streamEvent)));
                                } catch (Exception e) {
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

}

