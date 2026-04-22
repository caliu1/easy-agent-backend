package cn.caliu.agent.domain.agent.service.armory.matter.skills;

import cn.caliu.agent.domain.agent.model.valobj.AiAgentConfigTableVO;
import org.springframework.ai.tool.ToolCallback;

public interface IToolSkillsCreateService {

    ToolCallback[] buildToolCallback(AiAgentConfigTableVO.Module.ChatModel.ToolSkills toolSkills ) throws Exception;
}
