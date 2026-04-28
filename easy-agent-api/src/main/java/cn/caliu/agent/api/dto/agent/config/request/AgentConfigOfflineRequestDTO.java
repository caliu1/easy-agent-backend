package cn.caliu.agent.api.dto.agent.config.request;

import lombok.Data;
/**
 * AgentConfigOfflineRequestDTO DTO，用于接口层数据传输。
 */

@Data
public class AgentConfigOfflineRequestDTO {

    private String agentId;
    private String operator;

}


