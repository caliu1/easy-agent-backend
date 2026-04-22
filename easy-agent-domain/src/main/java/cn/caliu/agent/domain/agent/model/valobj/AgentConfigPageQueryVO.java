package cn.caliu.agent.domain.agent.model.valobj;

import lombok.Builder;
import lombok.Data;

/**
 * Agent 配置分页查询条件。
 */
@Data
@Builder
public class AgentConfigPageQueryVO {

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
