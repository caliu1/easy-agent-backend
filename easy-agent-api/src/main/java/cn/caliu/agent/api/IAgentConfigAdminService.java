package cn.caliu.agent.api;

import cn.caliu.agent.api.dto.AgentConfigDeleteRequestDTO;
import cn.caliu.agent.api.dto.AgentConfigDetailResponseDTO;
import cn.caliu.agent.api.dto.AgentConfigOfflineRequestDTO;
import cn.caliu.agent.api.dto.AgentConfigPageQueryRequestDTO;
import cn.caliu.agent.api.dto.AgentConfigPageResponseDTO;
import cn.caliu.agent.api.dto.AgentConfigPublishRequestDTO;
import cn.caliu.agent.api.dto.AgentConfigRollbackRequestDTO;
import cn.caliu.agent.api.dto.AgentConfigSubscribeRequestDTO;
import cn.caliu.agent.api.dto.AgentConfigSummaryResponseDTO;
import cn.caliu.agent.api.dto.AgentConfigUpsertRequestDTO;
import cn.caliu.agent.api.response.Response;

import java.util.List;

public interface IAgentConfigAdminService {

    Response<AgentConfigDetailResponseDTO> createAgentConfig(AgentConfigUpsertRequestDTO requestDTO);

    Response<AgentConfigDetailResponseDTO> updateAgentConfig(AgentConfigUpsertRequestDTO requestDTO);

    Response<Boolean> deleteAgentConfig(AgentConfigDeleteRequestDTO requestDTO);

    Response<AgentConfigDetailResponseDTO> queryAgentConfigDetail(String agentId);

    Response<List<AgentConfigSummaryResponseDTO>> queryAgentConfigList();

    Response<List<AgentConfigSummaryResponseDTO>> queryMyAgentConfigList(String userId);

    Response<List<AgentConfigSummaryResponseDTO>> queryAgentPlazaList();

    Response<List<AgentConfigSummaryResponseDTO>> queryMySubscribedAgentConfigList(String userId);

    Response<AgentConfigPageResponseDTO> queryAgentConfigPage(AgentConfigPageQueryRequestDTO requestDTO);

    Response<AgentConfigDetailResponseDTO> publishAgentConfig(AgentConfigPublishRequestDTO requestDTO);

    Response<AgentConfigDetailResponseDTO> offlineAgentConfig(AgentConfigOfflineRequestDTO requestDTO);

    Response<AgentConfigDetailResponseDTO> rollbackAgentConfig(AgentConfigRollbackRequestDTO requestDTO);

    Response<AgentConfigDetailResponseDTO> publishAgentToPlaza(AgentConfigPublishRequestDTO requestDTO);

    Response<AgentConfigDetailResponseDTO> unpublishAgentFromPlaza(AgentConfigOfflineRequestDTO requestDTO);

    Response<Boolean> subscribeAgentConfig(AgentConfigSubscribeRequestDTO requestDTO);

    Response<Boolean> unsubscribeAgentConfig(AgentConfigSubscribeRequestDTO requestDTO);

}
