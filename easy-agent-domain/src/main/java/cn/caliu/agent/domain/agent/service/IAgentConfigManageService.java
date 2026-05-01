package cn.caliu.agent.domain.agent.service;

import cn.caliu.agent.domain.agent.model.entity.AgentConfigEntity;
import cn.caliu.agent.domain.agent.model.valobj.AgentConfigPageQueryResult;
import cn.caliu.agent.domain.agent.model.valobj.AgentConfigPageQueryVO;
import cn.caliu.agent.domain.agent.model.valobj.SkillAssetEntryVO;
import cn.caliu.agent.domain.agent.model.valobj.SkillAssetsResultVO;
import cn.caliu.agent.domain.agent.model.valobj.SkillImportResultVO;

import java.util.List;

/**
 * Agent 配置领域服务接口。
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

    int reloadPublishedAgentRuntime();

    SkillImportResultVO importSkillZip(String operator, String fileName, byte[] zipBytes);

    SkillImportResultVO saveSkillAssets(String operator, String rootFolder, List<SkillAssetEntryVO> entries);

    /**
     * 按 OSS 路径读取 Skill 目录资产（文件/文件夹及文件内容）。
     */
    SkillAssetsResultVO querySkillAssets(String ossPath);

}

