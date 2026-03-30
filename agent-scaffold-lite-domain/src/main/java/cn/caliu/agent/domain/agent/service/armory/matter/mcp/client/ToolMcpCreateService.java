package cn.caliu.agent.domain.agent.service.armory.matter.mcp.client;

import cn.caliu.agent.domain.agent.model.valobj.AiAgentConfigTableVO;
import org.springframework.ai.tool.ToolCallback;

public interface ToolMcpCreateService {

    ToolCallback[] buildToolCallback(AiAgentConfigTableVO.Module.ChatModel.ToolMcp toolMcp) throws Exception;

}
