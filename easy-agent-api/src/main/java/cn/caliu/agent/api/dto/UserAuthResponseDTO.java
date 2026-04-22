package cn.caliu.agent.api.dto;

import lombok.Data;

@Data
public class UserAuthResponseDTO {

    private String userId;
    private String nickname;
    private String token;

}

