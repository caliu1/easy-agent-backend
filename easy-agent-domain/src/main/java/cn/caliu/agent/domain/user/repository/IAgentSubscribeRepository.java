package cn.caliu.agent.domain.user.repository;

import java.util.List;
/**
 * IAgentSubscribeRepository 领域仓储接口。
 */

public interface IAgentSubscribeRepository {

    void subscribe(String userId, String agentId);

    boolean unsubscribe(String userId, String agentId);

    List<String> querySubscribedAgentIds(String userId);

}
