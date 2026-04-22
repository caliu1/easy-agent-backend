package cn.caliu.agent.domain.agent.repository;

import java.util.List;

public interface IAgentSubscribeRepository {

    void subscribe(String userId, String agentId);

    boolean unsubscribe(String userId, String agentId);

    List<String> querySubscribedAgentIds(String userId);

}
