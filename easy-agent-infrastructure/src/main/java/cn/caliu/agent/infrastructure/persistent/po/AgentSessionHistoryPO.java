package cn.caliu.agent.infrastructure.persistent.po;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;
/**
 * AgentSessionHistoryPO 持久化对象，对应数据库表结构。
 */

@Data
@TableName("ai_agent_session_history")
public class AgentSessionHistoryPO {

    @TableId(value = "id", type = IdType.AUTO)
    private Long id;
    @TableField("session_id")
    private String sessionId;
    @TableField("agent_id")
    private String agentId;
    @TableField("user_id")
    private String userId;
    @TableField("session_title")
    private String sessionTitle;
    @TableField("latest_message")
    private String latestMessage;
    @TableField("message_count")
    private Long messageCount;
    @TableField("total_tokens")
    private Long totalTokens;
    @TableField("create_time")
    private LocalDateTime createTime;
    @TableField("update_time")
    private LocalDateTime updateTime;

}
