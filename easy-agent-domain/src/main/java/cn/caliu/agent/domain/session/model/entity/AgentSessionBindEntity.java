package cn.caliu.agent.domain.session.model.entity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.apache.commons.lang3.StringUtils;
/**
 * AgentSessionBindEntity 类。
 */

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AgentSessionBindEntity {

    private String sessionId;
    private String agentId;
    private Long configVersion;
    private String userId;
    private Long createTime;
    private Long updateTime;

    public static AgentSessionBindEntity create(String sessionId, String agentId, Long configVersion, String userId) {
        return AgentSessionBindEntity.builder()
                .sessionId(StringUtils.trimToEmpty(sessionId))
                .agentId(StringUtils.trimToEmpty(agentId))
                .configVersion(configVersion == null ? 0L : configVersion)
                .userId(StringUtils.trimToEmpty(userId))
                .build();
    }

}
