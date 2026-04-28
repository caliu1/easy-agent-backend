package cn.caliu.agent.api.dto.agent.config.request;

import lombok.Data;
/**
 * AgentConfigPublishRequestDTO DTO，用于接口层数据传输。
 */

@Data
public class AgentConfigPublishRequestDTO {

    private String agentId;
    private String operator;

}


