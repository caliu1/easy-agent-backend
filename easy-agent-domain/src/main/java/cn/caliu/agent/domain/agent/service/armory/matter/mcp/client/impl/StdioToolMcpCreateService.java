package cn.caliu.agent.domain.agent.service.armory.matter.mcp.client.impl;

import cn.caliu.agent.domain.agent.model.valobj.AiAgentConfigTableVO;
import cn.caliu.agent.domain.agent.service.armory.matter.mcp.client.IToolMcpCreateService;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.modelcontextprotocol.client.McpClient;
import io.modelcontextprotocol.client.McpSyncClient;
import io.modelcontextprotocol.client.transport.ServerParameters;
import io.modelcontextprotocol.client.transport.StdioClientTransport;
import io.modelcontextprotocol.json.McpJsonMapper;
import io.modelcontextprotocol.spec.McpSchema;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.mcp.SyncMcpToolCallbackProvider;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.stereotype.Service;

import java.time.Duration;
/**
 * StdioToolMcpCreateService 类。
 */

@Slf4j
@Service
public class StdioToolMcpCreateService implements IToolMcpCreateService {
    @Override
    public ToolCallback[] buildToolCallback(AiAgentConfigTableVO.Module.ChatModel.ToolMcp toolMcp) {
        AiAgentConfigTableVO.Module.ChatModel.ToolMcp.StdioServerParameters stdioConfig = toolMcp.getStdio();

        AiAgentConfigTableVO.Module.ChatModel.ToolMcp.StdioServerParameters.ServerParameters serverParameters = stdioConfig.getServerParameters();

        ServerParameters stdioParams = ServerParameters.builder(serverParameters.getCommand())
                .args(serverParameters.getArgs())
                .env(serverParameters.getEnv())
                .build();

        McpSyncClient mcpSyncClient = McpClient.sync(new StdioClientTransport(stdioParams, createJsonMapper()))
                .requestTimeout(Duration.ofSeconds(stdioConfig.getRequestTimeout()))
                .build();

        McpSchema.InitializeResult initialize = mcpSyncClient.initialize();

        log.info("tool stdio mcp initialize {}", initialize);

        return SyncMcpToolCallbackProvider.builder()
                .mcpClients(mcpSyncClient).build()
                .getToolCallbacks();
    }

    /**
     * 同时兼容 mcp-json-jackson2 / mcp-json-jackson3 两套包结构。
     */
    private McpJsonMapper createJsonMapper() {
        try {
            Class<?> jackson3MapperClass = Class.forName("io.modelcontextprotocol.json.jackson3.JacksonMcpJsonMapper");
            Class<?> jsonMapperClass = Class.forName("tools.jackson.databind.json.JsonMapper");
            Object jsonMapper = jsonMapperClass.getDeclaredConstructor().newInstance();
            return (McpJsonMapper) jackson3MapperClass
                    .getDeclaredConstructor(jsonMapperClass)
                    .newInstance(jsonMapper);
        } catch (ClassNotFoundException ignored) {
            // ignore and fallback to jackson2
        } catch (Exception e) {
            throw new RuntimeException("create jackson3 mcp json mapper failed", e);
        }

        try {
            Class<?> jackson2MapperClass = Class.forName("io.modelcontextprotocol.json.jackson.JacksonMcpJsonMapper");
            return (McpJsonMapper) jackson2MapperClass
                    .getDeclaredConstructor(ObjectMapper.class)
                    .newInstance(new ObjectMapper());
        } catch (Exception e) {
            throw new RuntimeException("create jackson2 mcp json mapper failed", e);
        }
    }
}
