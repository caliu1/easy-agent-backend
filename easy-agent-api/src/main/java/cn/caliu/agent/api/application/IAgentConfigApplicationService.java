package cn.caliu.agent.api.application;

import cn.caliu.agent.api.dto.agent.config.request.AgentConfigDeleteRequestDTO;
import cn.caliu.agent.api.dto.agent.config.response.AgentConfigDetailResponseDTO;
import cn.caliu.agent.api.dto.agent.config.request.AgentConfigOfflineRequestDTO;
import cn.caliu.agent.api.dto.agent.config.request.AgentConfigPageQueryRequestDTO;
import cn.caliu.agent.api.dto.agent.config.response.AgentConfigPageResponseDTO;
import cn.caliu.agent.api.dto.agent.config.request.AgentConfigPublishRequestDTO;
import cn.caliu.agent.api.dto.agent.config.request.AgentConfigRollbackRequestDTO;
import cn.caliu.agent.api.dto.agent.config.request.AgentMcpProfileDeleteRequestDTO;
import cn.caliu.agent.api.dto.agent.config.request.AgentMcpProfileUpsertRequestDTO;
import cn.caliu.agent.api.dto.agent.config.request.AgentSkillSaveRequestDTO;
import cn.caliu.agent.api.dto.agent.config.request.AgentSkillProfileDeleteRequestDTO;
import cn.caliu.agent.api.dto.agent.config.request.AgentSkillProfileUpsertRequestDTO;
import cn.caliu.agent.api.dto.agent.config.request.AgentConfigSubscribeRequestDTO;
import cn.caliu.agent.api.dto.agent.config.response.AgentMcpProfileResponseDTO;
import cn.caliu.agent.api.dto.agent.config.response.AgentSkillAssetsResponseDTO;
import cn.caliu.agent.api.dto.agent.config.response.AgentSkillImportResponseDTO;
import cn.caliu.agent.api.dto.agent.config.response.AgentSkillProfileResponseDTO;
import cn.caliu.agent.api.dto.agent.config.response.AgentConfigSummaryResponseDTO;
import cn.caliu.agent.api.dto.agent.config.request.AgentConfigUpsertRequestDTO;

import java.util.List;

/**
 * Agent 配置应用服务接口。
 *
 * 面向 Trigger 提供配置用例编排入口，
 * 内部调用 Domain 服务完成业务校验与状态流转。
 */
public interface IAgentConfigApplicationService {

    AgentConfigDetailResponseDTO createAgentConfig(AgentConfigUpsertRequestDTO requestDTO);

    AgentConfigDetailResponseDTO updateAgentConfig(AgentConfigUpsertRequestDTO requestDTO);

    boolean deleteAgentConfig(AgentConfigDeleteRequestDTO requestDTO);

    AgentConfigDetailResponseDTO queryAgentConfigDetail(String agentId);

    List<AgentConfigSummaryResponseDTO> queryAgentPlazaList();

    List<AgentConfigSummaryResponseDTO> queryMySubscribedAgentConfigList(String userId);

    AgentConfigPageResponseDTO queryAgentConfigPage(AgentConfigPageQueryRequestDTO requestDTO);

    AgentConfigDetailResponseDTO publishAgentConfig(AgentConfigPublishRequestDTO requestDTO);

    AgentConfigDetailResponseDTO offlineAgentConfig(AgentConfigOfflineRequestDTO requestDTO);

    AgentConfigDetailResponseDTO rollbackAgentConfig(AgentConfigRollbackRequestDTO requestDTO);

    AgentConfigDetailResponseDTO publishAgentToPlaza(AgentConfigPublishRequestDTO requestDTO);

    AgentConfigDetailResponseDTO unpublishAgentFromPlaza(AgentConfigOfflineRequestDTO requestDTO);

    boolean subscribeAgentConfig(AgentConfigSubscribeRequestDTO requestDTO);

    boolean unsubscribeAgentConfig(AgentConfigSubscribeRequestDTO requestDTO);

    AgentSkillImportResponseDTO importSkillZip(String operator, String fileName, byte[] zipBytes);

    AgentSkillImportResponseDTO saveSkillAssets(AgentSkillSaveRequestDTO requestDTO);

    AgentSkillAssetsResponseDTO querySkillAssets(String ossPath);

    AgentMcpProfileResponseDTO createMcpProfile(AgentMcpProfileUpsertRequestDTO requestDTO);

    AgentMcpProfileResponseDTO updateMcpProfile(AgentMcpProfileUpsertRequestDTO requestDTO);

    boolean deleteMcpProfile(AgentMcpProfileDeleteRequestDTO requestDTO);

    List<AgentMcpProfileResponseDTO> queryMcpProfileList(String userId);

    boolean testMcpProfileConnection(AgentMcpProfileUpsertRequestDTO requestDTO);

    AgentSkillProfileResponseDTO createSkillProfile(AgentSkillProfileUpsertRequestDTO requestDTO);

    AgentSkillProfileResponseDTO updateSkillProfile(AgentSkillProfileUpsertRequestDTO requestDTO);

    boolean deleteSkillProfile(AgentSkillProfileDeleteRequestDTO requestDTO);

    List<AgentSkillProfileResponseDTO> querySkillProfileList(String userId);

}

