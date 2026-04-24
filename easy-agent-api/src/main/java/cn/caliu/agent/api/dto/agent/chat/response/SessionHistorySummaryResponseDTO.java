package cn.caliu.agent.api.dto.agent.chat.response;

import lombok.Data;

@Data
public class SessionHistorySummaryResponseDTO {

    private String sessionId;
    private String agentId;
    private String userId;
    private String sessionTitle;
    private String latestMessage;
    private Long messageCount;
    private Long totalTokens;
    private Long createTime;
    private Long updateTime;

}
