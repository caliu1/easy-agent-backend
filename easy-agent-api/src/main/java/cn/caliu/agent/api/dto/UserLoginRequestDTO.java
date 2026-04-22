package cn.caliu.agent.api.dto;

import lombok.Data;

@Data
public class UserLoginRequestDTO {

    private String userId;
    private String password;

}

