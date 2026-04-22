package cn.caliu.agent.api.dto;

import lombok.Data;

@Data
public class UserRegisterRequestDTO {

    private String userId;
    private String password;
    private String nickname;

}

