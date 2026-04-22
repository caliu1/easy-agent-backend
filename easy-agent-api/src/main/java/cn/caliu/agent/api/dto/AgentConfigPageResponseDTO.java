package cn.caliu.agent.api.dto;

import lombok.Data;

import java.util.List;

/**
 * Agent 配置分页查询响应。
 */
@Data
public class AgentConfigPageResponseDTO {

    private Long pageNo;
    private Long pageSize;
    private Long total;
    private List<AgentConfigSummaryResponseDTO> records;

}

