package cn.caliu.agent.infrastructure.persistent.po;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;
/**
 * UserAccountPO 持久化对象，对应数据库表结构。
 */

@Data
@TableName("ai_user_account")
public class UserAccountPO {

    @TableId(value = "id", type = IdType.AUTO)
    private Long id;
    @TableField("user_id")
    private String userId;
    @TableField("nickname")
    private String nickname;
    @TableField("password_hash")
    private String passwordHash;
    @TableField("password_salt")
    private String passwordSalt;
    @TableField("status")
    private String status;
    @TableField("last_login_time")
    private LocalDateTime lastLoginTime;
    @TableField("is_deleted")
    private Integer isDeleted;
    @TableField("create_time")
    private LocalDateTime createTime;
    @TableField("update_time")
    private LocalDateTime updateTime;

}

