package cn.caliu.agent.domain.agent.service.armory.matter.mcp.client;

import cn.caliu.agent.domain.agent.model.valobj.AiAgentConfigTableVO;
import org.springframework.ai.tool.ToolCallback;
/**
 * IToolMcpCreateService 接口定义。
 */

public interface IToolMcpCreateService {

    ToolCallback[] buildToolCallback(AiAgentConfigTableVO.Module.ChatModel.ToolMcp toolMcp) throws Exception;

}
