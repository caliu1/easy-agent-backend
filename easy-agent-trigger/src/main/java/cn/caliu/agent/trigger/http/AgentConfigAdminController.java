package cn.caliu.agent.trigger.http;

import cn.caliu.agent.api.IAgentConfigAdminService;
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
import cn.caliu.agent.domain.agent.model.valobj.AgentConfigManageVO;
import cn.caliu.agent.domain.agent.model.valobj.AgentConfigPageQueryVO;
import cn.caliu.agent.domain.agent.model.valobj.AgentConfigPageResultVO;
import cn.caliu.agent.domain.agent.service.IAgentConfigManageService;
import cn.caliu.agent.types.enums.ResponseCode;
import cn.caliu.agent.types.exception.AppException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import javax.annotation.Resource;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

@Slf4j
@RestController
@RequestMapping("/api/v1/")
@CrossOrigin("*")
public class AgentConfigAdminController implements IAgentConfigAdminService {

    @Resource
    private IAgentConfigManageService agentConfigManageService;

    @RequestMapping(value = "agent_config_create", method = RequestMethod.POST)
    @Override
    public Response<AgentConfigDetailResponseDTO> createAgentConfig(@RequestBody AgentConfigUpsertRequestDTO requestDTO) {
        try {
            AgentConfigManageVO created = agentConfigManageService.createAgentConfig(toManageVO(requestDTO));
            return success(toDetailResponse(created));
        } catch (AppException e) {
            log.error("create agent config failed", e);
            return fail(e.getCode(), e.getInfo());
        } catch (Exception e) {
            log.error("create agent config failed", e);
            return fail(ResponseCode.UN_ERROR.getCode(), ResponseCode.UN_ERROR.getInfo());
        }
    }

    @RequestMapping(value = "agent_config_update", method = RequestMethod.POST)
    @Override
    public Response<AgentConfigDetailResponseDTO> updateAgentConfig(@RequestBody AgentConfigUpsertRequestDTO requestDTO) {
        try {
            AgentConfigManageVO updated = agentConfigManageService.updateAgentConfig(toManageVO(requestDTO));
            return success(toDetailResponse(updated));
        } catch (AppException e) {
            log.error("update agent config failed", e);
            return fail(e.getCode(), e.getInfo());
        } catch (Exception e) {
            log.error("update agent config failed", e);
            return fail(ResponseCode.UN_ERROR.getCode(), ResponseCode.UN_ERROR.getInfo());
        }
    }

    @RequestMapping(value = "agent_config_delete", method = RequestMethod.POST)
    @Override
    public Response<Boolean> deleteAgentConfig(@RequestBody AgentConfigDeleteRequestDTO requestDTO) {
        try {
            boolean deleted = agentConfigManageService.deleteAgentConfig(requestDTO.getAgentId(), requestDTO.getOperator());
            return Response.<Boolean>builder()
                    .code(ResponseCode.SUCCESS.getCode())
                    .info(ResponseCode.SUCCESS.getInfo())
                    .data(deleted)
                    .build();
        } catch (AppException e) {
            log.error("delete agent config failed", e);
            return Response.<Boolean>builder()
                    .code(e.getCode())
                    .info(e.getInfo())
                    .data(false)
                    .build();
        } catch (Exception e) {
            log.error("delete agent config failed", e);
            return Response.<Boolean>builder()
                    .code(ResponseCode.UN_ERROR.getCode())
                    .info(ResponseCode.UN_ERROR.getInfo())
                    .data(false)
                    .build();
        }
    }

    @RequestMapping(value = "agent_config_detail", method = RequestMethod.GET)
    @Override
    public Response<AgentConfigDetailResponseDTO> queryAgentConfigDetail(@RequestParam("agentId") String agentId) {
        try {
            AgentConfigManageVO detail = agentConfigManageService.queryAgentConfigDetail(agentId);
            return success(toDetailResponse(detail));
        } catch (AppException e) {
            log.error("query agent config detail failed", e);
            return fail(e.getCode(), e.getInfo());
        } catch (Exception e) {
            log.error("query agent config detail failed", e);
            return fail(ResponseCode.UN_ERROR.getCode(), ResponseCode.UN_ERROR.getInfo());
        }
    }

    @RequestMapping(value = "agent_config_list", method = RequestMethod.GET)
    @Override
    public Response<List<AgentConfigSummaryResponseDTO>> queryAgentConfigList() {
        try {
            List<AgentConfigSummaryResponseDTO> list = agentConfigManageService.queryAgentConfigList().stream()
                    .map(this::toSummaryResponse)
                    .collect(Collectors.toList());
            return Response.<List<AgentConfigSummaryResponseDTO>>builder()
                    .code(ResponseCode.SUCCESS.getCode())
                    .info(ResponseCode.SUCCESS.getInfo())
                    .data(list)
                    .build();
        } catch (AppException e) {
            log.error("query agent config list failed", e);
            return Response.<List<AgentConfigSummaryResponseDTO>>builder()
                    .code(e.getCode())
                    .info(e.getInfo())
                    .build();
        } catch (Exception e) {
            log.error("query agent config list failed", e);
            return Response.<List<AgentConfigSummaryResponseDTO>>builder()
                    .code(ResponseCode.UN_ERROR.getCode())
                    .info(ResponseCode.UN_ERROR.getInfo())
                    .build();
        }
    }

    @RequestMapping(value = "agent_config_my_list", method = RequestMethod.GET)
    @Override
    public Response<List<AgentConfigSummaryResponseDTO>> queryMyAgentConfigList(@RequestParam("userId") String userId) {
        try {
            List<AgentConfigSummaryResponseDTO> list = agentConfigManageService.queryMyAgentConfigList(userId).stream()
                    .map(this::toSummaryResponse)
                    .collect(Collectors.toList());
            return Response.<List<AgentConfigSummaryResponseDTO>>builder()
                    .code(ResponseCode.SUCCESS.getCode())
                    .info(ResponseCode.SUCCESS.getInfo())
                    .data(list)
                    .build();
        } catch (AppException e) {
            log.error("query my agent config list failed", e);
            return Response.<List<AgentConfigSummaryResponseDTO>>builder()
                    .code(e.getCode())
                    .info(e.getInfo())
                    .build();
        } catch (Exception e) {
            log.error("query my agent config list failed", e);
            return Response.<List<AgentConfigSummaryResponseDTO>>builder()
                    .code(ResponseCode.UN_ERROR.getCode())
                    .info(ResponseCode.UN_ERROR.getInfo())
                    .build();
        }
    }

    @RequestMapping(value = "agent_config_plaza_list", method = RequestMethod.GET)
    @Override
    public Response<List<AgentConfigSummaryResponseDTO>> queryAgentPlazaList() {
        try {
            List<AgentConfigSummaryResponseDTO> list = agentConfigManageService.queryAgentPlazaList().stream()
                    .map(this::toSummaryResponse)
                    .collect(Collectors.toList());
            return Response.<List<AgentConfigSummaryResponseDTO>>builder()
                    .code(ResponseCode.SUCCESS.getCode())
                    .info(ResponseCode.SUCCESS.getInfo())
                    .data(list)
                    .build();
        } catch (AppException e) {
            log.error("query agent plaza list failed", e);
            return Response.<List<AgentConfigSummaryResponseDTO>>builder()
                    .code(e.getCode())
                    .info(e.getInfo())
                    .build();
        } catch (Exception e) {
            log.error("query agent plaza list failed", e);
            return Response.<List<AgentConfigSummaryResponseDTO>>builder()
                    .code(ResponseCode.UN_ERROR.getCode())
                    .info(ResponseCode.UN_ERROR.getInfo())
                    .build();
        }
    }

    @RequestMapping(value = "agent_config_my_subscribe_list", method = RequestMethod.GET)
    @Override
    public Response<List<AgentConfigSummaryResponseDTO>> queryMySubscribedAgentConfigList(@RequestParam("userId") String userId) {
        try {
            List<AgentConfigSummaryResponseDTO> list = agentConfigManageService.queryMySubscribedAgentList(userId).stream()
                    .map(this::toSummaryResponse)
                    .collect(Collectors.toList());
            return Response.<List<AgentConfigSummaryResponseDTO>>builder()
                    .code(ResponseCode.SUCCESS.getCode())
                    .info(ResponseCode.SUCCESS.getInfo())
                    .data(list)
                    .build();
        } catch (AppException e) {
            log.error("query my subscribed agent list failed", e);
            return Response.<List<AgentConfigSummaryResponseDTO>>builder()
                    .code(e.getCode())
                    .info(e.getInfo())
                    .build();
        } catch (Exception e) {
            log.error("query my subscribed agent list failed", e);
            return Response.<List<AgentConfigSummaryResponseDTO>>builder()
                    .code(ResponseCode.UN_ERROR.getCode())
                    .info(ResponseCode.UN_ERROR.getInfo())
                    .build();
        }
    }

    @RequestMapping(value = "agent_config_page_query", method = RequestMethod.POST)
    @Override
    public Response<AgentConfigPageResponseDTO> queryAgentConfigPage(@RequestBody AgentConfigPageQueryRequestDTO requestDTO) {
        try {
            AgentConfigPageResultVO pageResult = agentConfigManageService.queryAgentConfigPage(toPageQueryVO(requestDTO));
            AgentConfigPageResponseDTO responseDTO = new AgentConfigPageResponseDTO();
            responseDTO.setPageNo(pageResult.getPageNo());
            responseDTO.setPageSize(pageResult.getPageSize());
            responseDTO.setTotal(pageResult.getTotal());
            responseDTO.setRecords(pageResult.getRecords() == null
                    ? Collections.emptyList()
                    : pageResult.getRecords().stream().map(this::toSummaryResponse).collect(Collectors.toList()));
            return Response.<AgentConfigPageResponseDTO>builder()
                    .code(ResponseCode.SUCCESS.getCode())
                    .info(ResponseCode.SUCCESS.getInfo())
                    .data(responseDTO)
                    .build();
        } catch (AppException e) {
            log.error("query agent config page failed", e);
            return Response.<AgentConfigPageResponseDTO>builder()
                    .code(e.getCode())
                    .info(e.getInfo())
                    .build();
        } catch (Exception e) {
            log.error("query agent config page failed", e);
            return Response.<AgentConfigPageResponseDTO>builder()
                    .code(ResponseCode.UN_ERROR.getCode())
                    .info(ResponseCode.UN_ERROR.getInfo())
                    .build();
        }
    }

    @RequestMapping(value = "agent_config_publish", method = RequestMethod.POST)
    @Override
    public Response<AgentConfigDetailResponseDTO> publishAgentConfig(@RequestBody AgentConfigPublishRequestDTO requestDTO) {
        try {
            AgentConfigManageVO published = agentConfigManageService.publishAgentConfig(requestDTO.getAgentId(), requestDTO.getOperator());
            return success(toDetailResponse(published));
        } catch (AppException e) {
            log.error("publish agent config failed", e);
            return fail(e.getCode(), e.getInfo());
        } catch (Exception e) {
            log.error("publish agent config failed", e);
            return fail(ResponseCode.UN_ERROR.getCode(), ResponseCode.UN_ERROR.getInfo());
        }
    }

    @RequestMapping(value = "agent_config_offline", method = RequestMethod.POST)
    @Override
    public Response<AgentConfigDetailResponseDTO> offlineAgentConfig(@RequestBody AgentConfigOfflineRequestDTO requestDTO) {
        try {
            AgentConfigManageVO offline = agentConfigManageService.offlineAgentConfig(requestDTO.getAgentId(), requestDTO.getOperator());
            return success(toDetailResponse(offline));
        } catch (AppException e) {
            log.error("offline agent config failed", e);
            return fail(e.getCode(), e.getInfo());
        } catch (Exception e) {
            log.error("offline agent config failed", e);
            return fail(ResponseCode.UN_ERROR.getCode(), ResponseCode.UN_ERROR.getInfo());
        }
    }

    @RequestMapping(value = "agent_config_rollback", method = RequestMethod.POST)
    @Override
    public Response<AgentConfigDetailResponseDTO> rollbackAgentConfig(@RequestBody AgentConfigRollbackRequestDTO requestDTO) {
        try {
            AgentConfigManageVO rollback = agentConfigManageService.rollbackAgentConfig(
                    requestDTO.getAgentId(),
                    requestDTO.getTargetVersion(),
                    requestDTO.getOperator()
            );
            return success(toDetailResponse(rollback));
        } catch (AppException e) {
            log.error("rollback agent config failed", e);
            return fail(e.getCode(), e.getInfo());
        } catch (Exception e) {
            log.error("rollback agent config failed", e);
            return fail(ResponseCode.UN_ERROR.getCode(), ResponseCode.UN_ERROR.getInfo());
        }
    }

    @RequestMapping(value = "agent_config_plaza_publish", method = RequestMethod.POST)
    @Override
    public Response<AgentConfigDetailResponseDTO> publishAgentToPlaza(@RequestBody AgentConfigPublishRequestDTO requestDTO) {
        try {
            AgentConfigManageVO updated = agentConfigManageService.publishAgentToPlaza(requestDTO.getAgentId(), requestDTO.getOperator());
            return success(toDetailResponse(updated));
        } catch (AppException e) {
            log.error("publish agent to plaza failed", e);
            return fail(e.getCode(), e.getInfo());
        } catch (Exception e) {
            log.error("publish agent to plaza failed", e);
            return fail(ResponseCode.UN_ERROR.getCode(), ResponseCode.UN_ERROR.getInfo());
        }
    }

    @RequestMapping(value = "agent_config_plaza_offline", method = RequestMethod.POST)
    @Override
    public Response<AgentConfigDetailResponseDTO> unpublishAgentFromPlaza(@RequestBody AgentConfigOfflineRequestDTO requestDTO) {
        try {
            AgentConfigManageVO updated = agentConfigManageService.unpublishAgentFromPlaza(requestDTO.getAgentId(), requestDTO.getOperator());
            return success(toDetailResponse(updated));
        } catch (AppException e) {
            log.error("unpublish agent from plaza failed", e);
            return fail(e.getCode(), e.getInfo());
        } catch (Exception e) {
            log.error("unpublish agent from plaza failed", e);
            return fail(ResponseCode.UN_ERROR.getCode(), ResponseCode.UN_ERROR.getInfo());
        }
    }

    @RequestMapping(value = "agent_config_subscribe", method = RequestMethod.POST)
    @Override
    public Response<Boolean> subscribeAgentConfig(@RequestBody AgentConfigSubscribeRequestDTO requestDTO) {
        try {
            boolean subscribed = agentConfigManageService.subscribeAgent(requestDTO.getUserId(), requestDTO.getAgentId());
            return Response.<Boolean>builder()
                    .code(ResponseCode.SUCCESS.getCode())
                    .info(ResponseCode.SUCCESS.getInfo())
                    .data(subscribed)
                    .build();
        } catch (AppException e) {
            log.error("subscribe agent failed", e);
            return Response.<Boolean>builder()
                    .code(e.getCode())
                    .info(e.getInfo())
                    .data(false)
                    .build();
        } catch (Exception e) {
            log.error("subscribe agent failed", e);
            return Response.<Boolean>builder()
                    .code(ResponseCode.UN_ERROR.getCode())
                    .info(ResponseCode.UN_ERROR.getInfo())
                    .data(false)
                    .build();
        }
    }

    @RequestMapping(value = "agent_config_unsubscribe", method = RequestMethod.POST)
    @Override
    public Response<Boolean> unsubscribeAgentConfig(@RequestBody AgentConfigSubscribeRequestDTO requestDTO) {
        try {
            boolean unsubscribed = agentConfigManageService.unsubscribeAgent(requestDTO.getUserId(), requestDTO.getAgentId());
            return Response.<Boolean>builder()
                    .code(ResponseCode.SUCCESS.getCode())
                    .info(ResponseCode.SUCCESS.getInfo())
                    .data(unsubscribed)
                    .build();
        } catch (AppException e) {
            log.error("unsubscribe agent failed", e);
            return Response.<Boolean>builder()
                    .code(e.getCode())
                    .info(e.getInfo())
                    .data(false)
                    .build();
        } catch (Exception e) {
            log.error("unsubscribe agent failed", e);
            return Response.<Boolean>builder()
                    .code(ResponseCode.UN_ERROR.getCode())
                    .info(ResponseCode.UN_ERROR.getInfo())
                    .data(false)
                    .build();
        }
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

    private AgentConfigManageVO toManageVO(AgentConfigUpsertRequestDTO requestDTO) {
        return AgentConfigManageVO.builder()
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

    private AgentConfigDetailResponseDTO toDetailResponse(AgentConfigManageVO source) {
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

    private AgentConfigSummaryResponseDTO toSummaryResponse(AgentConfigManageVO source) {
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

    private Response<AgentConfigDetailResponseDTO> success(AgentConfigDetailResponseDTO data) {
        return Response.<AgentConfigDetailResponseDTO>builder()
                .code(ResponseCode.SUCCESS.getCode())
                .info(ResponseCode.SUCCESS.getInfo())
                .data(data)
                .build();
    }

    private Response<AgentConfigDetailResponseDTO> fail(String code, String info) {
        return Response.<AgentConfigDetailResponseDTO>builder()
                .code(code)
                .info(info)
                .build();
    }

}
