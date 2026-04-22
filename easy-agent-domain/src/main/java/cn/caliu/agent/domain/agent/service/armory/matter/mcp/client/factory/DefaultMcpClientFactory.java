package cn.caliu.agent.domain.agent.service.armory.matter.mcp.client.factory;

import cn.caliu.agent.domain.agent.model.valobj.AiAgentConfigTableVO;
import cn.caliu.agent.domain.agent.service.armory.matter.mcp.client.IToolMcpCreateService;
import cn.caliu.agent.domain.agent.service.armory.matter.mcp.client.impl.LocalToolMcpCreateService;
import cn.caliu.agent.domain.agent.service.armory.matter.mcp.client.impl.SSEToolMcpCreateService;
import cn.caliu.agent.domain.agent.service.armory.matter.mcp.client.impl.StdioToolMcpCreateService;
import cn.caliu.agent.types.enums.ResponseCode;
import cn.caliu.agent.types.exception.AppException;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Slf4j
@Service
public class DefaultMcpClientFactory {

    @Resource
    private LocalToolMcpCreateService localToolMcpCreateService;

    @Resource
    private SSEToolMcpCreateService sseToolMcpCreateService;

    @Resource
    private StdioToolMcpCreateService stdioToolMcpCreateService;

    public IToolMcpCreateService getToolMcpCreateService(AiAgentConfigTableVO.Module.ChatModel.ToolMcp toolMcp) {
        if (null != toolMcp.getLocal()) return localToolMcpCreateService;
        if (null != toolMcp.getSse()) return sseToolMcpCreateService;
        if (null != toolMcp.getStdio()) return stdioToolMcpCreateService;
        throw new AppException(ResponseCode.NOT_FOUND_METHOD.getCode(), ResponseCode.NOT_FOUND_METHOD.getInfo());
    }

}
