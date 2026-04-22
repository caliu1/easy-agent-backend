package cn.caliu.agent.domain.agent.model.valobj;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserAccountVO {

    private String userId;
    private String nickname;
    private String passwordHash;
    private String passwordSalt;
    private String status;
    private Integer isDeleted;
    private Long lastLoginTime;
    private Long createTime;
    private Long updateTime;

}

