package cn.caliu.agent.api.dto.user.auth.request;

import lombok.Data;
/**
 * UserRegisterRequestDTO DTO，用于接口层数据传输。
 */

@Data
public class UserRegisterRequestDTO {

    private String userId;
    private String password;
    private String nickname;

}


