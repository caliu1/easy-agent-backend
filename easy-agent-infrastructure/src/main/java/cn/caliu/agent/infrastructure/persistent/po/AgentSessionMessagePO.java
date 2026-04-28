package cn.caliu.agent.infrastructure.persistent.po;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;
/**
 * AgentSessionMessagePO 持久化对象，对应数据库表结构。
 */

@Data
@TableName("ai_agent_session_message")
public class AgentSessionMessagePO {

    @TableId(value = "id", type = IdType.AUTO)
    private Long id;
    @TableField("session_id")
    private String sessionId;
    @TableField("agent_id")
    private String agentId;
    @TableField("user_id")
    private String userId;
    @TableField("role")
    private String role;
    @TableField("content")
    private String content;
    @TableField("create_time")
    private LocalDateTime createTime;

}

