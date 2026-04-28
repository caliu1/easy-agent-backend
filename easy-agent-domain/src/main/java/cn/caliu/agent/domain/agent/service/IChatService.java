package cn.caliu.agent.domain.agent.service;

import cn.caliu.agent.domain.agent.model.entity.ChatCommandEntity;
import cn.caliu.agent.domain.agent.model.valobj.AiAgentConfigTableVO;
import com.google.adk.events.Event;
import io.reactivex.rxjava3.core.Flowable;

import java.util.List;

/**
 * 聊天领域服务接口。
 *
 * 作用：
 * 1. 提供同步/流式聊天能力。
 * 2. 负责按会话上下文选择并调用运行时 Agent。
 * 3. 提供可用 Agent 列表查询能力。
 */
public interface IChatService {

    List<AiAgentConfigTableVO.Agent> queryAiAgentConfigList();

    List<String> handleMessage(String agentId, String userId, String sessionId, String message);

    Flowable<Event> handleMessageStream(String agentId, String userId, String sessionId, String message);

    List<String> handleMessage(ChatCommandEntity chatCommandEntity);

}

