package cn.caliu.agent.api.dto.agent.config.request;

import lombok.Data;

/**
 * Agent 閰嶇疆鍒嗛〉/鏉′欢鏌ヨ璇锋眰銆? */
@Data
public class AgentConfigPageQueryRequestDTO {

    private String agentId;
    private String appName;
    private String agentName;
    private String status;
    private String operator;
    private String ownerUserId;
    private String sourceType;
    private String plazaStatus;
    private Long pageNo;
    private Long pageSize;

}

