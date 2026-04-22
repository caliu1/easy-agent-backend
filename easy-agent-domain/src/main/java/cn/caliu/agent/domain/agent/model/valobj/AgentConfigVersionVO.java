package cn.caliu.agent.domain.agent.model.valobj;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Agent 配置版本快照值对象。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AgentConfigVersionVO {

    /**
     * 业务主键：Agent ID。
     */
    private String agentId;
    /**
     * 版本号。
     */
    private Long version;
    /**
     * 该版本的状态（DRAFT / PUBLISHED / OFFLINE）。
     */
    private String status;
    /**
     * 该版本对应的完整配置 JSON。
     */
    private String configJson;
    /**
     * 操作人。
     */
    private String operator;
    /**
     * 快照创建时间（毫秒时间戳）。
     */
    private Long createTime;

}

