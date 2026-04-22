package cn.caliu.agent.infrastructure.persistent.po;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * ai_agent_session_bind 持久化对象
 */
@Data
@TableName("ai_agent_session_bind")
public class AgentSessionBindPO {

    @TableId(value = "id", type = IdType.AUTO)
    private Long id;
    @TableField("session_id")
    private String sessionId;
    @TableField("agent_id")
    private String agentId;
    @TableField("config_version")
    private Long configVersion;
    @TableField("user_id")
    private String userId;
    @TableField("create_time")
    private LocalDateTime createTime;
    @TableField("update_time")
    private LocalDateTime updateTime;

}
