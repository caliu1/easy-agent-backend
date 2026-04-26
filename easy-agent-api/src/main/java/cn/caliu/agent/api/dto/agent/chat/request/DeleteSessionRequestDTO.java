package cn.caliu.agent.api.dto.agent.chat.request;

import lombok.Data;

@Data
public class DeleteSessionRequestDTO {

    private String sessionId;

    private String userId;

}

