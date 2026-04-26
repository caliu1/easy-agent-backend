package cn.caliu.agent.domain.session.service;

public interface ISessionService {

    String createSession(String agentId, String userId);

    boolean deleteSession(String sessionId, String userId);

}
