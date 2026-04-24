package cn.caliu.agent.domain.agent.service;

import cn.caliu.agent.domain.agent.model.entity.AgentConfigEntity;
import cn.caliu.agent.domain.agent.model.valobj.AgentConfigPageQueryVO;
import cn.caliu.agent.domain.agent.model.valobj.AgentConfigPageQueryResult;

import java.util.List;

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
     * 鍚姩鎴栨墜鍔ㄨЕ鍙戯細浠庢暟鎹簱宸插彂甯冮厤缃噸寤鸿繍琛屾椂娉ㄥ唽琛ㄣ€?     *
     * @return 鎴愬姛瑁呴厤骞舵縺娲荤殑 Agent 鏁伴噺
     */
    int reloadPublishedAgentRuntime();

}

