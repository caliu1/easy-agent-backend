package cn.caliu.agent.api.dto.user.auth.request;

import lombok.Data;

@Data
public class UserRegisterRequestDTO {

    private String userId;
    private String password;
    private String nickname;

}


