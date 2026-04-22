package cn.caliu.agent.api.dto;

import lombok.Data;

@Data
public class AgentConfigRollbackRequestDTO {

    private String agentId;
    private Long targetVersion;
    private String operator;

}

