package cn.caliu.agent.api.dto.user.auth.response;

import lombok.Data;
/**
 * UserAuthResponseDTO DTO，用于接口层数据传输。
 */

@Data
public class UserAuthResponseDTO {

    private String userId;
    private String nickname;
    private String token;

}


