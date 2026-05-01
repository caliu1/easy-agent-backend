package cn.caliu.agent.infrastructure.persistent.po;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * ai_agent_mcp_profile 持久化对象。
 */
@Data
@TableName("ai_agent_mcp_profile")
public class AgentMcpProfilePO {

    @TableId(value = "id", type = IdType.AUTO)
    private Long id;
    @TableField("user_id")
    private String userId;
    @TableField("mcp_type")
    private String mcpType;
    @TableField("mcp_name")
    private String mcpName;
    @TableField("mcp_desc")
    private String mcpDesc;
    @TableField("config_json")
    private String configJson;
    @TableField("is_deleted")
    private Integer isDeleted;
    @TableField("create_time")
    private LocalDateTime createTime;
    @TableField("update_time")
    private LocalDateTime updateTime;

}
