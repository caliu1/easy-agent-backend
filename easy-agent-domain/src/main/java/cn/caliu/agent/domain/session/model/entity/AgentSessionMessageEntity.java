package cn.caliu.agent.domain.session.model.entity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.apache.commons.lang3.StringUtils;
/**
 * AgentSessionMessageEntity 类。
 */

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AgentSessionMessageEntity {

    public static final String ROLE_USER = "user";
    public static final String ROLE_ASSISTANT = "assistant";
    public static final String ROLE_SYSTEM = "system";
    public static final String ROLE_TOOL = "tool";

    private Long id;
    private String sessionId;
    private String agentId;
    private String userId;
    private String role;
    private String content;
    private Long createTime;

    public static AgentSessionMessageEntity create(String sessionId, String agentId, String userId, String role, String content) {
        return AgentSessionMessageEntity.builder()
                .sessionId(StringUtils.trimToEmpty(sessionId))
                .agentId(StringUtils.trimToEmpty(agentId))
                .userId(StringUtils.trimToEmpty(userId))
                .role(StringUtils.trimToEmpty(role))
                .content(StringUtils.defaultString(content))
                .build();
    }

}

