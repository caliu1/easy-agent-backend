package cn.caliu.agent.infrastructure.persistent.po;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * ai_agent_config 持久化对象
 */
@Data
@TableName("ai_agent_config")
public class AgentConfigPO {

    @TableId(value = "id", type = IdType.AUTO)
    private Long id;
    @TableField("agent_id")
    private String agentId;
    @TableField("app_name")
    private String appName;
    @TableField("agent_name")
    private String agentName;
    @TableField("agent_desc")
    private String agentDesc;
    @TableField("config_json")
    private String configJson;
    @TableField("status")
    private String status;
    @TableField("current_version")
    private Long currentVersion;
    @TableField("published_version")
    private Long publishedVersion;
    @TableField("operator")
    private String operator;
    @TableField("owner_user_id")
    private String ownerUserId;
    @TableField("source_type")
    private String sourceType;
    @TableField("plaza_status")
    private String plazaStatus;
    @TableField("plaza_publish_time")
    private LocalDateTime plazaPublishTime;
    @TableField("is_deleted")
    private Integer isDeleted;
    @TableField("create_time")
    private LocalDateTime createTime;
    @TableField("update_time")
    private LocalDateTime updateTime;

}
