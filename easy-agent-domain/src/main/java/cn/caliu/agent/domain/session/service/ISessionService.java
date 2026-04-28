package cn.caliu.agent.domain.session.service;

/**
 * 会话绑定领域服务接口。
 *
 * 负责会话创建、删除，以及会话与 Agent 版本绑定关系维护。
 */
public interface ISessionService {

    String createSession(String agentId, String userId);

    boolean deleteSession(String sessionId, String userId);

}

