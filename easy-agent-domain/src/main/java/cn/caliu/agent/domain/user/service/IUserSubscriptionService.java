package cn.caliu.agent.domain.user.service;

import java.util.List;

public interface IUserSubscriptionService {

    List<String> querySubscribedAgentIds(String userId);

    boolean subscribeAgent(String userId, String agentId);

    boolean unsubscribeAgent(String userId, String agentId);

}

