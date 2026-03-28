package cn.caliu.agent.domain.agent.service.armory.node;

import cn.bugstack.wrench.design.framework.tree.StrategyHandler;
import cn.caliu.agent.domain.agent.model.entity.ArmoryCommandEntity;
import cn.caliu.agent.domain.agent.model.valobj.AiAgentConfigTableVO;
import cn.caliu.agent.domain.agent.model.valobj.AiAgentRegisterVO;
import cn.caliu.agent.domain.agent.service.armory.AbstractArmorySupport;
import cn.caliu.agent.domain.agent.service.armory.factory.DefaultArmoryFactory;
import com.google.adk.agents.SequentialAgent;
import com.google.adk.runner.InMemoryRunner;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * 执行节点
 */

@Slf4j
@Service
public class RunnerNode extends AbstractArmorySupport {


    @Override
    protected AiAgentRegisterVO doApply(ArmoryCommandEntity requestParameter, DefaultArmoryFactory.DynamicContext dynamicContext) throws Exception {
        log.info("Agent装配操作-RunnerNode");

        AiAgentConfigTableVO aiAgentConfigTableVO = requestParameter.getAiAgentConfigTableVO();
        String appName = aiAgentConfigTableVO.getAppName();
        AiAgentConfigTableVO.Agent agent = aiAgentConfigTableVO.getAgent();
        String agentId = agent.getAgentId();
        String agentName = agent.getAgentName();
        String agentDesc = agent.getAgentDesc();

        // 获取上下文对象
        SequentialAgent sequentialAgent = dynamicContext.getSequentialAgent();

        InMemoryRunner runner = new InMemoryRunner(sequentialAgent, appName);

        AiAgentRegisterVO aiAgentRegisterVO = AiAgentRegisterVO.builder()
                .appName(appName)
                .agentId(agentId)
                .agentName(agentName)
                .agentDesc(agentDesc)
                .runner(runner)
                .build();

        // 注册到 Spring 容器
        registerBean(agentId, AiAgentRegisterVO.class, aiAgentRegisterVO);

        return aiAgentRegisterVO;

    }

    @Override
    public StrategyHandler<ArmoryCommandEntity, DefaultArmoryFactory.DynamicContext, AiAgentRegisterVO> get(ArmoryCommandEntity requestParameter, DefaultArmoryFactory.DynamicContext dynamicContext) throws Exception {
        return null;
    }
}
