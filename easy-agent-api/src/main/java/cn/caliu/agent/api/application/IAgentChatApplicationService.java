package cn.caliu.agent.api.application;

import cn.caliu.agent.api.dto.agent.chat.response.AiAgentConfigResponseDTO;
import cn.caliu.agent.api.dto.agent.chat.request.ChatRequestDTO;
import cn.caliu.agent.api.dto.agent.chat.response.ChatResponseDTO;
import cn.caliu.agent.api.dto.agent.chat.event.ChatStreamEventResponseDTO;
import cn.caliu.agent.api.dto.agent.chat.request.CreateSessionRequestDTO;
import cn.caliu.agent.api.dto.agent.chat.request.DeleteSessionRequestDTO;
import cn.caliu.agent.api.dto.agent.chat.response.CreateSessionResponseDTO;
import cn.caliu.agent.api.dto.agent.chat.response.SessionHistoryMessageResponseDTO;
import cn.caliu.agent.api.dto.agent.chat.response.SessionHistorySummaryResponseDTO;
import io.reactivex.rxjava3.core.Flowable;

import java.util.List;

public interface IAgentChatApplicationService {

    List<AiAgentConfigResponseDTO> queryAiAgentConfigList();

    CreateSessionResponseDTO createSession(CreateSessionRequestDTO requestDTO);

    ChatResponseDTO chat(ChatRequestDTO requestDTO);

    Flowable<ChatStreamEventResponseDTO> chatStream(ChatRequestDTO requestDTO);

    List<SessionHistorySummaryResponseDTO> querySessionHistoryList(String userId, String agentId);

    List<SessionHistoryMessageResponseDTO> querySessionMessageList(String sessionId);

    Boolean deleteSession(DeleteSessionRequestDTO requestDTO);

}

