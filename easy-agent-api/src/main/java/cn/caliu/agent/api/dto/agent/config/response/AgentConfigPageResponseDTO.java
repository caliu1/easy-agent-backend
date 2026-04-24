package cn.caliu.agent.api.dto.agent.config.response;

import lombok.Data;

import java.util.List;

/**
 * Agent 閰嶇疆鍒嗛〉鏌ヨ鍝嶅簲銆? */
@Data
public class AgentConfigPageResponseDTO {

    private Long pageNo;
    private Long pageSize;
    private Long total;
    private List<AgentConfigSummaryResponseDTO> records;

}


