package cn.caliu.agent.domain.agent.model.valobj;

import lombok.Builder;
import lombok.Data;

import java.util.List;

/**
 * Agent 配置分页查询结果。
 */
@Data
@Builder
public class AgentConfigPageResultVO {

    private Long pageNo;
    private Long pageSize;
    private Long total;
    private List<AgentConfigManageVO> records;

}

