package cn.caliu.agent.trigger.http;

import cn.caliu.agent.api.IAgentConfigAdminService;
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
import cn.caliu.agent.api.dto.agent.config.request.AgentConfigSubscribeRequestDTO;
import cn.caliu.agent.api.dto.agent.config.request.AgentSkillProfileDeleteRequestDTO;
import cn.caliu.agent.api.dto.agent.config.request.AgentSkillProfileUpsertRequestDTO;
import cn.caliu.agent.api.dto.agent.config.response.AgentMcpProfileResponseDTO;
import cn.caliu.agent.api.dto.agent.config.response.AgentSkillAssetsResponseDTO;
import cn.caliu.agent.api.dto.agent.config.response.AgentSkillImportResponseDTO;
import cn.caliu.agent.api.dto.agent.config.response.AgentSkillProfileResponseDTO;
import cn.caliu.agent.api.dto.agent.config.response.AgentConfigSummaryResponseDTO;
import cn.caliu.agent.api.dto.agent.config.request.AgentConfigUpsertRequestDTO;
import cn.caliu.agent.api.response.Response;
import cn.caliu.agent.types.enums.ResponseCode;
import cn.caliu.agent.types.exception.AppException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import javax.annotation.Resource;
import java.util.List;

/**
 * Agent 配置管理 HTTP 控制器。
 *
 * 覆盖管理台能力：
 * 1. 配置 CRUD、发布、下线、回滚。
 * 2. 广场发布状态管理与订阅操作。
 * 3. 统一响应结构与错误处理。
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/")
@CrossOrigin("*")
public class AgentConfigAdminController implements IAgentConfigAdminService {

    @Resource
    private IAgentConfigApplicationService agentConfigApplicationService;

    @RequestMapping(value = "agent_config_create", method = RequestMethod.POST)
    @Override
    public Response<AgentConfigDetailResponseDTO> createAgentConfig(@RequestBody AgentConfigUpsertRequestDTO requestDTO) {
        try {
            return success(agentConfigApplicationService.createAgentConfig(requestDTO));
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
            return success(agentConfigApplicationService.updateAgentConfig(requestDTO));
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
            boolean deleted = agentConfigApplicationService.deleteAgentConfig(requestDTO);
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
            return success(agentConfigApplicationService.queryAgentConfigDetail(agentId));
        } catch (AppException e) {
            log.error("query agent config detail failed", e);
            return fail(e.getCode(), e.getInfo());
        } catch (Exception e) {
            log.error("query agent config detail failed", e);
            return fail(ResponseCode.UN_ERROR.getCode(), ResponseCode.UN_ERROR.getInfo());
        }
    }

    @RequestMapping(value = "agent_config_plaza_list", method = RequestMethod.GET)
    @Override
    public Response<List<AgentConfigSummaryResponseDTO>> queryAgentPlazaList() {
        try {
            List<AgentConfigSummaryResponseDTO> list = agentConfigApplicationService.queryAgentPlazaList();
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
            List<AgentConfigSummaryResponseDTO> list = agentConfigApplicationService.queryMySubscribedAgentConfigList(userId);
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
            AgentConfigPageResponseDTO responseDTO = agentConfigApplicationService.queryAgentConfigPage(requestDTO);
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
            return success(agentConfigApplicationService.publishAgentConfig(requestDTO));
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
            return success(agentConfigApplicationService.offlineAgentConfig(requestDTO));
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
            return success(agentConfigApplicationService.rollbackAgentConfig(requestDTO));
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
            return success(agentConfigApplicationService.publishAgentToPlaza(requestDTO));
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
            return success(agentConfigApplicationService.unpublishAgentFromPlaza(requestDTO));
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
            boolean subscribed = agentConfigApplicationService.subscribeAgentConfig(requestDTO);
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
            boolean unsubscribed = agentConfigApplicationService.unsubscribeAgentConfig(requestDTO);
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

    @RequestMapping(value = "agent_skill_import_zip", method = RequestMethod.POST)
    public Response<AgentSkillImportResponseDTO> importSkillZip(
            @RequestParam("file") MultipartFile file,
            @RequestParam(value = "operator", required = false) String operator
    ) {
        try {
            if (file == null || file.isEmpty()) {
                throw new AppException(ResponseCode.ILLEGAL_PARAMETER.getCode(), "zip file is empty");
            }

            AgentSkillImportResponseDTO responseDTO = agentConfigApplicationService.importSkillZip(
                    operator,
                    file.getOriginalFilename(),
                    file.getBytes()
            );

            return Response.<AgentSkillImportResponseDTO>builder()
                    .code(ResponseCode.SUCCESS.getCode())
                    .info(ResponseCode.SUCCESS.getInfo())
                    .data(responseDTO)
                    .build();
        } catch (AppException e) {
            log.error("import skill zip failed", e);
            return Response.<AgentSkillImportResponseDTO>builder()
                    .code(e.getCode())
                    .info(e.getInfo())
                    .build();
        } catch (Exception e) {
            log.error("import skill zip failed", e);
            return Response.<AgentSkillImportResponseDTO>builder()
                    .code(ResponseCode.UN_ERROR.getCode())
                    .info(ResponseCode.UN_ERROR.getInfo())
                    .build();
        }
    }

    @RequestMapping(value = "agent_skill_save", method = RequestMethod.POST)
    public Response<AgentSkillImportResponseDTO> saveSkillAssets(@RequestBody AgentSkillSaveRequestDTO requestDTO) {
        try {
            AgentSkillImportResponseDTO responseDTO = agentConfigApplicationService.saveSkillAssets(requestDTO);
            return Response.<AgentSkillImportResponseDTO>builder()
                    .code(ResponseCode.SUCCESS.getCode())
                    .info(ResponseCode.SUCCESS.getInfo())
                    .data(responseDTO)
                    .build();
        } catch (AppException e) {
            log.error("save skill assets failed", e);
            return Response.<AgentSkillImportResponseDTO>builder()
                    .code(e.getCode())
                    .info(e.getInfo())
                    .build();
        } catch (Exception e) {
            log.error("save skill assets failed", e);
            return Response.<AgentSkillImportResponseDTO>builder()
                    .code(ResponseCode.UN_ERROR.getCode())
                    .info(ResponseCode.UN_ERROR.getInfo())
                    .build();
        }
    }

    @RequestMapping(value = "agent_skill_assets_query", method = RequestMethod.GET)
    @Override
    public Response<AgentSkillAssetsResponseDTO> querySkillAssets(@RequestParam("ossPath") String ossPath) {
        try {
            AgentSkillAssetsResponseDTO responseDTO = agentConfigApplicationService.querySkillAssets(ossPath);
            return Response.<AgentSkillAssetsResponseDTO>builder()
                    .code(ResponseCode.SUCCESS.getCode())
                    .info(ResponseCode.SUCCESS.getInfo())
                    .data(responseDTO)
                    .build();
        } catch (AppException e) {
            log.error("query skill assets failed", e);
            return Response.<AgentSkillAssetsResponseDTO>builder()
                    .code(e.getCode())
                    .info(e.getInfo())
                    .build();
        } catch (Exception e) {
            log.error("query skill assets failed", e);
            return Response.<AgentSkillAssetsResponseDTO>builder()
                    .code(ResponseCode.UN_ERROR.getCode())
                    .info(ResponseCode.UN_ERROR.getInfo())
                    .build();
        }
    }

    @RequestMapping(value = "agent_mcp_profile_create", method = RequestMethod.POST)
    @Override
    public Response<AgentMcpProfileResponseDTO> createMcpProfile(@RequestBody AgentMcpProfileUpsertRequestDTO requestDTO) {
        try {
            AgentMcpProfileResponseDTO responseDTO = agentConfigApplicationService.createMcpProfile(requestDTO);
            return Response.<AgentMcpProfileResponseDTO>builder()
                    .code(ResponseCode.SUCCESS.getCode())
                    .info(ResponseCode.SUCCESS.getInfo())
                    .data(responseDTO)
                    .build();
        } catch (AppException e) {
            log.error("create mcp profile failed", e);
            return Response.<AgentMcpProfileResponseDTO>builder()
                    .code(e.getCode())
                    .info(e.getInfo())
                    .build();
        } catch (Exception e) {
            log.error("create mcp profile failed", e);
            return Response.<AgentMcpProfileResponseDTO>builder()
                    .code(ResponseCode.UN_ERROR.getCode())
                    .info(ResponseCode.UN_ERROR.getInfo())
                    .build();
        }
    }

    @RequestMapping(value = "agent_mcp_profile_update", method = RequestMethod.POST)
    @Override
    public Response<AgentMcpProfileResponseDTO> updateMcpProfile(@RequestBody AgentMcpProfileUpsertRequestDTO requestDTO) {
        try {
            AgentMcpProfileResponseDTO responseDTO = agentConfigApplicationService.updateMcpProfile(requestDTO);
            return Response.<AgentMcpProfileResponseDTO>builder()
                    .code(ResponseCode.SUCCESS.getCode())
                    .info(ResponseCode.SUCCESS.getInfo())
                    .data(responseDTO)
                    .build();
        } catch (AppException e) {
            log.error("update mcp profile failed", e);
            return Response.<AgentMcpProfileResponseDTO>builder()
                    .code(e.getCode())
                    .info(e.getInfo())
                    .build();
        } catch (Exception e) {
            log.error("update mcp profile failed", e);
            return Response.<AgentMcpProfileResponseDTO>builder()
                    .code(ResponseCode.UN_ERROR.getCode())
                    .info(ResponseCode.UN_ERROR.getInfo())
                    .build();
        }
    }

    @RequestMapping(value = "agent_mcp_profile_delete", method = RequestMethod.POST)
    @Override
    public Response<Boolean> deleteMcpProfile(@RequestBody AgentMcpProfileDeleteRequestDTO requestDTO) {
        try {
            boolean deleted = agentConfigApplicationService.deleteMcpProfile(requestDTO);
            return Response.<Boolean>builder()
                    .code(ResponseCode.SUCCESS.getCode())
                    .info(ResponseCode.SUCCESS.getInfo())
                    .data(deleted)
                    .build();
        } catch (AppException e) {
            log.error("delete mcp profile failed", e);
            return Response.<Boolean>builder()
                    .code(e.getCode())
                    .info(e.getInfo())
                    .data(false)
                    .build();
        } catch (Exception e) {
            log.error("delete mcp profile failed", e);
            return Response.<Boolean>builder()
                    .code(ResponseCode.UN_ERROR.getCode())
                    .info(ResponseCode.UN_ERROR.getInfo())
                    .data(false)
                    .build();
        }
    }

    @RequestMapping(value = "agent_mcp_profile_list", method = RequestMethod.GET)
    @Override
    public Response<List<AgentMcpProfileResponseDTO>> queryMcpProfileList(@RequestParam("userId") String userId) {
        try {
            List<AgentMcpProfileResponseDTO> list = agentConfigApplicationService.queryMcpProfileList(userId);
            return Response.<List<AgentMcpProfileResponseDTO>>builder()
                    .code(ResponseCode.SUCCESS.getCode())
                    .info(ResponseCode.SUCCESS.getInfo())
                    .data(list)
                    .build();
        } catch (AppException e) {
            log.error("query mcp profile list failed", e);
            return Response.<List<AgentMcpProfileResponseDTO>>builder()
                    .code(e.getCode())
                    .info(e.getInfo())
                    .build();
        } catch (Exception e) {
            log.error("query mcp profile list failed", e);
            return Response.<List<AgentMcpProfileResponseDTO>>builder()
                    .code(ResponseCode.UN_ERROR.getCode())
                    .info(ResponseCode.UN_ERROR.getInfo())
                    .build();
        }
    }

    @RequestMapping(value = "agent_mcp_profile_test", method = RequestMethod.POST)
    @Override
    public Response<Boolean> testMcpProfileConnection(@RequestBody AgentMcpProfileUpsertRequestDTO requestDTO) {
        try {
            boolean connected = agentConfigApplicationService.testMcpProfileConnection(requestDTO);
            return Response.<Boolean>builder()
                    .code(ResponseCode.SUCCESS.getCode())
                    .info(ResponseCode.SUCCESS.getInfo())
                    .data(connected)
                    .build();
        } catch (AppException e) {
            log.error("test mcp profile connection failed", e);
            return Response.<Boolean>builder()
                    .code(e.getCode())
                    .info(e.getInfo())
                    .data(false)
                    .build();
        } catch (Exception e) {
            log.error("test mcp profile connection failed", e);
            return Response.<Boolean>builder()
                    .code(ResponseCode.UN_ERROR.getCode())
                    .info(ResponseCode.UN_ERROR.getInfo())
                    .data(false)
                    .build();
        }
    }

    @RequestMapping(value = "agent_skill_profile_create", method = RequestMethod.POST)
    @Override
    public Response<AgentSkillProfileResponseDTO> createSkillProfile(@RequestBody AgentSkillProfileUpsertRequestDTO requestDTO) {
        try {
            AgentSkillProfileResponseDTO responseDTO = agentConfigApplicationService.createSkillProfile(requestDTO);
            return Response.<AgentSkillProfileResponseDTO>builder()
                    .code(ResponseCode.SUCCESS.getCode())
                    .info(ResponseCode.SUCCESS.getInfo())
                    .data(responseDTO)
                    .build();
        } catch (AppException e) {
            log.error("create skill profile failed", e);
            return Response.<AgentSkillProfileResponseDTO>builder()
                    .code(e.getCode())
                    .info(e.getInfo())
                    .build();
        } catch (Exception e) {
            log.error("create skill profile failed", e);
            return Response.<AgentSkillProfileResponseDTO>builder()
                    .code(ResponseCode.UN_ERROR.getCode())
                    .info(ResponseCode.UN_ERROR.getInfo())
                    .build();
        }
    }

    @RequestMapping(value = "agent_skill_profile_update", method = RequestMethod.POST)
    @Override
    public Response<AgentSkillProfileResponseDTO> updateSkillProfile(@RequestBody AgentSkillProfileUpsertRequestDTO requestDTO) {
        try {
            AgentSkillProfileResponseDTO responseDTO = agentConfigApplicationService.updateSkillProfile(requestDTO);
            return Response.<AgentSkillProfileResponseDTO>builder()
                    .code(ResponseCode.SUCCESS.getCode())
                    .info(ResponseCode.SUCCESS.getInfo())
                    .data(responseDTO)
                    .build();
        } catch (AppException e) {
            log.error("update skill profile failed", e);
            return Response.<AgentSkillProfileResponseDTO>builder()
                    .code(e.getCode())
                    .info(e.getInfo())
                    .build();
        } catch (Exception e) {
            log.error("update skill profile failed", e);
            return Response.<AgentSkillProfileResponseDTO>builder()
                    .code(ResponseCode.UN_ERROR.getCode())
                    .info(ResponseCode.UN_ERROR.getInfo())
                    .build();
        }
    }

    @RequestMapping(value = "agent_skill_profile_delete", method = RequestMethod.POST)
    @Override
    public Response<Boolean> deleteSkillProfile(@RequestBody AgentSkillProfileDeleteRequestDTO requestDTO) {
        try {
            boolean deleted = agentConfigApplicationService.deleteSkillProfile(requestDTO);
            return Response.<Boolean>builder()
                    .code(ResponseCode.SUCCESS.getCode())
                    .info(ResponseCode.SUCCESS.getInfo())
                    .data(deleted)
                    .build();
        } catch (AppException e) {
            log.error("delete skill profile failed", e);
            return Response.<Boolean>builder()
                    .code(e.getCode())
                    .info(e.getInfo())
                    .data(false)
                    .build();
        } catch (Exception e) {
            log.error("delete skill profile failed", e);
            return Response.<Boolean>builder()
                    .code(ResponseCode.UN_ERROR.getCode())
                    .info(ResponseCode.UN_ERROR.getInfo())
                    .data(false)
                    .build();
        }
    }

    @RequestMapping(value = "agent_skill_profile_list", method = RequestMethod.GET)
    @Override
    public Response<List<AgentSkillProfileResponseDTO>> querySkillProfileList(@RequestParam("userId") String userId) {
        try {
            List<AgentSkillProfileResponseDTO> list = agentConfigApplicationService.querySkillProfileList(userId);
            return Response.<List<AgentSkillProfileResponseDTO>>builder()
                    .code(ResponseCode.SUCCESS.getCode())
                    .info(ResponseCode.SUCCESS.getInfo())
                    .data(list)
                    .build();
        } catch (AppException e) {
            log.error("query skill profile list failed", e);
            return Response.<List<AgentSkillProfileResponseDTO>>builder()
                    .code(e.getCode())
                    .info(e.getInfo())
                    .build();
        } catch (Exception e) {
            log.error("query skill profile list failed", e);
            return Response.<List<AgentSkillProfileResponseDTO>>builder()
                    .code(ResponseCode.UN_ERROR.getCode())
                    .info(ResponseCode.UN_ERROR.getInfo())
                    .build();
        }
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

