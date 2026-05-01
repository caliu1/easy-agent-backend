package cn.caliu.agent.domain.agent.model.entity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * User MCP profile entity.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AgentMcpProfileEntity {

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
     * Full MCP json configuration.
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
    /**
     * bearer token / apiKey value
     */
    private String authToken;
    /**
     * header key when authType=apiKey
     */
    private String authKeyName;
    /**
     * JSON object string, e.g. {"Authorization":"Bearer xxx"}
     */
    private String headersJson;
    /**
     * JSON object string, e.g. {"api_key":"xxx"}
     */
    private String queryJson;

    private Long createTime;
    private Long updateTime;
}
