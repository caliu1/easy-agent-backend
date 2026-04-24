package cn.caliu.agent.domain.agent.repository;

import cn.caliu.agent.domain.agent.model.entity.AgentConfigEntity;
import cn.caliu.agent.domain.agent.model.valobj.AgentConfigPageQueryVO;
import cn.caliu.agent.domain.agent.model.valobj.AgentConfigPageQueryResult;

import java.util.List;

/**
 * Agent 褰撳墠閰嶇疆浠撳偍鎺ュ彛銆? */
public interface IAgentConfigRepository {

    boolean exists(String agentId);

    void insert(AgentConfigEntity config);

    void update(AgentConfigEntity config);

    boolean softDelete(String agentId, String operator);

    AgentConfigEntity queryByAgentId(String agentId);

    List<AgentConfigEntity> queryPublishedList();

    List<AgentConfigEntity> queryPlazaList();

    AgentConfigPageQueryResult queryPage(AgentConfigPageQueryVO queryVO);

}

