package cn.caliu.agent.domain.user.service;

import java.util.List;

/**
 * 用户订阅领域服务接口。
 *
 * 负责用户与 Agent 的订阅关系查询与维护。
 */
public interface IUserSubscriptionService {

    List<String> querySubscribedAgentIds(String userId);

    boolean subscribeAgent(String userId, String agentId);

    boolean unsubscribeAgent(String userId, String agentId);

}

