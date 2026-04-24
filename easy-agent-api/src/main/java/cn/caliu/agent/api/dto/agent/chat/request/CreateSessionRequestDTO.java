package cn.caliu.agent.api.dto.agent.chat.request;

import lombok.Data;

@Data
public class CreateSessionRequestDTO {

    private String agentId;

    private String userId;

}

