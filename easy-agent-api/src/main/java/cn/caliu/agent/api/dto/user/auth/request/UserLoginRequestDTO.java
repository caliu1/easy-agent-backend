package cn.caliu.agent.api.dto.user.auth.request;

import lombok.Data;
/**
 * UserLoginRequestDTO DTO，用于接口层数据传输。
 */

@Data
public class UserLoginRequestDTO {

    private String userId;
    private String password;

}


