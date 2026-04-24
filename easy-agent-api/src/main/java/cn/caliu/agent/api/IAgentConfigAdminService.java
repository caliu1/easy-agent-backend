package cn.caliu.agent.api;

import cn.caliu.agent.api.dto.agent.config.request.AgentConfigDeleteRequestDTO;
import cn.caliu.agent.api.dto.agent.config.response.AgentConfigDetailResponseDTO;
import cn.caliu.agent.api.dto.agent.config.request.AgentConfigOfflineRequestDTO;
import cn.caliu.agent.api.dto.agent.config.request.AgentConfigPageQueryRequestDTO;
import cn.caliu.agent.api.dto.agent.config.response.AgentConfigPageResponseDTO;
import cn.caliu.agent.api.dto.agent.config.request.AgentConfigPublishRequestDTO;
import cn.caliu.agent.api.dto.agent.config.request.AgentConfigRollbackRequestDTO;
import cn.caliu.agent.api.dto.agent.config.request.AgentConfigSubscribeRequestDTO;
import cn.caliu.agent.api.dto.agent.config.response.AgentConfigSummaryResponseDTO;
import cn.caliu.agent.api.dto.agent.config.request.AgentConfigUpsertRequestDTO;
import cn.caliu.agent.api.response.Response;

import java.util.List;

public interface IAgentConfigAdminService {

    Response<AgentConfigDetailResponseDTO> createAgentConfig(AgentConfigUpsertRequestDTO requestDTO);

    Response<AgentConfigDetailResponseDTO> updateAgentConfig(AgentConfigUpsertRequestDTO requestDTO);

    Response<Boolean> deleteAgentConfig(AgentConfigDeleteRequestDTO requestDTO);

    Response<AgentConfigDetailResponseDTO> queryAgentConfigDetail(String agentId);

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

