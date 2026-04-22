package cn.caliu.agent.api.dto;

import lombok.Data;

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
