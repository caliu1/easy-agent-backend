package cn.caliu.agent.domain.agent.model.valobj;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 会话与 Agent 版本绑定关系值对象。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AgentSessionBindVO {

    /**
     * 会话 ID。
     */
    private String sessionId;
    /**
     * Agent ID。
     */
    private String agentId;
    /**
     * 该会话绑定的配置版本号。
     */
    private Long configVersion;
    /**
     * 用户 ID。
     */
    private String userId;
    /**
     * 创建时间（毫秒时间戳）。
     */
    private Long createTime;
    /**
     * 更新时间（毫秒时间戳）。
     */
    private Long updateTime;

}

