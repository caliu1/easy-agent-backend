package cn.caliu.agent.api;

import cn.caliu.agent.api.dto.agent.chat.request.ChatRequestDTO;
import cn.caliu.agent.api.dto.agent.chat.request.CreateSessionRequestDTO;
import cn.caliu.agent.api.dto.agent.chat.request.DeleteSessionRequestDTO;
import cn.caliu.agent.api.dto.agent.chat.response.AiAgentConfigResponseDTO;
import cn.caliu.agent.api.dto.agent.chat.response.ChatResponseDTO;
import cn.caliu.agent.api.dto.agent.chat.response.CreateSessionResponseDTO;
import cn.caliu.agent.api.dto.agent.chat.response.SessionHistoryMessageResponseDTO;
import cn.caliu.agent.api.dto.agent.chat.response.SessionHistorySummaryResponseDTO;
import cn.caliu.agent.api.response.Response;

import java.util.List;

/**
 * 聊天与会话能力对外接口。
 *
 * 面向 Trigger 层提供：
 * 1. Agent 列表查询
 * 2. 会话创建/删除
 * 3. 同步聊天与历史查询
 */
public interface IAgentService {

    Response<List<AiAgentConfigResponseDTO>> queryAiAgentConfigList();

    Response<CreateSessionResponseDTO> createSession(CreateSessionRequestDTO requestDTO);

    Response<ChatResponseDTO> chat(ChatRequestDTO requestDTO);

    Response<List<SessionHistorySummaryResponseDTO>> querySessionHistoryList(String userId, String agentId);

    Response<List<SessionHistoryMessageResponseDTO>> querySessionMessageList(String sessionId);

    Response<Boolean> deleteSession(DeleteSessionRequestDTO requestDTO);

}
