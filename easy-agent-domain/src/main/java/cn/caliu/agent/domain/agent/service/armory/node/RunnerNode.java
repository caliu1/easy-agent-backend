package cn.caliu.agent.domain.agent.service.armory.node;

import cn.bugstack.wrench.design.framework.tree.StrategyHandler;
import cn.caliu.agent.domain.agent.model.entity.ArmoryCommandEntity;
import cn.caliu.agent.domain.agent.model.valobj.AiAgentConfigTableVO;
import cn.caliu.agent.domain.agent.model.valobj.AiAgentRegisterVO;
import cn.caliu.agent.domain.agent.service.armory.AbstractArmorySupport;
import cn.caliu.agent.domain.agent.service.armory.factory.DefaultArmoryFactory;
import cn.caliu.agent.types.enums.ResponseCode;
import cn.caliu.agent.types.exception.AppException;
import com.google.adk.agents.BaseAgent;
import com.google.adk.plugins.BasePlugin;
import com.google.adk.runner.InMemoryRunner;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.jetbrains.annotations.NotNull;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

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

        InMemoryRunner runner = getRunner(dynamicContext, aiAgentConfigTableVO, appName);

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

    @NotNull
    private InMemoryRunner getRunner(DefaultArmoryFactory.DynamicContext dynamicContext, AiAgentConfigTableVO aiAgentConfigTableVO, String appName) {
        AiAgentConfigTableVO.Module.Runner runnerConfig = aiAgentConfigTableVO.getModule().getRunner();

        String agentName = runnerConfig.getAgentName();
        if (StringUtils.isBlank(agentName)) {
            log.error("runner.agentName is null");
            throw new AppException(ResponseCode.ILLEGAL_PARAMETER.getCode(), ResponseCode.ILLEGAL_PARAMETER.getInfo());
        }

        BaseAgent baseAgent = dynamicContext.getAgentGroup().get(agentName);

        List<BasePlugin> plugins = new ArrayList<>();
        List<String> pluginNameList = runnerConfig.getPluginNameList();
        if (null != pluginNameList && !pluginNameList.isEmpty()) {
            for (String pluginName : pluginNameList) {
                BasePlugin plugin = getBean(pluginName);
                addPluginIfAbsent(plugins, plugin);
            }
        }

        // Token statistics is a cross-cutting concern and should always be enabled.
        tryAddPluginByBeanName(plugins, "myTokenUsagePlugin");

        return new InMemoryRunner(baseAgent, appName, plugins);
    }

    private void tryAddPluginByBeanName(List<BasePlugin> plugins, String beanName) {
        try {
            BasePlugin plugin = getBean(beanName);
            addPluginIfAbsent(plugins, plugin);
        } catch (Exception e) {
            log.warn("Optional plugin not found, beanName:{}", beanName);
        }
    }

    private void addPluginIfAbsent(List<BasePlugin> plugins, BasePlugin plugin) {
        if (plugin == null) {
            return;
        }

        boolean exists = plugins.stream()
                .filter(Objects::nonNull)
                .anyMatch(item -> StringUtils.equals(item.getName(), plugin.getName()));
        if (!exists) {
            plugins.add(plugin);
        }
    }


    @Override
    public StrategyHandler<ArmoryCommandEntity, DefaultArmoryFactory.DynamicContext, AiAgentRegisterVO> get(ArmoryCommandEntity requestParameter, DefaultArmoryFactory.DynamicContext dynamicContext) throws Exception {
        return defaultStrategyHandler;
    }
}
