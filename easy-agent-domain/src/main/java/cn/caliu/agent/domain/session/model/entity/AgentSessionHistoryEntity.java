package cn.caliu.agent.domain.session.model.entity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.apache.commons.lang3.StringUtils;
/**
 * AgentSessionHistoryEntity 类。
 */

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AgentSessionHistoryEntity {

    private String sessionId;
    private String agentId;
    private String userId;
    private String sessionTitle;
    private String latestMessage;
    private Long messageCount;
    private Long totalTokens;
    private Long createTime;
    private Long updateTime;

    public static AgentSessionHistoryEntity createEmpty(String sessionId, String agentId, String userId) {
        return AgentSessionHistoryEntity.builder()
                .sessionId(StringUtils.trimToEmpty(sessionId))
                .agentId(StringUtils.trimToEmpty(agentId))
                .userId(StringUtils.trimToEmpty(userId))
                .sessionTitle("")
                .latestMessage("")
                .messageCount(0L)
                .totalTokens(0L)
                .build();
    }

}
