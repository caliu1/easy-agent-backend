package cn.caliu.agent.api.dto.agent.chat.event;

import lombok.Data;
/**
 * ChatStreamEventResponseDTO DTO，用于接口层数据传输。
 */

@Data
public class ChatStreamEventResponseDTO {

    /**
     * thinking | route | reply | final | error
     */
    private String type;

    private String agentName;

    private String content;

    private String routeTarget;

    private Boolean partial;

    private Boolean finalResponse;

}

