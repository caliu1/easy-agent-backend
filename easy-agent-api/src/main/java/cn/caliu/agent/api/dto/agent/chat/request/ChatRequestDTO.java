package cn.caliu.agent.api.dto.agent.chat.request;

import lombok.Data;
/**
 * ChatRequestDTO DTO，用于接口层数据传输。
 */

@Data
public class ChatRequestDTO {

    private String agentId;
    private String userId;
    private String sessionId;
    private String message;

}

