package cn.caliu.agent.api.dto.agent.config.response;

import lombok.Data;
/**
 * AgentConfigSummaryResponseDTO DTO，用于接口层数据传输。
 */

@Data
public class AgentConfigSummaryResponseDTO {

    private String agentId;
    private String appName;
    private String agentName;
    private String agentDesc;
    private String status;
    private Long currentVersion;
    private Long publishedVersion;
    private String ownerUserId;
    private String sourceType;
    private String plazaStatus;
    private Long plazaPublishTime;
    private Long updateTime;

}

