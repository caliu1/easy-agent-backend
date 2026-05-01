package cn.caliu.agent.api.dto.agent.config.request;

import lombok.Data;

/**
 * MCP 配置档案删除请求。
 */
@Data
public class AgentMcpProfileDeleteRequestDTO {

    private Long id;
    private String userId;

}

