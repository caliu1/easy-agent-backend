package cn.caliu.agent.api.dto.agent.config.request;

import lombok.Data;
/**
 * AgentConfigSubscribeRequestDTO DTO，用于接口层数据传输。
 */

@Data
public class AgentConfigSubscribeRequestDTO {

    private String userId;
    private String agentId;

}

