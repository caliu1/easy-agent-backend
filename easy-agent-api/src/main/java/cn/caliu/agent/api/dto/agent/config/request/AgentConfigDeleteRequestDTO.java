package cn.caliu.agent.api.dto.agent.config.request;

import lombok.Data;
/**
 * AgentConfigDeleteRequestDTO DTO，用于接口层数据传输。
 */

@Data
public class AgentConfigDeleteRequestDTO {

    private String agentId;
    private String operator;

}


