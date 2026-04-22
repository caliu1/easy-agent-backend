package cn.caliu.agent.domain.agent.repository;

import cn.caliu.agent.domain.agent.model.valobj.AgentConfigManageVO;
import cn.caliu.agent.domain.agent.model.valobj.AgentConfigPageQueryVO;
import cn.caliu.agent.domain.agent.model.valobj.AgentConfigPageResultVO;

import java.util.List;

/**
 * Agent 当前配置仓储接口。
 */
public interface IAgentConfigRepository {

    boolean exists(String agentId);

    void insert(AgentConfigManageVO config);

    void update(AgentConfigManageVO config);

    boolean softDelete(String agentId, String operator);

    AgentConfigManageVO queryByAgentId(String agentId);

    List<AgentConfigManageVO> queryList();

    List<AgentConfigManageVO> queryPublishedList();

    List<AgentConfigManageVO> queryMyList(String userId);

    List<AgentConfigManageVO> queryPlazaList();

    AgentConfigPageResultVO queryPage(AgentConfigPageQueryVO queryVO);

}
