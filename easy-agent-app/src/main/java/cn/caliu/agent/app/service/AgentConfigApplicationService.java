package cn.caliu.agent.app.service;

import cn.caliu.agent.api.application.IAgentConfigApplicationService;
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
import cn.caliu.agent.domain.agent.model.entity.AgentConfigEntity;
import cn.caliu.agent.domain.agent.model.entity.AgentMcpProfileEntity;
import cn.caliu.agent.domain.agent.model.entity.AgentSkillProfileEntity;
import cn.caliu.agent.domain.agent.model.valobj.AgentConfigPageQueryVO;
import cn.caliu.agent.domain.agent.model.valobj.AgentConfigPageQueryResult;
import cn.caliu.agent.domain.agent.model.valobj.SkillAssetEntryVO;
import cn.caliu.agent.domain.agent.model.valobj.SkillAssetsResultVO;
import cn.caliu.agent.domain.agent.model.valobj.SkillImportResultVO;
import cn.caliu.agent.domain.agent.service.IAgentConfigManageService;
import cn.caliu.agent.domain.agent.service.IAgentToolProfileManageService;
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

/**
 * Agent 配置应用服务实现。
 *
 * 主要职责：
 * 1. DTO 与 Domain 对象转换。
 * 2. 管理台用例编排（发布、下线、回滚、广场、订阅）。
 * 3. 对查询结果做聚合与分页结果映射。
 */
@Service
public class AgentConfigApplicationService implements IAgentConfigApplicationService {
    private static final String SYSTEM_USER_ID = "system";

    @Resource
    private IAgentConfigManageService agentConfigManageService;
    @Resource
    private IAgentToolProfileManageService agentToolProfileManageService;
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

    @Override
    public AgentSkillImportResponseDTO importSkillZip(String operator, String fileName, byte[] zipBytes) {
        SkillImportResultVO importResult = agentConfigManageService.importSkillZip(operator, fileName, zipBytes);
        return toSkillImportResponse(importResult);
    }

    @Override
    public AgentSkillImportResponseDTO saveSkillAssets(AgentSkillSaveRequestDTO requestDTO) {
        List<SkillAssetEntryVO> entries = new ArrayList<>();
        if (requestDTO != null && requestDTO.getEntries() != null) {
            for (AgentSkillSaveRequestDTO.Entry entry : requestDTO.getEntries()) {
                if (entry == null) continue;
                entries.add(SkillAssetEntryVO.builder()
                        .kind(entry.getKind())
                        .path(entry.getPath())
                        .content(entry.getContent())
                        .build());
            }
        }

        SkillImportResultVO saveResult = agentConfigManageService.saveSkillAssets(
                requestDTO == null ? null : requestDTO.getOperator(),
                requestDTO == null ? null : requestDTO.getRootFolder(),
                entries
        );
        return toSkillImportResponse(saveResult);
    }

    @Override
    public AgentSkillAssetsResponseDTO querySkillAssets(String ossPath) {
        SkillAssetsResultVO resultVO = agentConfigManageService.querySkillAssets(ossPath);
        AgentSkillAssetsResponseDTO responseDTO = new AgentSkillAssetsResponseDTO();
        responseDTO.setBucket(resultVO.getBucket());
        responseDTO.setPrefix(resultVO.getPrefix());
        responseDTO.setFileCount(resultVO.getFileCount());
        responseDTO.setFolderCount(resultVO.getFolderCount());

        List<AgentSkillAssetsResponseDTO.Entry> entries = new ArrayList<>();
        if (resultVO.getEntries() != null) {
            for (SkillAssetEntryVO item : resultVO.getEntries()) {
                AgentSkillAssetsResponseDTO.Entry entry = new AgentSkillAssetsResponseDTO.Entry();
                entry.setKind(item.getKind());
                entry.setPath(item.getPath());
                entry.setContent(item.getContent());
                entries.add(entry);
            }
        }
        responseDTO.setEntries(entries);
        return responseDTO;
    }

    @Override
    public AgentMcpProfileResponseDTO createMcpProfile(AgentMcpProfileUpsertRequestDTO requestDTO) {
        AgentMcpProfileEntity created = agentToolProfileManageService.createMcpProfile(toMcpProfileEntity(requestDTO));
        return toMcpProfileResponse(created);
    }

    @Override
    public AgentMcpProfileResponseDTO updateMcpProfile(AgentMcpProfileUpsertRequestDTO requestDTO) {
        AgentMcpProfileEntity updated = agentToolProfileManageService.updateMcpProfile(toMcpProfileEntity(requestDTO));
        return toMcpProfileResponse(updated);
    }

    @Override
    public boolean deleteMcpProfile(AgentMcpProfileDeleteRequestDTO requestDTO) {
        return agentToolProfileManageService.deleteMcpProfile(
                requestDTO == null ? null : requestDTO.getId(),
                requestDTO == null ? null : requestDTO.getUserId()
        );
    }

    @Override
    public List<AgentMcpProfileResponseDTO> queryMcpProfileList(String userId) {
        String requesterUserId = StringUtils.trimToEmpty(userId);
        return agentToolProfileManageService.queryMcpProfileList(userId).stream()
                .map(this::toMcpProfileResponse)
                .map(item -> sanitizeSystemMcpProfile(item, requesterUserId))
                .collect(Collectors.toList());
    }

    @Override
    public boolean testMcpProfileConnection(AgentMcpProfileUpsertRequestDTO requestDTO) {
        return agentToolProfileManageService.testMcpProfileConnection(toMcpProfileEntity(requestDTO));
    }

    @Override
    public AgentSkillProfileResponseDTO createSkillProfile(AgentSkillProfileUpsertRequestDTO requestDTO) {
        AgentSkillProfileEntity created = agentToolProfileManageService.createSkillProfile(toSkillProfileEntity(requestDTO));
        return toSkillProfileResponse(created);
    }

    @Override
    public AgentSkillProfileResponseDTO updateSkillProfile(AgentSkillProfileUpsertRequestDTO requestDTO) {
        AgentSkillProfileEntity updated = agentToolProfileManageService.updateSkillProfile(toSkillProfileEntity(requestDTO));
        return toSkillProfileResponse(updated);
    }

    @Override
    public boolean deleteSkillProfile(AgentSkillProfileDeleteRequestDTO requestDTO) {
        return agentToolProfileManageService.deleteSkillProfile(
                requestDTO == null ? null : requestDTO.getId(),
                requestDTO == null ? null : requestDTO.getUserId()
        );
    }

    @Override
    public List<AgentSkillProfileResponseDTO> querySkillProfileList(String userId) {
        return agentToolProfileManageService.querySkillProfileList(userId).stream()
                .map(this::toSkillProfileResponse)
                .collect(Collectors.toList());
    }

    private AgentSkillImportResponseDTO toSkillImportResponse(SkillImportResultVO importResult) {
        AgentSkillImportResponseDTO responseDTO = new AgentSkillImportResponseDTO();
        responseDTO.setBucket(importResult.getBucket());
        responseDTO.setPrefix(importResult.getPrefix());
        responseDTO.setFileCount(importResult.getFileCount());
        responseDTO.setSkillCount(importResult.getSkillCount());

        List<AgentSkillImportResponseDTO.ToolSkillItemDTO> toolSkills = new ArrayList<>();
        if (importResult.getToolSkillsList() != null) {
            for (SkillImportResultVO.ToolSkillLocationVO source : importResult.getToolSkillsList()) {
                AgentSkillImportResponseDTO.ToolSkillItemDTO itemDTO = new AgentSkillImportResponseDTO.ToolSkillItemDTO();
                itemDTO.setType(source.getType());
                itemDTO.setPath(source.getPath());
                itemDTO.setSkillName(source.getSkillName());
                toolSkills.add(itemDTO);
            }
        }
        responseDTO.setToolSkillsList(toolSkills);
        return responseDTO;
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

    private AgentMcpProfileEntity toMcpProfileEntity(AgentMcpProfileUpsertRequestDTO requestDTO) {
        if (requestDTO == null) {
            return AgentMcpProfileEntity.builder().build();
        }
        return AgentMcpProfileEntity.builder()
                .id(requestDTO.getId())
                .userId(requestDTO.getUserId())
                .configJson(requestDTO.getConfigJson())
                .type(requestDTO.getType())
                .name(requestDTO.getName())
                .description(requestDTO.getDescription())
                .baseUri(requestDTO.getBaseUri())
                .sseEndpoint(requestDTO.getSseEndpoint())
                .requestTimeout(requestDTO.getRequestTimeout())
                .authType(requestDTO.getAuthType())
                .authToken(requestDTO.getAuthToken())
                .authKeyName(requestDTO.getAuthKeyName())
                .headersJson(requestDTO.getHeadersJson())
                .queryJson(requestDTO.getQueryJson())
                .build();
    }

    private AgentMcpProfileResponseDTO toMcpProfileResponse(AgentMcpProfileEntity source) {
        AgentMcpProfileResponseDTO dto = new AgentMcpProfileResponseDTO();
        dto.setId(source.getId());
        dto.setUserId(source.getUserId());
        dto.setType(source.getType());
        dto.setName(source.getName());
        dto.setDescription(source.getDescription());
        dto.setBaseUri(source.getBaseUri());
        dto.setConfigJson(source.getConfigJson());
        dto.setSseEndpoint(source.getSseEndpoint());
        dto.setRequestTimeout(source.getRequestTimeout());
        dto.setAuthType(source.getAuthType());
        dto.setAuthToken(source.getAuthToken());
        dto.setAuthKeyName(source.getAuthKeyName());
        dto.setHeadersJson(source.getHeadersJson());
        dto.setQueryJson(source.getQueryJson());
        dto.setCreateTime(source.getCreateTime());
        dto.setUpdateTime(source.getUpdateTime());
        return dto;
    }

    private AgentMcpProfileResponseDTO sanitizeSystemMcpProfile(AgentMcpProfileResponseDTO source, String requesterUserId) {
        if (source == null) {
            return null;
        }

        // system 自己查询时保留完整字段，便于运维管理。
        if (isSystemUser(requesterUserId)) {
            return source;
        }

        // 非 system 用户查询 system 配置时，只返回名称和描述，避免泄露敏感连接信息。
        if (isSystemUser(source.getUserId())) {
            AgentMcpProfileResponseDTO masked = new AgentMcpProfileResponseDTO();
            masked.setName(source.getName());
            masked.setDescription(source.getDescription());
            return masked;
        }
        return source;
    }

    private boolean isSystemUser(String userId) {
        return SYSTEM_USER_ID.equalsIgnoreCase(StringUtils.trimToEmpty(userId));
    }

    private AgentSkillProfileEntity toSkillProfileEntity(AgentSkillProfileUpsertRequestDTO requestDTO) {
        if (requestDTO == null) {
            return AgentSkillProfileEntity.builder().build();
        }
        return AgentSkillProfileEntity.builder()
                .id(requestDTO.getId())
                .userId(requestDTO.getUserId())
                .skillName(requestDTO.getSkillName())
                .ossPath(requestDTO.getOssPath())
                .build();
    }

    private AgentSkillProfileResponseDTO toSkillProfileResponse(AgentSkillProfileEntity source) {
        AgentSkillProfileResponseDTO dto = new AgentSkillProfileResponseDTO();
        dto.setId(source.getId());
        dto.setUserId(source.getUserId());
        dto.setSkillName(source.getSkillName());
        dto.setOssPath(source.getOssPath());
        dto.setCreateTime(source.getCreateTime());
        dto.setUpdateTime(source.getUpdateTime());
        return dto;
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


