package cn.caliu.agent.api.dto.agent.config.request;

import lombok.Data;

@Data
public class AgentConfigRollbackRequestDTO {

    private String agentId;
    private Long targetVersion;
    private String operator;

}


