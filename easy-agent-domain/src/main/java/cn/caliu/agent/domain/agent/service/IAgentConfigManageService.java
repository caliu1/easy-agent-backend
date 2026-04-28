package cn.caliu.agent.domain.agent.service;

import cn.caliu.agent.domain.agent.model.entity.AgentConfigEntity;
import cn.caliu.agent.domain.agent.model.valobj.AgentConfigPageQueryResult;
import cn.caliu.agent.domain.agent.model.valobj.AgentConfigPageQueryVO;

import java.util.List;

/**
 * Agent 配置领域服务接口。
 *
 * 覆盖能力：
 * 1. 配置生命周期：创建、更新、删除、详情。
 * 2. 版本与状态：发布、下线、回滚。
 * 3. 广场运营：发布到广场、取消发布、广场列表。
 * 4. 运行时：从已发布配置重建内存运行时注册表。
 */
public interface IAgentConfigManageService {

    AgentConfigEntity createAgentConfig(AgentConfigEntity request);

    AgentConfigEntity updateAgentConfig(AgentConfigEntity request);

    boolean deleteAgentConfig(String agentId, String operator);

    AgentConfigEntity queryAgentConfigDetail(String agentId);

    List<AgentConfigEntity> queryAgentPlazaList();

    AgentConfigPageQueryResult queryAgentConfigPage(AgentConfigPageQueryVO queryVO);

    AgentConfigEntity publishAgentConfig(String agentId, String operator);

    AgentConfigEntity offlineAgentConfig(String agentId, String operator);

    AgentConfigEntity rollbackAgentConfig(String agentId, Long targetVersion, String operator);

    AgentConfigEntity publishAgentToPlaza(String agentId, String operator);

    AgentConfigEntity unpublishAgentFromPlaza(String agentId, String operator);

    /**
     * 从已发布配置批量重建运行时注册表。
     *
     * @return 成功重载并激活的 Agent 数量
     */
    int reloadPublishedAgentRuntime();

}

