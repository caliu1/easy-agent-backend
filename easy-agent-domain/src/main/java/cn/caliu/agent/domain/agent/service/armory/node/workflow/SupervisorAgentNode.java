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

@Slf4j
@Service("supervisorAgentNode")
public class SupervisorAgentNode extends AbstractArmorySupport {

    @Override
    protected AiAgentRegisterVO doApply(ArmoryCommandEntity requestParameter, DefaultArmoryFactory.DynamicContext dynamicContext) throws Exception {
        log.info("Agent workflow build - SupervisorAgentNode");

        AiAgentConfigTableVO.Module.AgentWorkflow currentAgentWorkflow = dynamicContext.getCurrentAgentWorkflow();

        List<String> subAgentNames = currentAgentWorkflow.getSubAgents();
        List<BaseAgent> subAgents = dynamicContext.queryAgentList(subAgentNames);

        if (subAgents == null || subAgents.isEmpty()) {
            throw new AppException(ResponseCode.ILLEGAL_PARAMETER.getCode(), "supervisor workflow subAgents is empty");
        }

        String routerAgentName = StringUtils.trimToEmpty(currentAgentWorkflow.getRouterAgent());
        if (StringUtils.isBlank(routerAgentName) && subAgentNames != null && !subAgentNames.isEmpty()) {
            routerAgentName = subAgentNames.get(0);
        }
        if (StringUtils.isBlank(routerAgentName)) {
            throw new AppException(ResponseCode.ILLEGAL_PARAMETER.getCode(), "supervisor workflow routerAgent is empty");
        }

        SupervisorRoutingAgent supervisorRoutingAgent = new SupervisorRoutingAgent(
                currentAgentWorkflow.getName(),
                currentAgentWorkflow.getDescription(),
                subAgents,
                routerAgentName,
                currentAgentWorkflow.getMaxIterations()
        );

        dynamicContext.getAgentGroup().put(currentAgentWorkflow.getName(), supervisorRoutingAgent);

        // Register bean for later runner lookup.
        registerBean(currentAgentWorkflow.getName(), SupervisorRoutingAgent.class, supervisorRoutingAgent);

        return router(requestParameter, dynamicContext);
    }

    @Override
    public StrategyHandler<ArmoryCommandEntity, DefaultArmoryFactory.DynamicContext, AiAgentRegisterVO> get(ArmoryCommandEntity requestParameter, DefaultArmoryFactory.DynamicContext dynamicContext) throws Exception {
        return getBean("agentWorkflowNode");
    }

}
