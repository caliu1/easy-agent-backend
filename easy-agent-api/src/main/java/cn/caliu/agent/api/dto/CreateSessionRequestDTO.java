package cn.caliu.agent.api.dto;

import lombok.Data;

@Data
public class CreateSessionRequestDTO {

    private String agentId;

    private String userId;

}
