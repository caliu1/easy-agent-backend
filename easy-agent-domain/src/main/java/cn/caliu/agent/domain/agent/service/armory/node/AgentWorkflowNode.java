package cn.caliu.agent.domain.agent.service.armory.node;

import cn.bugstack.wrench.design.framework.tree.StrategyHandler;
import cn.caliu.agent.domain.agent.model.entity.ArmoryCommandEntity;
import cn.caliu.agent.domain.agent.model.valobj.AiAgentConfigTableVO;
import cn.caliu.agent.domain.agent.model.valobj.AiAgentRegisterVO;
import cn.caliu.agent.domain.agent.model.valobj.enums.AgentTypeEnum;
import cn.caliu.agent.domain.agent.service.armory.AbstractArmorySupport;
import cn.caliu.agent.domain.agent.service.armory.factory.DefaultArmoryFactory;
import cn.caliu.agent.domain.agent.service.armory.node.workflow.LoopAgentNode;
import cn.caliu.agent.domain.agent.service.armory.node.workflow.ParallelAgentNode;
import cn.caliu.agent.domain.agent.service.armory.node.workflow.SequentialAgentNode;
import cn.caliu.agent.domain.agent.service.armory.node.workflow.SupervisorAgentNode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.util.List;

/**
 * 工作流分发节点。
 *
 * 作用：
 * 1) 逐个读取 module.agentWorkflows 配置；
 * 2) 将当前 workflow 放入 dynamicContext；
 * 3) 根据 type 分派到对应节点（loop/parallel/sequential/supervisor）；
 * 4) 当 workflow 全部处理完成后，进入 RunnerNode 创建最终运行器。
 */
@Slf4j
@Service
public class AgentWorkflowNode extends AbstractArmorySupport {
    @Resource
    private LoopAgentNode loopAgentNode;
    @Resource
    private ParallelAgentNode parallelAgentNode;
    @Resource
    private SequentialAgentNode sequentialAgentNode;
    @Resource
    private SupervisorAgentNode supervisorAgentNode;
    @Resource
    private RunnerNode runnerNode;

    @Override
    protected AiAgentRegisterVO doApply(ArmoryCommandEntity requestParameter, DefaultArmoryFactory.DynamicContext dynamicContext) throws Exception {
        log.info("Agent 装配操作 - AgentWorkflowNode");

        AiAgentConfigTableVO aiAgentConfigTableVO = requestParameter.getAiAgentConfigTableVO();
        List<AiAgentConfigTableVO.Module.AgentWorkflow> agentWorkflows = aiAgentConfigTableVO.getModule().getAgentWorkflows();

        // 没有工作流，或已经处理完全部工作流，则清空 currentAgentWorkflow 并进入下一节点（RunnerNode）。
        if (agentWorkflows == null
                || agentWorkflows.isEmpty()
                || dynamicContext.getCurrentStepIndex() >= agentWorkflows.size()) {
            dynamicContext.setCurrentAgentWorkflow(null);
            return router(requestParameter, dynamicContext);
        }

        // 取当前索引的工作流并推进索引，供 get(...) 分发到具体节点执行。
        dynamicContext.setCurrentAgentWorkflow(agentWorkflows.get(dynamicContext.getCurrentStepIndex()));
        dynamicContext.addCurrentStepIndex();

        return router(requestParameter, dynamicContext);
    }

    @Override
    public StrategyHandler<ArmoryCommandEntity, DefaultArmoryFactory.DynamicContext, AiAgentRegisterVO> get(ArmoryCommandEntity requestParameter, DefaultArmoryFactory.DynamicContext dynamicContext) throws Exception {
        AiAgentConfigTableVO.Module.AgentWorkflow agentWorkflow = dynamicContext.getCurrentAgentWorkflow();

        // currentAgentWorkflow 为空表示工作流已处理完，直接进入 Runner 构建运行时。
        if (agentWorkflow == null) {
            return runnerNode;
        }

        String type = agentWorkflow.getType();
        AgentTypeEnum agentTypeEnum = AgentTypeEnum.formType(type);
        if (agentTypeEnum == null) {
            throw new RuntimeException("agentWorkflows.type is invalid: " + type);
        }

        String node = agentTypeEnum.getNode();
        return switch (node) {
            case "loopAgentNode" -> loopAgentNode;
            case "parallelAgentNode" -> parallelAgentNode;
            case "sequentialAgentNode" -> sequentialAgentNode;
            case "supervisorAgentNode" -> supervisorAgentNode;
            default -> runnerNode;
        };
    }
}

