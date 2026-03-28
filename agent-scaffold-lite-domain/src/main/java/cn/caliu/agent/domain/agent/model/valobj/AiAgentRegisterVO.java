package cn.caliu.agent.domain.agent.model.valobj;


import com.google.adk.runner.InMemoryRunner;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * Ai Agent 智能体注册值对象
 * @author xiaofuge bugstack.cn @小傅哥
 * 2025/12/17 08:19
 */
@Getter
@Builder
//@AllArgsConstructor
//@NoArgsConstructor
public class AiAgentRegisterVO {
    private String appName;
    private String agentId;
    private String agentName;
    private String agentDesc;
    private InMemoryRunner runner;
}
