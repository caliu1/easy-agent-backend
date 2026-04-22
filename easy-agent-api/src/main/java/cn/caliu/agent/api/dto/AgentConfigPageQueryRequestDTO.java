package cn.caliu.agent.api.dto;

import lombok.Data;

/**
 * Agent 配置分页/条件查询请求。
 */
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
