package cn.caliu.agent.api.dto.agent.chat.event;

import lombok.Data;

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

