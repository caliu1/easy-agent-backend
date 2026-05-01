package cn.caliu.agent.domain.agent.service.armory.matter.mcp.client.impl;

import cn.caliu.agent.domain.agent.model.valobj.AiAgentConfigTableVO;
import cn.caliu.agent.domain.agent.service.armory.matter.mcp.client.IToolMcpCreateService;
import io.modelcontextprotocol.client.McpClient;
import io.modelcontextprotocol.client.McpSyncClient;
import io.modelcontextprotocol.client.transport.HttpClientStreamableHttpTransport;
import io.modelcontextprotocol.spec.McpSchema;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.ai.mcp.SyncMcpToolCallbackProvider;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.stereotype.Service;

import java.net.URL;
import java.net.URLEncoder;
import java.net.http.HttpRequest;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

/**
 * streamableHttp MCP tool callback assembler.
 */
@Slf4j
@Service
public class StreamableHttpToolMcpCreateService implements IToolMcpCreateService {

    @Override
    public ToolCallback[] buildToolCallback(AiAgentConfigTableVO.Module.ChatModel.ToolMcp toolMcp) throws Exception {
        AiAgentConfigTableVO.Module.ChatModel.ToolMcp.StreamableHttpServerParameters config =
                toolMcp.getStreamableHttp();

        String originalBaseUri = config.getBaseUri();
        String baseUri = originalBaseUri;
        String endpoint = config.getEndpoint();

        if (StringUtils.isBlank(endpoint)) {
            URL url = new URL(originalBaseUri);
            String protocol = url.getProtocol();
            String host = url.getHost();
            int port = url.getPort();
            String baseUrl = port == -1 ? protocol + "://" + host : protocol + "://" + host + ":" + port;

            int index = originalBaseUri.indexOf(baseUrl);
            if (index != -1) {
                endpoint = originalBaseUri.substring(index + baseUrl.length());
            }
            baseUri = baseUrl;
        }

        endpoint = StringUtils.isBlank(endpoint) ? "/mcp" : endpoint;
        endpoint = appendQuery(endpoint, config.getQuery());

        Integer timeout = config.getRequestTimeout();
        if (timeout == null || timeout <= 0) {
            timeout = 3000;
        }

        HttpClientStreamableHttpTransport.Builder transportBuilder = HttpClientStreamableHttpTransport
                .builder(baseUri)
                .endpoint(endpoint)
                // Some third-party streamableHttp MCP servers do not support long-lived SSE back-channel.
                // Disable resumable stream hints to reduce noisy reconnect attempts and warning logs.
                .resumableStreams(false)
                .openConnectionOnStartup(false);

        Map<String, String> headers = buildHeaders(config.getHeaders(), config.getAuth());
        if (!headers.isEmpty()) {
            HttpRequest.Builder requestBuilder = HttpRequest.newBuilder();
            headers.forEach(requestBuilder::header);
            transportBuilder.requestBuilder(requestBuilder);
        }

        HttpClientStreamableHttpTransport transport = transportBuilder.build();

        McpSyncClient mcpSyncClient = McpClient
                .sync(transport)
                .requestTimeout(Duration.ofMillis(timeout))
                .build();

        McpSchema.InitializeResult initialize = mcpSyncClient.initialize();
        log.info("tool streamable-http mcp initialize {}", initialize);

        return SyncMcpToolCallbackProvider.builder()
                .mcpClients(mcpSyncClient)
                .build()
                .getToolCallbacks();
    }

    private Map<String, String> buildHeaders(
            Map<String, String> headers,
            AiAgentConfigTableVO.Module.ChatModel.ToolMcp.AuthParameters auth
    ) {
        Map<String, String> merged = new LinkedHashMap<>();
        if (headers != null) {
            headers.forEach((key, value) -> {
                if (StringUtils.isNotBlank(key) && value != null) {
                    merged.put(key.trim(), value);
                }
            });
        }

        if (auth == null || StringUtils.isBlank(auth.getType())) {
            return merged;
        }

        String type = auth.getType().trim().toLowerCase(Locale.ROOT).replace("-", "");
        if ("bearer".equals(type) && StringUtils.isNotBlank(auth.getToken())) {
            merged.putIfAbsent("Authorization", "Bearer " + auth.getToken().trim());
            return merged;
        }

        if ("apikey".equals(type) && StringUtils.isNotBlank(auth.getToken())) {
            String keyName = StringUtils.defaultIfBlank(auth.getKeyName(), "X-API-Key");
            merged.putIfAbsent(keyName.trim(), auth.getToken().trim());
        }

        return merged;
    }

    private String appendQuery(String endpoint, Map<String, String> query) {
        if (query == null || query.isEmpty()) {
            return endpoint;
        }

        String result = StringUtils.defaultString(endpoint);
        StringBuilder builder = new StringBuilder(result);
        char joiner = result.contains("?") ? '&' : '?';

        for (Map.Entry<String, String> entry : query.entrySet()) {
            if (StringUtils.isBlank(entry.getKey()) || entry.getValue() == null) {
                continue;
            }
            builder.append(joiner)
                    .append(URLEncoder.encode(entry.getKey().trim(), StandardCharsets.UTF_8))
                    .append('=')
                    .append(URLEncoder.encode(entry.getValue(), StandardCharsets.UTF_8));
            joiner = '&';
        }

        return builder.toString();
    }
}
