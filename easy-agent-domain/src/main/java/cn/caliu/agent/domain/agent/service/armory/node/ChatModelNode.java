package cn.caliu.agent.domain.agent.service.armory.node;

import cn.bugstack.wrench.design.framework.tree.StrategyHandler;
import cn.caliu.agent.domain.agent.model.entity.ArmoryCommandEntity;
import cn.caliu.agent.domain.agent.model.valobj.AiAgentConfigTableVO;
import cn.caliu.agent.domain.agent.model.valobj.AiAgentRegisterVO;
import cn.caliu.agent.domain.agent.service.armory.AbstractArmorySupport;
import cn.caliu.agent.domain.agent.service.armory.factory.DefaultArmoryFactory;
import cn.caliu.agent.domain.agent.service.armory.matter.mcp.client.IToolMcpCreateService;
import cn.caliu.agent.domain.agent.service.armory.matter.mcp.client.factory.DefaultMcpClientFactory;
import cn.caliu.agent.domain.agent.service.armory.matter.skills.impl.DefaultToolSkillsCreateService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.ai.openai.api.OpenAiApi;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.util.ArrayList;
import java.util.List;

/**
 * 装配 ChatModel 与工具回调。
 */
@Slf4j
@Service
public class ChatModelNode extends AbstractArmorySupport {

    @Resource
    private AgentNode agentNode;
    @Resource
    private DefaultMcpClientFactory defaultMcpClientFactory;
    @Resource
    private DefaultToolSkillsCreateService defaultToolSkillsCreateService;

    @Override
    protected AiAgentRegisterVO doApply(ArmoryCommandEntity requestParameter, DefaultArmoryFactory.DynamicContext dynamicContext) throws Exception {
        log.info("Agent assembly node - ChatModelNode");

        OpenAiApi openAiApi = dynamicContext.getOpenAiApi();

        AiAgentConfigTableVO aiAgentConfigTableVO = requestParameter.getAiAgentConfigTableVO();
        AiAgentConfigTableVO.Module.ChatModel chatModelConfig = aiAgentConfigTableVO.getModule().getChatModel();
        String modelName = chatModelConfig.getModel();
        boolean streamUsageEnabled = chatModelConfig.getStreamUsage() == null || chatModelConfig.getStreamUsage();
        List<AiAgentConfigTableVO.Module.ChatModel.ToolMcp> toolMcpList = chatModelConfig.getToolMcpList();
        List<AiAgentConfigTableVO.Module.ChatModel.ToolSkills> toolSkillsList = chatModelConfig.getToolSkillsList();

        List<ToolCallback> toolCallbackList = new ArrayList<>();
        if (toolMcpList != null && !toolMcpList.isEmpty()) {
            for (AiAgentConfigTableVO.Module.ChatModel.ToolMcp toolMcp : toolMcpList) {
                IToolMcpCreateService toolMcpCreateService = defaultMcpClientFactory.getToolMcpCreateService(toolMcp);
                ToolCallback[] toolCallbacks = toolMcpCreateService.buildToolCallback(toolMcp);
                toolCallbackList.addAll(List.of(toolCallbacks));
            }
        }

        if (toolSkillsList != null && !toolSkillsList.isEmpty()) {
            for (AiAgentConfigTableVO.Module.ChatModel.ToolSkills toolSkills : toolSkillsList) {
                ToolCallback[] toolCallbacks = defaultToolSkillsCreateService.buildToolCallback(toolSkills);
                toolCallbackList.addAll(List.of(toolCallbacks));
            }
        }

        ChatModel chatModel = OpenAiChatModel.builder()
                .openAiApi(openAiApi)
                .defaultOptions(OpenAiChatOptions.builder()
                        .model(modelName)
                        .streamUsage(streamUsageEnabled)
                        .toolCallbacks(toolCallbackList)
                        .build())
                .build();

        dynamicContext.setChatModel(chatModel);
        return router(requestParameter, dynamicContext);
    }

    @Override
    public StrategyHandler<ArmoryCommandEntity, DefaultArmoryFactory.DynamicContext, AiAgentRegisterVO> get(ArmoryCommandEntity armoryCommandEntity, DefaultArmoryFactory.DynamicContext dynamicContext) throws Exception {
        return agentNode;
    }
}

