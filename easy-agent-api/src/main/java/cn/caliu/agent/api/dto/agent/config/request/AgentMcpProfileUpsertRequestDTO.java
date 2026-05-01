package cn.caliu.agent.api.dto.agent.config.request;

import lombok.Data;

/**
 * MCP profile create/update request.
 */
@Data
public class AgentMcpProfileUpsertRequestDTO {

    /**
     * Required for update; optional for create.
     */
    private Long id;

    private String userId;

    /**
     * Full MCP json config.
     * Preferred payload shape:
     * {
     *   "mcpServers": {
     *     "server-name": {
     *       "type": "sse|streamableHttp",
     *       "url": "https://host/path",
     *       "headers": {},
     *       "query": {},
     *       "auth": {"type":"none|bearer|apiKey","token":"","keyName":""},
     *       "requestTimeout": 3000
     *     }
     *   }
     * }
     */
    private String configJson;

    /**
     * sse | streamableHttp
     */
    private String type;

    private String name;
    private String description;
    private String baseUri;

    /**
     * sseEndpoint (SSE) / endpoint (streamableHttp)
     */
    private String sseEndpoint;

    private Integer requestTimeout;

    /**
     * none | bearer | apiKey
     */
    private String authType;

    /**
     * bearer token / apiKey value
     */
    private String authToken;

    /**
     * header key when authType=apiKey
     */
    private String authKeyName;

    /**
     * JSON object string for headers
     */
    private String headersJson;

    /**
     * JSON object string for query params
     */
    private String queryJson;
}
