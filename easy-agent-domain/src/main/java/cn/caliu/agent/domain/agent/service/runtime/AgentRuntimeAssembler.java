package cn.caliu.agent.domain.agent.service.runtime;

import cn.caliu.agent.domain.agent.model.valobj.AgentConfigManageVO;
import cn.caliu.agent.domain.agent.model.valobj.AiAgentConfigTableVO;
import cn.caliu.agent.domain.agent.model.valobj.AiAgentRegisterVO;
import cn.caliu.agent.domain.agent.service.IArmoryService;
import cn.caliu.agent.domain.agent.service.armory.factory.DefaultArmoryFactory;
import cn.caliu.agent.types.enums.ResponseCode;
import cn.caliu.agent.types.exception.AppException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.util.List;

@Service
public class AgentRuntimeAssembler {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    @Resource
    private IArmoryService armoryService;
    @Resource
    private DefaultArmoryFactory defaultArmoryFactory;

    public AiAgentRegisterVO assemble(AgentConfigManageVO configVO) {
        if (configVO == null) {
            throw new AppException(ResponseCode.ILLEGAL_PARAMETER.getCode(), "agent config is null");
        }

        AiAgentConfigTableVO tableVO = mergeConfigAndMetadata(configVO);
        try {
            armoryService.acceptArmoryAgents(List.of(tableVO));
        } catch (Exception e) {
            throw new AppException(ResponseCode.UN_ERROR.getCode(), "armory assemble failed: " + e.getMessage());
        }

        AiAgentRegisterVO registerVO = defaultArmoryFactory.getAiAgentRegisterBean(configVO.getAgentId());
        if (registerVO == null) {
            throw new AppException(ResponseCode.E0001.getCode(), "runtime register not found: " + configVO.getAgentId());
        }
        return registerVO;
    }

    public AiAgentConfigTableVO parseConfigJson(String configJson) {
        if (StringUtils.isBlank(configJson)) {
            throw new AppException(ResponseCode.ILLEGAL_PARAMETER.getCode(), "configJson is blank");
        }

        try {
            AiAgentConfigTableVO tableVO = OBJECT_MAPPER.readValue(configJson, AiAgentConfigTableVO.class);
            if (tableVO == null) {
                throw new AppException(ResponseCode.ILLEGAL_PARAMETER.getCode(), "configJson parsed as null");
            }
            return tableVO;
        } catch (AppException e) {
            throw e;
        } catch (Exception e) {
            throw new AppException(ResponseCode.ILLEGAL_PARAMETER.getCode(), "configJson parse failed: " + e.getMessage());
        }
    }

    public AiAgentConfigTableVO mergeConfigAndMetadata(AgentConfigManageVO configVO) {
        AiAgentConfigTableVO tableVO = parseConfigJson(configVO.getConfigJson());

        if (tableVO.getAgent() == null) {
            tableVO.setAgent(new AiAgentConfigTableVO.Agent());
        }

        String mergedAgentId = firstNonBlank(configVO.getAgentId(), tableVO.getAgent().getAgentId());
        String mergedAgentName = firstNonBlank(configVO.getAgentName(), tableVO.getAgent().getAgentName());
        String mergedAgentDesc = firstNonBlank(configVO.getAgentDesc(), tableVO.getAgent().getAgentDesc());
        String mergedAppName = firstNonBlank(configVO.getAppName(), tableVO.getAppName());

        if (StringUtils.isBlank(mergedAgentId)) {
            throw new AppException(ResponseCode.ILLEGAL_PARAMETER.getCode(), "agentId is blank");
        }
        if (StringUtils.isBlank(mergedAppName)) {
            throw new AppException(ResponseCode.ILLEGAL_PARAMETER.getCode(), "appName is blank");
        }

        tableVO.setAppName(mergedAppName);
        tableVO.getAgent().setAgentId(mergedAgentId);
        tableVO.getAgent().setAgentName(StringUtils.defaultString(mergedAgentName));
        tableVO.getAgent().setAgentDesc(StringUtils.defaultString(mergedAgentDesc));
        return tableVO;
    }

    private String firstNonBlank(String primary, String fallback) {
        return StringUtils.isNotBlank(primary) ? primary.trim() : StringUtils.trimToEmpty(fallback);
    }

}

