package cn.caliu.agent.domain.agent.service.armory.node.workflow;

import cn.bugstack.wrench.design.framework.tree.StrategyHandler;
import cn.caliu.agent.domain.agent.model.entity.ArmoryCommandEntity;
import cn.caliu.agent.domain.agent.model.valobj.AiAgentConfigTableVO;
import cn.caliu.agent.domain.agent.model.valobj.AiAgentRegisterVO;
import cn.caliu.agent.domain.agent.model.valobj.enums.AgentTypeEnum;
import cn.caliu.agent.domain.agent.service.armory.AbstractArmorySupport;
import cn.caliu.agent.domain.agent.service.armory.factory.DefaultArmoryFactory;
import cn.caliu.agent.domain.agent.service.armory.node.RunnerNode;
import com.google.adk.agents.BaseAgent;
import com.google.adk.agents.ParallelAgent;
import com.google.adk.agents.SequentialAgent;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;

@Slf4j
@Service("sequentialAgentNode")
public class SequentialAgentNode extends AbstractArmorySupport {

    @Resource
    private RunnerNode runnerNode;
    @Override
    protected AiAgentRegisterVO doApply(ArmoryCommandEntity requestParameter, DefaultArmoryFactory.DynamicContext dynamicContext) throws Exception {
        log.info("Agent装配操作-SequentialAgentNode");

        List<AiAgentConfigTableVO.Module.AgentWorkflow> agentWorkflows = dynamicContext.getAgentWorkflows();
        AiAgentConfigTableVO.Module.AgentWorkflow agentWorkflow = agentWorkflows.remove(0);

        List<String> subAgentNames = agentWorkflow.getSubAgents();
        // log.info("===> 准备查找的子 Agent 名称: {}", subAgentNames);

        List<BaseAgent> subAgents = dynamicContext.queryAgentList(subAgentNames);
        // log.info("===> 实际查找到的子 Agent 数量: {}", subAgents == null ? 0 : subAgents.size()); // 新增日志

        SequentialAgent sequentialAgent = SequentialAgent.builder()
                .name(agentWorkflow.getName())
                .description(agentWorkflow.getDescription())
                .subAgents(subAgents)
                .build();

        dynamicContext.getAgentGroup().put(agentWorkflow.getName(), sequentialAgent);

        dynamicContext.setSequentialAgent(sequentialAgent);

        // 注册bean到spring容器
        registerBean(agentWorkflow.getName(), SequentialAgent.class, sequentialAgent);

        return router(requestParameter, dynamicContext);
    }

    @Override
    public StrategyHandler<ArmoryCommandEntity, DefaultArmoryFactory.DynamicContext, AiAgentRegisterVO> get(ArmoryCommandEntity requestParameter, DefaultArmoryFactory.DynamicContext dynamicContext) throws Exception {

        return runnerNode;
    }
}
