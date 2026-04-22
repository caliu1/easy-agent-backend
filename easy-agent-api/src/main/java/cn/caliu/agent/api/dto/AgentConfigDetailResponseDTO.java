package cn.caliu.agent.api.dto;

import lombok.Data;

@Data
public class AgentConfigDetailResponseDTO {

    private String agentId;
    private String appName;
    private String agentName;
    private String agentDesc;
    private String configJson;
    private String status;
    private Long currentVersion;
    private Long publishedVersion;
    private String operator;
    private String ownerUserId;
    private String sourceType;
    private String plazaStatus;
    private Long plazaPublishTime;
    private Long createTime;
    private Long updateTime;

}
