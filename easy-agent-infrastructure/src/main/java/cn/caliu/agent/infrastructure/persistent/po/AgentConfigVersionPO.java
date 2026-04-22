package cn.caliu.agent.infrastructure.persistent.po;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * ai_agent_config_version 持久化对象
 */
@Data
@TableName("ai_agent_config_version")
public class AgentConfigVersionPO {

    @TableId(value = "id", type = IdType.AUTO)
    private Long id;
    @TableField("agent_id")
    private String agentId;
    @TableField("version")
    private Long version;
    @TableField("status")
    private String status;
    @TableField("config_json")
    private String configJson;
    @TableField("operator")
    private String operator;
    @TableField("create_time")
    private LocalDateTime createTime;

}
