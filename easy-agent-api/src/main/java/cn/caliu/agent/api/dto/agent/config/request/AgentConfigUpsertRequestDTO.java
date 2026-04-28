package cn.caliu.agent.api.dto.agent.config.request;

import lombok.Data;
/**
 * AgentConfigUpsertRequestDTO DTO，用于接口层数据传输。
 */

@Data
public class AgentConfigUpsertRequestDTO {

    private String agentId;
    private String appName;
    private String agentName;
    private String agentDesc;
    /**
     * Serialized JSON payload for dynamic agent configuration.
     */
    private String configJson;
    private String operator;
    private String ownerUserId;
    private String sourceType;
    private String plazaStatus;

}

