package cn.caliu.agent.domain.agent.service.armory.factory;

import cn.caliu.agent.domain.agent.model.entity.ArmoryCommandEntity;
import cn.caliu.agent.domain.agent.model.valobj.AiAgentConfigTableVO;
import cn.caliu.agent.domain.agent.model.valobj.AiAgentRegisterVO;
import cn.caliu.agent.domain.agent.service.armory.node.RootNode;
import cn.caliu.agent.domain.agent.service.runtime.AgentRuntimeRegistry;
import cn.bugstack.wrench.design.framework.tree.StrategyHandler;
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
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;


@Service
public class DefaultArmoryFactory {

    @Resource
    private ApplicationContext applicationContext;

    @Resource
    private RootNode rootNode;
    @Resource
    private AgentRuntimeRegistry agentRuntimeRegistry;

    public StrategyHandler<ArmoryCommandEntity, DynamicContext, AiAgentRegisterVO> armoryStrategyHandler() {
        return rootNode;
    }

    /**
     * 默认取当前激活版本；未激活时回退到 Spring 容器中的同名 Bean。
     */
    public AiAgentRegisterVO getAiAgentRegisterVO(String agentId){
        return agentRuntimeRegistry.getActiveRegisterVO(agentId)
                .orElseGet(() -> getAiAgentRegisterBean(agentId));
    }

    /**
     * 按指定版本取 Runner，未命中时回退到当前激活版本。
     */
    public AiAgentRegisterVO getAiAgentRegisterVO(String agentId, Long configVersion){
        if (configVersion != null && configVersion > 0) {
            Optional<AiAgentRegisterVO> versionRunner = agentRuntimeRegistry.getRegisterVO(agentId, configVersion);
            if (versionRunner.isPresent()) {
                return versionRunner.get();
            }
        }
        return getAiAgentRegisterVO(agentId);
    }

    /**
     * 仅从 Spring 容器直接获取 Bean，用于装配后取回最新 Runner。
     */
    public AiAgentRegisterVO getAiAgentRegisterBean(String agentId) {
        if (!applicationContext.containsBean(agentId)) {
            return null;
        }
        return applicationContext.getBean(agentId, AiAgentRegisterVO.class);
    }

    /**
     * 定义一个上下文对象，用于各个节点串联的时候，写入数据和使用数据
     */
    @Data
    @Builder
    @AllArgsConstructor
    @NoArgsConstructor
    public static class DynamicContext {

        private OpenAiApi openAiApi;

        private ChatModel chatModel;

        /**
         * 最后一个智能体节点
         */
        private SequentialAgent sequentialAgent;
        /**
         * 智能体配置组
         */
        private Map<String, BaseAgent> agentGroup = new HashMap<>();

        // private List<AiAgentConfigTableVO.Module.AgentWorkflow> agentWorkflows = new ArrayList<>();

        private AtomicInteger currentStepIndex = new AtomicInteger(0);
        private AiAgentConfigTableVO.Module.AgentWorkflow currentAgentWorkflow;

        private Map<String, Object> dataObjects = new HashMap<>();

        public <T> void setValue(String key, T value) {
            dataObjects.put(key, value);
        }

        public <T> T getValue(String key) {
            return (T) dataObjects.get(key);
        }

        public List<BaseAgent> queryAgentList(List<String> agentNames){
            if (agentNames == null || agentNames.isEmpty() || agentGroup.isEmpty()) {
                return Collections.emptyList();
            }

            List<BaseAgent> agents = new ArrayList<>();
            for(String agentName:agentNames){
                BaseAgent baseAgent = agentGroup.get(agentName);
                if (baseAgent != null) {
                    agents.add(baseAgent);
                }
            }

            return agents;
        }

        public void addCurrentStepIndex(){
            currentStepIndex.incrementAndGet();
        }
        public int getCurrentStepIndex(){
            return currentStepIndex.get();
        }
    }

}
