package cn.caliu.agent.api.dto.user.auth.request;

import lombok.Data;

@Data
public class UserLoginRequestDTO {

    private String userId;
    private String password;

}


