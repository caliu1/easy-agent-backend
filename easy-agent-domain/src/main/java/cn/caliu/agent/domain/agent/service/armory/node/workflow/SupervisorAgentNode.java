package cn.caliu.agent.domain.agent.service.armory.node.workflow;

import cn.bugstack.wrench.design.framework.tree.StrategyHandler;
import cn.caliu.agent.domain.agent.model.entity.ArmoryCommandEntity;
import cn.caliu.agent.domain.agent.model.valobj.AiAgentConfigTableVO;
import cn.caliu.agent.domain.agent.model.valobj.AiAgentRegisterVO;
import cn.caliu.agent.domain.agent.service.armory.AbstractArmorySupport;
import cn.caliu.agent.domain.agent.service.armory.factory.DefaultArmoryFactory;
import cn.caliu.agent.types.enums.ResponseCode;
import cn.caliu.agent.types.exception.AppException;
import com.google.adk.agents.BaseAgent;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * Supervisor 工作流装配节点。
 *
 * 作用：
 * 1) 从当前 workflow 配置中解析 subAgents/routerAgent/maxIterations；
 * 2) 基于已装配好的子 Agent，构建 SupervisorRoutingAgent；
 * 3) 将 SupervisorRoutingAgent 放入 dynamicContext.agentGroup；
 * 4) 注册为 Spring Bean，供 RunnerNode 按名称查找并创建 InMemoryRunner。
 */
@Slf4j
@Service("supervisorAgentNode")
public class SupervisorAgentNode extends AbstractArmorySupport {

    @Override
    protected AiAgentRegisterVO doApply(ArmoryCommandEntity requestParameter, DefaultArmoryFactory.DynamicContext dynamicContext) throws Exception {
        log.info("Agent 工作流装配 - SupervisorAgentNode");

        AiAgentConfigTableVO.Module.AgentWorkflow currentAgentWorkflow = dynamicContext.getCurrentAgentWorkflow();

        // 1) 解析子 Agent 名称并从上下文取出对应实例。
        List<String> subAgentNames = currentAgentWorkflow.getSubAgents();
        List<BaseAgent> subAgents = dynamicContext.queryAgentList(subAgentNames);
        if (subAgents == null || subAgents.isEmpty()) {
            throw new AppException(ResponseCode.ILLEGAL_PARAMETER.getCode(), "Supervisor 工作流 subAgents 为空");
        }

        // 2) 解析 routerAgent。
        // 若配置未显式填写 routerAgent，则回退为 subAgents 第一项（兼容策略）。
        String routerAgentName = StringUtils.trimToEmpty(currentAgentWorkflow.getRouterAgent());
        if (StringUtils.isBlank(routerAgentName) && subAgentNames != null && !subAgentNames.isEmpty()) {
            routerAgentName = subAgentNames.get(0);
        }
        if (StringUtils.isBlank(routerAgentName)) {
            throw new AppException(ResponseCode.ILLEGAL_PARAMETER.getCode(), "Supervisor 工作流 routerAgent 为空");
        }

        // 3) 构建 SupervisorRoutingAgent（真正执行动态路由的核心对象）。
        SupervisorRoutingAgent supervisorRoutingAgent = new SupervisorRoutingAgent(
                currentAgentWorkflow.getName(),
                currentAgentWorkflow.getDescription(),
                subAgents,
                routerAgentName,
                currentAgentWorkflow.getMaxIterations()
        );

        // 4) 写入动态上下文，供后续 workflow/runner 流程继续使用。
        dynamicContext.getAgentGroup().put(currentAgentWorkflow.getName(), supervisorRoutingAgent);

        // 5) 注册 Bean，确保 RunnerNode 可以通过 runner.agentName 定位该工作流 Agent。
        registerBean(currentAgentWorkflow.getName(), SupervisorRoutingAgent.class, supervisorRoutingAgent);

        // 继续流转到下一个装配节点（通常回到 AgentWorkflowNode 或 RunnerNode）。
        return router(requestParameter, dynamicContext);
    }

    @Override
    public StrategyHandler<ArmoryCommandEntity, DefaultArmoryFactory.DynamicContext, AiAgentRegisterVO> get(ArmoryCommandEntity requestParameter, DefaultArmoryFactory.DynamicContext dynamicContext) throws Exception {
        // Supervisor 节点执行完成后，回到工作流分发节点，处理后续流程或进入 Runner。
        return getBean("agentWorkflowNode");
    }

}

