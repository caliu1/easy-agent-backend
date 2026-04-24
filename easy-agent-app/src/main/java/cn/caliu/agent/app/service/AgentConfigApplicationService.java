package cn.caliu.agent.app.service;

import cn.caliu.agent.api.application.IAgentConfigApplicationService;
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
import cn.caliu.agent.domain.agent.model.entity.AgentConfigEntity;
import cn.caliu.agent.domain.agent.model.valobj.AgentConfigPageQueryVO;
import cn.caliu.agent.domain.agent.model.valobj.AgentConfigPageQueryResult;
import cn.caliu.agent.domain.agent.service.IAgentConfigManageService;
import cn.caliu.agent.domain.user.service.IUserSubscriptionService;
import cn.caliu.agent.types.enums.ResponseCode;
import cn.caliu.agent.types.exception.AppException;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
public class AgentConfigApplicationService implements IAgentConfigApplicationService {

    @Resource
    private IAgentConfigManageService agentConfigManageService;
    @Resource
    private IUserSubscriptionService userSubscriptionService;

    @Override
    public AgentConfigDetailResponseDTO createAgentConfig(AgentConfigUpsertRequestDTO requestDTO) {
        AgentConfigEntity created = agentConfigManageService.createAgentConfig(toConfigEntity(requestDTO));
        return toDetailResponse(created);
    }

    @Override
    public AgentConfigDetailResponseDTO updateAgentConfig(AgentConfigUpsertRequestDTO requestDTO) {
        AgentConfigEntity updated = agentConfigManageService.updateAgentConfig(toConfigEntity(requestDTO));
        return toDetailResponse(updated);
    }

    @Override
    public boolean deleteAgentConfig(AgentConfigDeleteRequestDTO requestDTO) {
        return agentConfigManageService.deleteAgentConfig(requestDTO.getAgentId(), requestDTO.getOperator());
    }

    @Override
    public AgentConfigDetailResponseDTO queryAgentConfigDetail(String agentId) {
        AgentConfigEntity detail = agentConfigManageService.queryAgentConfigDetail(agentId);
        return toDetailResponse(detail);
    }

    @Override
    public List<AgentConfigSummaryResponseDTO> queryAgentPlazaList() {
        return agentConfigManageService.queryAgentPlazaList().stream()
                .map(this::toSummaryResponse)
                .collect(Collectors.toList());
    }

    @Override
    public List<AgentConfigSummaryResponseDTO> queryMySubscribedAgentConfigList(String userId) {
        if (StringUtils.isBlank(userId)) {
            return Collections.emptyList();
        }

        List<String> subscribedAgentIds = userSubscriptionService.querySubscribedAgentIds(userId);
        if (subscribedAgentIds == null || subscribedAgentIds.isEmpty()) {
            return Collections.emptyList();
        }

        Map<String, AgentConfigSummaryResponseDTO> plazaAgentMap = agentConfigManageService.queryAgentPlazaList().stream()
                .map(this::toSummaryResponse)
                .collect(Collectors.toMap(
                        AgentConfigSummaryResponseDTO::getAgentId,
                        item -> item,
                        (left, right) -> right,
                        LinkedHashMap::new
                ));

        List<AgentConfigSummaryResponseDTO> subscribedAgents = new ArrayList<>();
        for (String subscribedAgentId : subscribedAgentIds) {
            AgentConfigSummaryResponseDTO subscribed = plazaAgentMap.get(subscribedAgentId);
            if (subscribed != null) {
                subscribedAgents.add(subscribed);
            }
        }
        return subscribedAgents;
    }

    @Override
    public AgentConfigPageResponseDTO queryAgentConfigPage(AgentConfigPageQueryRequestDTO requestDTO) {
        AgentConfigPageQueryResult pageResult = agentConfigManageService.queryAgentConfigPage(toPageQueryVO(requestDTO));
        AgentConfigPageResponseDTO responseDTO = new AgentConfigPageResponseDTO();
        responseDTO.setPageNo(pageResult.getPageNo());
        responseDTO.setPageSize(pageResult.getPageSize());
        responseDTO.setTotal(pageResult.getTotal());
        responseDTO.setRecords(pageResult.getRecords() == null
                ? Collections.emptyList()
                : pageResult.getRecords().stream().map(this::toSummaryResponse).collect(Collectors.toList()));
        return responseDTO;
    }

    @Override
    public AgentConfigDetailResponseDTO publishAgentConfig(AgentConfigPublishRequestDTO requestDTO) {
        AgentConfigEntity published = agentConfigManageService.publishAgentConfig(requestDTO.getAgentId(), requestDTO.getOperator());
        return toDetailResponse(published);
    }

    @Override
    public AgentConfigDetailResponseDTO offlineAgentConfig(AgentConfigOfflineRequestDTO requestDTO) {
        AgentConfigEntity offline = agentConfigManageService.offlineAgentConfig(requestDTO.getAgentId(), requestDTO.getOperator());
        return toDetailResponse(offline);
    }

    @Override
    public AgentConfigDetailResponseDTO rollbackAgentConfig(AgentConfigRollbackRequestDTO requestDTO) {
        AgentConfigEntity rollback = agentConfigManageService.rollbackAgentConfig(
                requestDTO.getAgentId(),
                requestDTO.getTargetVersion(),
                requestDTO.getOperator()
        );
        return toDetailResponse(rollback);
    }

    @Override
    public AgentConfigDetailResponseDTO publishAgentToPlaza(AgentConfigPublishRequestDTO requestDTO) {
        AgentConfigEntity updated = agentConfigManageService.publishAgentToPlaza(requestDTO.getAgentId(), requestDTO.getOperator());
        return toDetailResponse(updated);
    }

    @Override
    public AgentConfigDetailResponseDTO unpublishAgentFromPlaza(AgentConfigOfflineRequestDTO requestDTO) {
        AgentConfigEntity updated = agentConfigManageService.unpublishAgentFromPlaza(requestDTO.getAgentId(), requestDTO.getOperator());
        return toDetailResponse(updated);
    }

    @Override
    public boolean subscribeAgentConfig(AgentConfigSubscribeRequestDTO requestDTO) {
        if (!existsInPlaza(requestDTO.getAgentId())) {
            throw new AppException(ResponseCode.ILLEGAL_PARAMETER.getCode(), "agent is not available in plaza");
        }
        return userSubscriptionService.subscribeAgent(requestDTO.getUserId(), requestDTO.getAgentId());
    }

    @Override
    public boolean unsubscribeAgentConfig(AgentConfigSubscribeRequestDTO requestDTO) {
        return userSubscriptionService.unsubscribeAgent(requestDTO.getUserId(), requestDTO.getAgentId());
    }

    private boolean existsInPlaza(String agentId) {
        if (StringUtils.isBlank(agentId)) {
            return false;
        }
        String targetAgentId = agentId.trim();
        return agentConfigManageService.queryAgentPlazaList().stream()
                .anyMatch(item -> targetAgentId.equals(item.getAgentId()));
    }

    private AgentConfigPageQueryVO toPageQueryVO(AgentConfigPageQueryRequestDTO requestDTO) {
        if (requestDTO == null) {
            return AgentConfigPageQueryVO.builder().build();
        }
        return AgentConfigPageQueryVO.builder()
                .agentId(requestDTO.getAgentId())
                .appName(requestDTO.getAppName())
                .agentName(requestDTO.getAgentName())
                .status(requestDTO.getStatus())
                .operator(requestDTO.getOperator())
                .ownerUserId(requestDTO.getOwnerUserId())
                .sourceType(requestDTO.getSourceType())
                .plazaStatus(requestDTO.getPlazaStatus())
                .pageNo(requestDTO.getPageNo())
                .pageSize(requestDTO.getPageSize())
                .build();
    }

    private AgentConfigEntity toConfigEntity(AgentConfigUpsertRequestDTO requestDTO) {
        return AgentConfigEntity.builder()
                .agentId(requestDTO.getAgentId())
                .appName(requestDTO.getAppName())
                .agentName(requestDTO.getAgentName())
                .agentDesc(requestDTO.getAgentDesc())
                .configJson(requestDTO.getConfigJson())
                .operator(requestDTO.getOperator())
                .ownerUserId(requestDTO.getOwnerUserId())
                .sourceType(requestDTO.getSourceType())
                .plazaStatus(requestDTO.getPlazaStatus())
                .build();
    }

    private AgentConfigDetailResponseDTO toDetailResponse(AgentConfigEntity source) {
        AgentConfigDetailResponseDTO dto = new AgentConfigDetailResponseDTO();
        dto.setAgentId(source.getAgentId());
        dto.setAppName(source.getAppName());
        dto.setAgentName(source.getAgentName());
        dto.setAgentDesc(source.getAgentDesc());
        dto.setConfigJson(source.getConfigJson());
        dto.setStatus(source.getStatus());
        dto.setCurrentVersion(source.getCurrentVersion());
        dto.setPublishedVersion(source.getPublishedVersion());
        dto.setOperator(source.getOperator());
        dto.setOwnerUserId(source.getOwnerUserId());
        dto.setSourceType(source.getSourceType());
        dto.setPlazaStatus(source.getPlazaStatus());
        dto.setPlazaPublishTime(source.getPlazaPublishTime());
        dto.setCreateTime(source.getCreateTime());
        dto.setUpdateTime(source.getUpdateTime());
        return dto;
    }

    private AgentConfigSummaryResponseDTO toSummaryResponse(AgentConfigEntity source) {
        AgentConfigSummaryResponseDTO dto = new AgentConfigSummaryResponseDTO();
        dto.setAgentId(source.getAgentId());
        dto.setAppName(source.getAppName());
        dto.setAgentName(source.getAgentName());
        dto.setAgentDesc(source.getAgentDesc());
        dto.setStatus(source.getStatus());
        dto.setCurrentVersion(source.getCurrentVersion());
        dto.setPublishedVersion(source.getPublishedVersion());
        dto.setOwnerUserId(source.getOwnerUserId());
        dto.setSourceType(source.getSourceType());
        dto.setPlazaStatus(source.getPlazaStatus());
        dto.setPlazaPublishTime(source.getPlazaPublishTime());
        dto.setUpdateTime(source.getUpdateTime());
        return dto;
    }

}


