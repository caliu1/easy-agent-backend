package cn.caliu.agent.api.dto.agent.chat.response;

import lombok.Data;

@Data
public class SessionHistoryMessageResponseDTO {

    private Long id;
    private String sessionId;
    private String agentId;
    private String userId;
    private String role;
    private String content;
    private Long createTime;

}

