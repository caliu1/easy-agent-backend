package cn.caliu.agent.domain.agent.service;

import cn.caliu.agent.domain.agent.model.valobj.AgentConfigManageVO;
import cn.caliu.agent.domain.agent.model.valobj.AgentConfigPageQueryVO;
import cn.caliu.agent.domain.agent.model.valobj.AgentConfigPageResultVO;

import java.util.List;

public interface IAgentConfigManageService {

    AgentConfigManageVO createAgentConfig(AgentConfigManageVO request);

    AgentConfigManageVO updateAgentConfig(AgentConfigManageVO request);

    boolean deleteAgentConfig(String agentId, String operator);

    AgentConfigManageVO queryAgentConfigDetail(String agentId);

    List<AgentConfigManageVO> queryAgentConfigList();

    List<AgentConfigManageVO> queryMyAgentConfigList(String userId);

    List<AgentConfigManageVO> queryAgentPlazaList();

    List<AgentConfigManageVO> queryMySubscribedAgentList(String userId);

    List<AgentConfigManageVO> queryPublishedAgentConfigList();

    AgentConfigPageResultVO queryAgentConfigPage(AgentConfigPageQueryVO queryVO);

    AgentConfigManageVO publishAgentConfig(String agentId, String operator);

    AgentConfigManageVO offlineAgentConfig(String agentId, String operator);

    AgentConfigManageVO rollbackAgentConfig(String agentId, Long targetVersion, String operator);

    AgentConfigManageVO publishAgentToPlaza(String agentId, String operator);

    AgentConfigManageVO unpublishAgentFromPlaza(String agentId, String operator);

    boolean subscribeAgent(String userId, String agentId);

    boolean unsubscribeAgent(String userId, String agentId);

    /**
     * 启动或手动触发：从数据库已发布配置重建运行时注册表。
     *
     * @return 成功装配并激活的 Agent 数量
     */
    int reloadPublishedAgentRuntime();

}
