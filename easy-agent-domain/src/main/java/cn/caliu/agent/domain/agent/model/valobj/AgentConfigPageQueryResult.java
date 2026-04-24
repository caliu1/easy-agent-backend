package cn.caliu.agent.domain.agent.model.valobj;

import cn.caliu.agent.domain.agent.model.entity.AgentConfigEntity;
import lombok.Builder;
import lombok.Data;

import java.util.List;

/**
 * Agent config page query result.
 */
@Data
@Builder
public class AgentConfigPageQueryResult {

    private Long pageNo;
    private Long pageSize;
    private Long total;
    private List<AgentConfigEntity> records;

}