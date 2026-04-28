package cn.caliu.agent.api.dto.agent.config.request;

import lombok.Data;
/**
 * AgentConfigRollbackRequestDTO DTO，用于接口层数据传输。
 */

@Data
public class AgentConfigRollbackRequestDTO {

    private String agentId;
    private Long targetVersion;
    private String operator;

}


