package cn.caliu.agent.api.dto.agent.config.response;

import lombok.Data;

/**
 * MCP profile response.
 */
@Data
public class AgentMcpProfileResponseDTO {

    private Long id;
    private String userId;

    /**
     * sse | streamableHttp
     */
    private String type;

    private String name;
    private String description;
    private String baseUri;
    /**
     * Raw MCP json config saved in DB.
     */
    private String configJson;

    /**
     * sseEndpoint (SSE) / endpoint (streamableHttp)
     */
    private String sseEndpoint;

    private Integer requestTimeout;

    /**
     * none | bearer | apiKey
     */
    private String authType;
    private String authToken;
    private String authKeyName;
    private String headersJson;
    private String queryJson;

    private Long createTime;
    private Long updateTime;
}
