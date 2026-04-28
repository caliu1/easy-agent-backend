package cn.caliu.agent.domain.agent.service.armory.factory;

import cn.bugstack.wrench.design.framework.tree.StrategyHandler;
import cn.caliu.agent.domain.agent.model.entity.ArmoryCommandEntity;
import cn.caliu.agent.domain.agent.model.valobj.AiAgentConfigTableVO;
import cn.caliu.agent.domain.agent.model.valobj.AiAgentRegisterVO;
import cn.caliu.agent.domain.agent.service.armory.node.RootNode;
import cn.caliu.agent.domain.agent.service.runtime.AgentRuntimeRegistry;
import com.google.adk.agents.BaseAgent;
import com.google.adk.agents.SequentialAgent;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.openai.api.OpenAiApi;
import org.springframework.context.ApplicationContext;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Armory 工厂。
 *
 * 作用：
 * 1. 提供装配策略链入口（RootNode）。
 * 2. 提供 Agent 运行时获取能力（active 版本优先，Bean 回退）。
 * 3. 定义装配节点间共享的 DynamicContext。
 */
@Service
public class DefaultArmoryFactory {

    @Resource
    private ApplicationContext applicationContext;
    @Resource
    private RootNode rootNode;
    @Resource
    private AgentRuntimeRegistry agentRuntimeRegistry;

    /**
     * 返回装配策略链入口。
     */
    public StrategyHandler<ArmoryCommandEntity, DynamicContext, AiAgentRegisterVO> armoryStrategyHandler() {
        return rootNode;
    }

    /**
     * 获取 Agent 运行时：
     * - 优先 active 版本
     * - 未激活时回退到 Spring 容器中的同名 Bean
     */
    public AiAgentRegisterVO getAiAgentRegisterVO(String agentId) {
        return agentRuntimeRegistry.getActiveRegisterVO(agentId)
                .orElseGet(() -> getAiAgentRegisterBean(agentId));
    }

    /**
     * 按指定版本获取运行时，未命中则回退到 active 版本。
     */
    public AiAgentRegisterVO getAiAgentRegisterVO(String agentId, Long configVersion) {
        if (configVersion != null && configVersion > 0) {
            Optional<AiAgentRegisterVO> versionRunner = agentRuntimeRegistry.getRegisterVO(agentId, configVersion);
            if (versionRunner.isPresent()) {
                return versionRunner.get();
            }
        }
        return getAiAgentRegisterVO(agentId);
    }

    /**
     * 仅从 Spring 容器中按名称查找运行时 Bean。
     */
    public AiAgentRegisterVO getAiAgentRegisterBean(String agentId) {
        if (!applicationContext.containsBean(agentId)) {
            return null;
        }
        return applicationContext.getBean(agentId, AiAgentRegisterVO.class);
    }

    /**
     * Armory 装配过程共享上下文。
     *
     * 保存：
     * - 模型基础依赖（OpenAiApi/ChatModel）
     * - 已装配的子 Agent 集合
     * - 当前 workflow 游标与临时对象
     */
    @Data
    @Builder
    @AllArgsConstructor
    @NoArgsConstructor
    public static class DynamicContext {

        private OpenAiApi openAiApi;
        private ChatModel chatModel;

        /**
         * 历史兼容字段（顺序工作流场景）。
         */
        private SequentialAgent sequentialAgent;

        /**
         * 已装配 Agent 集合：agentName -> agentInstance
         */
        private Map<String, BaseAgent> agentGroup = new HashMap<>();

        /**
         * 当前工作流索引与当前工作流对象。
         */
        private AtomicInteger currentStepIndex = new AtomicInteger(0);
        private AiAgentConfigTableVO.Module.AgentWorkflow currentAgentWorkflow;

        /**
         * 节点间共享的临时对象容器。
         */
        private Map<String, Object> dataObjects = new HashMap<>();

        public <T> void setValue(String key, T value) {
            dataObjects.put(key, value);
        }

        public <T> T getValue(String key) {
            return (T) dataObjects.get(key);
        }

        /**
         * 按名称列表查询上下文中已装配的 Agent 实例。
         */
        public List<BaseAgent> queryAgentList(List<String> agentNames) {
            if (agentNames == null || agentNames.isEmpty() || agentGroup.isEmpty()) {
                return Collections.emptyList();
            }

            List<BaseAgent> agents = new ArrayList<>();
            for (String agentName : agentNames) {
                BaseAgent baseAgent = agentGroup.get(agentName);
                if (baseAgent != null) {
                    agents.add(baseAgent);
                }
            }
            return agents;
        }

        public void addCurrentStepIndex() {
            currentStepIndex.incrementAndGet();
        }

        public int getCurrentStepIndex() {
            return currentStepIndex.get();
        }
    }
}

