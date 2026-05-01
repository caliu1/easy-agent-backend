package cn.caliu.agent.domain.agent.service.config;

import cn.caliu.agent.domain.agent.model.entity.AgentMcpProfileEntity;
import cn.caliu.agent.domain.agent.model.entity.AgentSkillProfileEntity;
import cn.caliu.agent.domain.agent.repository.IAgentMcpProfileRepository;
import cn.caliu.agent.domain.agent.repository.IAgentSkillProfileRepository;
import cn.caliu.agent.domain.agent.service.IAgentToolProfileManageService;
import cn.caliu.agent.types.enums.ResponseCode;
import cn.caliu.agent.types.exception.AppException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.modelcontextprotocol.client.McpClient;
import io.modelcontextprotocol.client.McpSyncClient;
import io.modelcontextprotocol.client.transport.HttpClientSseClientTransport;
import io.modelcontextprotocol.client.transport.HttpClientStreamableHttpTransport;
import io.modelcontextprotocol.spec.McpSchema;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.Resource;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URLDecoder;
import java.net.URLEncoder;
import java.net.http.HttpRequest;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * MCP / Skill 管理服务实现。
 *
 * MCP 配置采用 config_json 作为主存字段，同时保留 mcp_type/mcp_name/base_uri 作为检索索引列。
 */
@Slf4j
@Service
public class IAgentToolProfileManageServiceImpl implements IAgentToolProfileManageService {

    private static final String MCP_TYPE_SSE = "sse";
    private static final String MCP_TYPE_STREAMABLE_HTTP = "streamableHttp";
    private static final String SKILL_ROOT_PREFIX = "easyagent/skills/";
    private static final String LEGACY_SKILL_ROOT_PREFIX = "skills/";
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    @Resource
    private IAgentMcpProfileRepository agentMcpProfileRepository;
    @Resource
    private IAgentSkillProfileRepository agentSkillProfileRepository;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public AgentMcpProfileEntity createMcpProfile(AgentMcpProfileEntity request) {
        validateMcpRequest(request, false);
        AgentMcpProfileEntity candidate = normalizeMcpRequest(request, null);
        if (agentMcpProfileRepository.existsByMcpName(candidate.getUserId(), candidate.getName(), candidate.getType(), null)) {
            throw new AppException(ResponseCode.ILLEGAL_PARAMETER.getCode(), "mcp profile already exists");
        }
        agentMcpProfileRepository.insert(candidate);
        return requireMcp(candidate.getId(), candidate.getUserId());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public AgentMcpProfileEntity updateMcpProfile(AgentMcpProfileEntity request) {
        validateMcpRequest(request, true);
        AgentMcpProfileEntity existed = requireMcp(request.getId(), request.getUserId());
        AgentMcpProfileEntity candidate = normalizeMcpRequest(request, existed);
        if (agentMcpProfileRepository.existsByMcpName(candidate.getUserId(), candidate.getName(), candidate.getType(), request.getId())) {
            throw new AppException(ResponseCode.ILLEGAL_PARAMETER.getCode(), "mcp profile already exists");
        }
        agentMcpProfileRepository.update(candidate);
        return requireMcp(request.getId(), request.getUserId());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean deleteMcpProfile(Long id, String userId) {
        if (id == null || id <= 0) {
            throw new AppException(ResponseCode.ILLEGAL_PARAMETER.getCode(), "id is invalid");
        }
        if (StringUtils.isBlank(userId)) {
            throw new AppException(ResponseCode.ILLEGAL_PARAMETER.getCode(), "userId is blank");
        }
        return agentMcpProfileRepository.softDelete(id, userId.trim());
    }

    @Override
    public List<AgentMcpProfileEntity> queryMcpProfileList(String userId) {
        if (StringUtils.isBlank(userId)) {
            throw new AppException(ResponseCode.ILLEGAL_PARAMETER.getCode(), "userId is blank");
        }
        List<AgentMcpProfileEntity> rawList = agentMcpProfileRepository.queryByUserId(userId.trim());
        if (rawList == null || rawList.isEmpty()) {
            return new ArrayList<>();
        }
        List<AgentMcpProfileEntity> result = new ArrayList<>();
        for (AgentMcpProfileEntity raw : rawList) {
            try {
                AgentMcpProfileEntity hydrated = hydrateMcpEntity(raw);
                if (hydrated != null && isSupportedMcpType(hydrated.getType())) {
                    result.add(hydrated);
                }
            } catch (Exception e) {
                log.warn("skip invalid mcp profile, id={}, reason={}", raw == null ? null : raw.getId(), e.getMessage());
            }
        }
        return result;
    }

    @Override
    public boolean testMcpProfileConnection(AgentMcpProfileEntity request) {
        validateMcpConnectionTestRequest(request);
        AgentMcpProfileEntity candidate = normalizeMcpConnectionTestRequest(request);

        try {
            if (MCP_TYPE_STREAMABLE_HTTP.equals(candidate.getType())) {
                testStreamableHttpConnection(candidate);
            } else {
                testSseConnection(candidate);
            }
            return true;
        } catch (AppException e) {
            throw e;
        } catch (Exception e) {
            throw new AppException(
                    ResponseCode.ILLEGAL_PARAMETER.getCode(),
                    "mcp connection test failed: " + resolveExceptionMessage(e)
            );
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public AgentSkillProfileEntity createSkillProfile(AgentSkillProfileEntity request) {
        validateSkillRequest(request, false);
        if (agentSkillProfileRepository.existsBySkillName(request.getUserId().trim(), request.getSkillName().trim(), null)) {
            throw new AppException(ResponseCode.ILLEGAL_PARAMETER.getCode(), "skill name already exists");
        }
        AgentSkillProfileEntity candidate = normalizeSkillRequest(request, null);
        agentSkillProfileRepository.insert(candidate);
        return requireSkill(candidate.getId(), candidate.getUserId());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public AgentSkillProfileEntity updateSkillProfile(AgentSkillProfileEntity request) {
        validateSkillRequest(request, true);
        AgentSkillProfileEntity existed = requireSkill(request.getId(), request.getUserId());
        if (agentSkillProfileRepository.existsBySkillName(request.getUserId().trim(), request.getSkillName().trim(), request.getId())) {
            throw new AppException(ResponseCode.ILLEGAL_PARAMETER.getCode(), "skill name already exists");
        }
        AgentSkillProfileEntity candidate = normalizeSkillRequest(request, existed);
        agentSkillProfileRepository.update(candidate);
        return requireSkill(request.getId(), request.getUserId());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean deleteSkillProfile(Long id, String userId) {
        if (id == null || id <= 0) {
            throw new AppException(ResponseCode.ILLEGAL_PARAMETER.getCode(), "id is invalid");
        }
        if (StringUtils.isBlank(userId)) {
            throw new AppException(ResponseCode.ILLEGAL_PARAMETER.getCode(), "userId is blank");
        }
        return agentSkillProfileRepository.softDelete(id, userId.trim());
    }

    @Override
    public List<AgentSkillProfileEntity> querySkillProfileList(String userId) {
        if (StringUtils.isBlank(userId)) {
            throw new AppException(ResponseCode.ILLEGAL_PARAMETER.getCode(), "userId is blank");
        }
        return agentSkillProfileRepository.queryByUserId(userId.trim());
    }

    private void validateMcpRequest(AgentMcpProfileEntity request, boolean update) {
        if (request == null) {
            throw new AppException(ResponseCode.ILLEGAL_PARAMETER.getCode(), "request is null");
        }
        if (update && (request.getId() == null || request.getId() <= 0)) {
            throw new AppException(ResponseCode.ILLEGAL_PARAMETER.getCode(), "id is invalid");
        }
        if (StringUtils.isBlank(request.getUserId())) {
            throw new AppException(ResponseCode.ILLEGAL_PARAMETER.getCode(), "userId is blank");
        }
        resolveMcpConfigFromRequest(request);
    }

    private AgentMcpProfileEntity normalizeMcpRequest(AgentMcpProfileEntity request, AgentMcpProfileEntity existed) {
        ParsedMcpConfig parsed = resolveMcpConfigFromRequest(request);
        return AgentMcpProfileEntity.builder()
                .id(existed == null ? request.getId() : existed.getId())
                .userId(request.getUserId().trim())
                .type(parsed.type)
                .name(parsed.name)
                .description(parsed.description)
                .baseUri(parsed.baseUri)
                .configJson(parsed.configJson)
                .sseEndpoint(parsed.endpoint)
                .requestTimeout(parsed.requestTimeout)
                .authType(parsed.authType)
                .authToken(parsed.authToken)
                .authKeyName(parsed.authKeyName)
                .headersJson(mapToJson(parsed.headers))
                .queryJson(mapToJson(parsed.query))
                .createTime(existed == null ? request.getCreateTime() : existed.getCreateTime())
                .updateTime(request.getUpdateTime())
                .build();
    }

    private AgentMcpProfileEntity normalizeMcpConnectionTestRequest(AgentMcpProfileEntity request) {
        ParsedMcpConfig parsed = resolveMcpConfigFromRequest(request);
        return AgentMcpProfileEntity.builder()
                .userId(StringUtils.trimToEmpty(request.getUserId()))
                .type(parsed.type)
                .name(parsed.name)
                .description(parsed.description)
                .baseUri(parsed.baseUri)
                .configJson(parsed.configJson)
                .sseEndpoint(parsed.endpoint)
                .requestTimeout(parsed.requestTimeout)
                .authType(parsed.authType)
                .authToken(parsed.authToken)
                .authKeyName(parsed.authKeyName)
                .headersJson(mapToJson(parsed.headers))
                .queryJson(mapToJson(parsed.query))
                .build();
    }

    private void validateMcpConnectionTestRequest(AgentMcpProfileEntity request) {
        if (request == null) {
            throw new AppException(ResponseCode.ILLEGAL_PARAMETER.getCode(), "request is null");
        }
        if (StringUtils.isBlank(request.getUserId())) {
            throw new AppException(ResponseCode.ILLEGAL_PARAMETER.getCode(), "userId is blank");
        }
        resolveMcpConfigFromRequest(request);
    }

    private ParsedMcpConfig resolveMcpConfigFromRequest(AgentMcpProfileEntity request) {
        String configJson = StringUtils.trimToEmpty(request.getConfigJson());
        if (StringUtils.isNotBlank(configJson)) {
            return parseMcpConfigJson(configJson);
        }
        return parseMcpConfigJson(buildConfigJsonFromLegacyFields(request));
    }

    private AgentMcpProfileEntity hydrateMcpEntity(AgentMcpProfileEntity source) {
        if (source == null) {
            return null;
        }
        ParsedMcpConfig parsed = parseMcpConfigJson(source.getConfigJson());
        String resolvedDescription = StringUtils.defaultIfBlank(parsed.description, source.getDescription());
        return AgentMcpProfileEntity.builder()
                .id(source.getId())
                .userId(source.getUserId())
                .type(parsed.type)
                .name(parsed.name)
                .description(resolvedDescription)
                .baseUri(parsed.baseUri)
                .configJson(parsed.configJson)
                .sseEndpoint(parsed.endpoint)
                .requestTimeout(parsed.requestTimeout)
                .authType(parsed.authType)
                .authToken(parsed.authToken)
                .authKeyName(parsed.authKeyName)
                .headersJson(mapToJson(parsed.headers))
                .queryJson(mapToJson(parsed.query))
                .createTime(source.getCreateTime())
                .updateTime(source.getUpdateTime())
                .build();
    }

    private ParsedMcpConfig parseMcpConfigJson(String configJson) {
        String normalizedJson = StringUtils.trimToEmpty(configJson);
        if (StringUtils.isBlank(normalizedJson)) {
            throw new AppException(ResponseCode.ILLEGAL_PARAMETER.getCode(), "configJson is blank");
        }

        Map<String, Object> root = parseObjectMap(normalizedJson, "configJson is invalid");
        Object mcpServersRaw = root.get("mcpServers");
        if (!(mcpServersRaw instanceof Map)) {
            throw new AppException(ResponseCode.ILLEGAL_PARAMETER.getCode(), "configJson must contain mcpServers object");
        }

        Map<String, Object> mcpServers = castObjectMap(mcpServersRaw, "mcpServers is invalid");
        List<Map.Entry<String, Object>> entries = mcpServers.entrySet().stream()
                .filter(entry -> StringUtils.isNotBlank(entry.getKey()))
                .filter(entry -> entry.getValue() instanceof Map)
                .collect(Collectors.toList());
        if (entries.isEmpty()) {
            throw new AppException(ResponseCode.ILLEGAL_PARAMETER.getCode(), "mcpServers is empty");
        }
        if (entries.size() > 1) {
            throw new AppException(ResponseCode.ILLEGAL_PARAMETER.getCode(), "only one mcp server is supported currently");
        }

        Map.Entry<String, Object> entry = entries.get(0);
        String serverName = entry.getKey().trim();
        Map<String, Object> serverConfig = castObjectMap(entry.getValue(), "mcp server config is invalid");

        String type = normalizeMcpType(asString(serverConfig.get("type")));
        if (!isSupportedMcpType(type)) {
            throw new AppException(ResponseCode.ILLEGAL_PARAMETER.getCode(), "mcp type is invalid, only sse or streamableHttp is supported");
        }
        String description = StringUtils.trimToEmpty(asString(serverConfig.get("description")));

        String urlText = StringUtils.trimToEmpty(asString(serverConfig.get("url")));
        if (StringUtils.isBlank(urlText)) {
            throw new AppException(ResponseCode.ILLEGAL_PARAMETER.getCode(), "mcp url is blank");
        }

        URI uri;
        try {
            uri = new URI(urlText);
        } catch (URISyntaxException e) {
            throw new AppException(ResponseCode.ILLEGAL_PARAMETER.getCode(), "mcp url is invalid");
        }

        String scheme = StringUtils.lowerCase(StringUtils.trimToEmpty(uri.getScheme()));
        if (!"http".equals(scheme) && !"https".equals(scheme)) {
            throw new AppException(ResponseCode.ILLEGAL_PARAMETER.getCode(), "mcp url protocol must be http or https");
        }
        if (StringUtils.isNotBlank(uri.getUserInfo())) {
            throw new AppException(ResponseCode.ILLEGAL_PARAMETER.getCode(), "mcp url must not include user info");
        }

        String host = StringUtils.lowerCase(StringUtils.trimToEmpty(uri.getHost()));
        if (StringUtils.isBlank(host)) {
            throw new AppException(ResponseCode.ILLEGAL_PARAMETER.getCode(), "mcp url host is invalid");
        }
        if (isLocalOrPrivateHost(host)) {
            throw new AppException(ResponseCode.ILLEGAL_PARAMETER.getCode(), "mcp url host is not allowed");
        }

        String authority = StringUtils.trimToEmpty(uri.getRawAuthority());
        String baseUri = scheme + "://" + authority;
        validateMcpBaseUri(baseUri);

        String endpoint = StringUtils.defaultIfBlank(uri.getRawPath(), defaultEndpoint(type));
        if (!endpoint.startsWith("/")) {
            endpoint = "/" + endpoint;
        }
        validateMcpEndpoint(endpoint);

        int requestTimeout = parsePositiveInt(serverConfig.get("requestTimeout"), 3000);
        String authType = "none";
        String authToken = "";
        String authKeyName = "";

        Map<String, String> headers = toStringMap(serverConfig.get("headers"));
        Map<String, String> query = new LinkedHashMap<>();
        query.putAll(toStringMap(serverConfig.get("query")));
        query.putAll(toStringMap(serverConfig.get("queryParams")));
        query.putAll(parseQueryString(uri.getRawQuery()));

        Object authRaw = serverConfig.get("auth");
        if (authRaw instanceof Map) {
            Map<String, Object> authMap = castObjectMap(authRaw, "auth is invalid");
            authType = normalizeAuthType(asString(authMap.get("type")));
            authToken = StringUtils.trimToEmpty(asString(authMap.get("token")));
            authKeyName = StringUtils.trimToEmpty(asString(authMap.get("keyName")));
        }

        if ("bearer".equals(authType) && StringUtils.isBlank(authToken)) {
            throw new AppException(ResponseCode.ILLEGAL_PARAMETER.getCode(), "auth.token is blank when authType=bearer");
        }
        if ("apiKey".equals(authType)) {
            if (StringUtils.isBlank(authToken)) {
                throw new AppException(ResponseCode.ILLEGAL_PARAMETER.getCode(), "auth.token is blank when authType=apiKey");
            }
            if (StringUtils.isBlank(authKeyName)) {
                throw new AppException(ResponseCode.ILLEGAL_PARAMETER.getCode(), "auth.keyName is blank when authType=apiKey");
            }
        }

        Map<String, Object> normalizedServer = new LinkedHashMap<>(serverConfig);
        normalizedServer.put("type", type);
        normalizedServer.put("url", baseUri + endpoint);
        normalizedServer.put("requestTimeout", requestTimeout);
        if (StringUtils.isBlank(description)) {
            normalizedServer.remove("description");
        } else {
            normalizedServer.put("description", description);
        }
        if (headers.isEmpty()) {
            normalizedServer.remove("headers");
        } else {
            normalizedServer.put("headers", headers);
        }
        if (query.isEmpty()) {
            normalizedServer.remove("query");
            normalizedServer.remove("queryParams");
        } else {
            normalizedServer.put("query", query);
            normalizedServer.remove("queryParams");
        }
        if ("none".equals(authType)) {
            normalizedServer.remove("auth");
        } else {
            Map<String, Object> auth = new LinkedHashMap<>();
            auth.put("type", authType);
            auth.put("token", authToken);
            if (StringUtils.isNotBlank(authKeyName)) {
                auth.put("keyName", authKeyName);
            }
            normalizedServer.put("auth", auth);
        }

        Map<String, Object> normalizedMcpServers = new LinkedHashMap<>();
        normalizedMcpServers.put(serverName, normalizedServer);
        Map<String, Object> normalizedRoot = new LinkedHashMap<>(root);
        normalizedRoot.put("mcpServers", normalizedMcpServers);

        return new ParsedMcpConfig(
                serverName,
                description,
                type,
                baseUri,
                endpoint,
                requestTimeout,
                authType,
                authToken,
                authKeyName,
                headers,
                query,
                writeJson(normalizedRoot)
        );
    }

    private String buildConfigJsonFromLegacyFields(AgentMcpProfileEntity request) {
        String type = normalizeMcpType(request.getType());
        if (!isSupportedMcpType(type)) {
            throw new AppException(ResponseCode.ILLEGAL_PARAMETER.getCode(), "mcp type is invalid, only sse or streamableHttp is supported");
        }
        String name = StringUtils.trimToEmpty(request.getName());
        if (StringUtils.isBlank(name)) {
            throw new AppException(ResponseCode.ILLEGAL_PARAMETER.getCode(), "mcp name is blank");
        }
        String description = StringUtils.trimToEmpty(request.getDescription());
        String baseUri = StringUtils.trimToEmpty(request.getBaseUri());
        if (StringUtils.isBlank(baseUri)) {
            throw new AppException(ResponseCode.ILLEGAL_PARAMETER.getCode(), "mcp baseUri is blank");
        }
        validateMcpBaseUri(baseUri);

        String endpoint = StringUtils.defaultIfBlank(StringUtils.trimToEmpty(request.getSseEndpoint()), defaultEndpoint(type));
        if (!endpoint.startsWith("/")) {
            endpoint = "/" + endpoint;
        }
        validateMcpEndpoint(endpoint);

        int timeout = request.getRequestTimeout() == null || request.getRequestTimeout() <= 0 ? 3000 : request.getRequestTimeout();
        String authType = normalizeAuthType(request.getAuthType());
        String authToken = StringUtils.trimToEmpty(request.getAuthToken());
        String authKeyName = StringUtils.trimToEmpty(request.getAuthKeyName());
        if ("bearer".equals(authType) && StringUtils.isBlank(authToken)) {
            throw new AppException(ResponseCode.ILLEGAL_PARAMETER.getCode(), "authToken is blank when authType=bearer");
        }
        if ("apiKey".equals(authType)) {
            if (StringUtils.isBlank(authToken)) {
                throw new AppException(ResponseCode.ILLEGAL_PARAMETER.getCode(), "authToken is blank when authType=apiKey");
            }
            if (StringUtils.isBlank(authKeyName)) {
                throw new AppException(ResponseCode.ILLEGAL_PARAMETER.getCode(), "authKeyName is blank when authType=apiKey");
            }
        }

        Map<String, String> headers = parseJsonObjectMap(request.getHeadersJson());
        Map<String, String> query = parseJsonObjectMap(request.getQueryJson());

        Map<String, Object> server = new LinkedHashMap<>();
        server.put("type", type);
        server.put("url", baseUri + endpoint);
        if (StringUtils.isNotBlank(description)) {
            server.put("description", description);
        }
        server.put("requestTimeout", timeout);
        if (!headers.isEmpty()) {
            server.put("headers", headers);
        }
        if (!query.isEmpty()) {
            server.put("query", query);
        }
        if (!"none".equals(authType)) {
            Map<String, Object> auth = new LinkedHashMap<>();
            auth.put("type", authType);
            auth.put("token", authToken);
            if (StringUtils.isNotBlank(authKeyName)) {
                auth.put("keyName", authKeyName);
            }
            server.put("auth", auth);
        }

        Map<String, Object> mcpServers = new LinkedHashMap<>();
        mcpServers.put(name, server);
        Map<String, Object> root = new LinkedHashMap<>();
        root.put("mcpServers", mcpServers);
        return writeJson(root);
    }

    private String defaultEndpoint(String type) {
        return MCP_TYPE_STREAMABLE_HTTP.equals(type) ? "/mcp" : "/sse";
    }

    private String normalizeMcpType(String rawType) {
        String normalized = StringUtils.trimToEmpty(rawType);
        if (StringUtils.isBlank(normalized)) {
            return MCP_TYPE_SSE;
        }
        String lower = normalized.toLowerCase(Locale.ROOT).replace("-", "");
        if ("sse".equals(lower)) {
            return MCP_TYPE_SSE;
        }
        if ("streamablehttp".equals(lower)) {
            return MCP_TYPE_STREAMABLE_HTTP;
        }
        return normalized;
    }

    private boolean isSupportedMcpType(String type) {
        String normalized = normalizeMcpType(type);
        return MCP_TYPE_SSE.equals(normalized) || MCP_TYPE_STREAMABLE_HTTP.equals(normalized);
    }

    private void validateMcpBaseUri(String baseUri) {
        String uriText = StringUtils.trimToEmpty(baseUri);
        URI uri;
        try {
            uri = new URI(uriText);
        } catch (URISyntaxException e) {
            throw new AppException(ResponseCode.ILLEGAL_PARAMETER.getCode(), "mcp baseUri is invalid");
        }

        String scheme = StringUtils.lowerCase(StringUtils.trimToEmpty(uri.getScheme()));
        if (!"http".equals(scheme) && !"https".equals(scheme)) {
            throw new AppException(ResponseCode.ILLEGAL_PARAMETER.getCode(), "mcp baseUri protocol must be http or https");
        }

        if (StringUtils.isNotBlank(uri.getUserInfo())) {
            throw new AppException(ResponseCode.ILLEGAL_PARAMETER.getCode(), "mcp baseUri must not include user info");
        }

        String host = StringUtils.lowerCase(StringUtils.trimToEmpty(uri.getHost()));
        if (StringUtils.isBlank(host)) {
            throw new AppException(ResponseCode.ILLEGAL_PARAMETER.getCode(), "mcp baseUri host is invalid");
        }
        if (isLocalOrPrivateHost(host)) {
            throw new AppException(ResponseCode.ILLEGAL_PARAMETER.getCode(), "mcp baseUri host is not allowed");
        }
    }

    private void validateMcpEndpoint(String rawEndpoint) {
        String endpoint = StringUtils.trimToEmpty(rawEndpoint);
        if (StringUtils.isBlank(endpoint)) {
            return;
        }

        URI endpointUri;
        try {
            endpointUri = new URI(endpoint);
        } catch (URISyntaxException e) {
            throw new AppException(ResponseCode.ILLEGAL_PARAMETER.getCode(), "mcp endpoint is invalid");
        }

        if (endpointUri.isAbsolute() || StringUtils.isNotBlank(endpointUri.getHost())) {
            throw new AppException(ResponseCode.ILLEGAL_PARAMETER.getCode(), "mcp endpoint must be relative path");
        }
    }

    private void testSseConnection(AgentMcpProfileEntity profile) throws Exception {
        String endpoint = appendQueryParams(profile.getSseEndpoint(), parseJsonObjectMap(profile.getQueryJson()));
        HttpClientSseClientTransport.Builder transportBuilder = HttpClientSseClientTransport.builder(profile.getBaseUri())
                .sseEndpoint(endpoint);
        Map<String, String> headers = buildHeaders(profile);
        if (!headers.isEmpty()) {
            HttpRequest.Builder requestBuilder = HttpRequest.newBuilder();
            headers.forEach(requestBuilder::header);
            transportBuilder.requestBuilder(requestBuilder);
        }
        HttpClientSseClientTransport transport = transportBuilder.build();

        McpSyncClient client = McpClient
                .sync(transport)
                .requestTimeout(Duration.ofMillis(profile.getRequestTimeout()))
                .build();
        McpSchema.InitializeResult initializeResult = client.initialize();
        if (initializeResult == null) {
            throw new AppException(ResponseCode.ILLEGAL_PARAMETER.getCode(), "mcp connection test failed: initialize result is null");
        }
    }

    private void testStreamableHttpConnection(AgentMcpProfileEntity profile) throws Exception {
        String endpoint = appendQueryParams(profile.getSseEndpoint(), parseJsonObjectMap(profile.getQueryJson()));
        HttpClientStreamableHttpTransport.Builder transportBuilder = HttpClientStreamableHttpTransport.builder(profile.getBaseUri())
                .endpoint(endpoint)
                // Some streamableHttp MCP servers are stateless and don't support SSE back-channel streaming.
                // Keep the probe focused on request/response capability and reduce reconnect noise.
                .resumableStreams(false)
                .openConnectionOnStartup(false);
        Map<String, String> headers = buildHeaders(profile);
        if (!headers.isEmpty()) {
            HttpRequest.Builder requestBuilder = HttpRequest.newBuilder();
            headers.forEach(requestBuilder::header);
            transportBuilder.requestBuilder(requestBuilder);
        }
        HttpClientStreamableHttpTransport transport = transportBuilder.build();

        McpSyncClient client = McpClient
                .sync(transport)
                .requestTimeout(Duration.ofMillis(profile.getRequestTimeout()))
                .build();
        McpSchema.InitializeResult initializeResult = client.initialize();
        if (initializeResult == null) {
            throw new AppException(ResponseCode.ILLEGAL_PARAMETER.getCode(), "mcp connection test failed: initialize result is null");
        }
    }

    private String normalizeAuthType(String authType) {
        String normalized = StringUtils.trimToEmpty(authType);
        if (StringUtils.isBlank(normalized)) {
            return "none";
        }
        String lower = normalized.toLowerCase(Locale.ROOT).replace("-", "");
        if ("none".equals(lower)) {
            return "none";
        }
        if ("bearer".equals(lower)) {
            return "bearer";
        }
        if ("apikey".equals(lower)) {
            return "apiKey";
        }
        throw new AppException(ResponseCode.ILLEGAL_PARAMETER.getCode(), "authType is invalid, only none/bearer/apiKey is supported");
    }

    private Map<String, String> parseJsonObjectMap(String jsonText) {
        String normalized = StringUtils.trimToEmpty(jsonText);
        if (StringUtils.isBlank(normalized)) {
            return new LinkedHashMap<>();
        }
        return toStringMap(parseObjectMap(normalized, "json field must be valid JSON object"));
    }

    private Map<String, String> buildHeaders(AgentMcpProfileEntity profile) {
        Map<String, String> headers = parseJsonObjectMap(profile.getHeadersJson());
        String authType = normalizeAuthType(profile.getAuthType());
        if ("bearer".equals(authType) && StringUtils.isNotBlank(profile.getAuthToken())) {
            headers.putIfAbsent("Authorization", "Bearer " + profile.getAuthToken().trim());
            return headers;
        }
        if ("apiKey".equals(authType) && StringUtils.isNotBlank(profile.getAuthToken())) {
            String keyName = StringUtils.defaultIfBlank(profile.getAuthKeyName(), "X-API-Key");
            headers.putIfAbsent(keyName.trim(), profile.getAuthToken().trim());
        }
        return headers;
    }

    private String appendQueryParams(String endpoint, Map<String, String> queryParams) {
        if (queryParams == null || queryParams.isEmpty()) {
            return endpoint;
        }
        StringBuilder builder = new StringBuilder(StringUtils.defaultString(endpoint));
        char joiner = builder.indexOf("?") >= 0 ? '&' : '?';
        for (Map.Entry<String, String> entry : queryParams.entrySet()) {
            if (StringUtils.isBlank(entry.getKey()) || entry.getValue() == null) {
                continue;
            }
            builder.append(joiner)
                    .append(URLEncoder.encode(entry.getKey().trim(), StandardCharsets.UTF_8))
                    .append("=")
                    .append(URLEncoder.encode(entry.getValue(), StandardCharsets.UTF_8));
            joiner = '&';
        }
        return builder.toString();
    }

    private String resolveExceptionMessage(Exception exception) {
        String message = exception.getMessage();
        if (StringUtils.isBlank(message)) {
            return exception.getClass().getSimpleName();
        }
        return message;
    }

    private boolean isLocalOrPrivateHost(String host) {
        String normalizedHost = StringUtils.lowerCase(StringUtils.trimToEmpty(host), Locale.ROOT);
        if (StringUtils.isBlank(normalizedHost)) {
            return true;
        }
        if ("localhost".equals(normalizedHost) || normalizedHost.endsWith(".localhost") || normalizedHost.endsWith(".local")) {
            return true;
        }
        if ("0.0.0.0".equals(normalizedHost) || "::".equals(normalizedHost) || "::1".equals(normalizedHost)) {
            return true;
        }

        if (normalizedHost.contains(":")) {
            return normalizedHost.startsWith("fe80:")
                    || normalizedHost.startsWith("fc")
                    || normalizedHost.startsWith("fd");
        }

        if (!isIpv4Literal(normalizedHost)) {
            return false;
        }

        long ip = ipv4ToLong(normalizedHost);
        if (ip < 0) {
            return true;
        }

        return inIpv4Range(ip, "10.0.0.0", 8)
                || inIpv4Range(ip, "127.0.0.0", 8)
                || inIpv4Range(ip, "169.254.0.0", 16)
                || inIpv4Range(ip, "172.16.0.0", 12)
                || inIpv4Range(ip, "192.168.0.0", 16)
                || inIpv4Range(ip, "100.64.0.0", 10);
    }

    private boolean isIpv4Literal(String host) {
        String[] segments = host.split("\\.");
        if (segments.length != 4) {
            return false;
        }
        for (String segment : segments) {
            if (segment.isEmpty() || !StringUtils.isNumeric(segment)) {
                return false;
            }
            int value = Integer.parseInt(segment);
            if (value < 0 || value > 255) {
                return false;
            }
        }
        return true;
    }

    private long ipv4ToLong(String host) {
        String[] segments = host.split("\\.");
        if (segments.length != 4) {
            return -1;
        }
        long result = 0;
        for (String segment : segments) {
            if (!StringUtils.isNumeric(segment)) {
                return -1;
            }
            int part = Integer.parseInt(segment);
            if (part < 0 || part > 255) {
                return -1;
            }
            result = (result << 8) + part;
        }
        return result;
    }

    private boolean inIpv4Range(long ip, String cidrBase, int prefixLength) {
        long baseIp = ipv4ToLong(cidrBase);
        if (baseIp < 0) {
            return false;
        }
        long mask = prefixLength == 0 ? 0 : (~0L << (32 - prefixLength)) & 0xFFFFFFFFL;
        return (ip & mask) == (baseIp & mask);
    }

    private AgentMcpProfileEntity requireMcp(Long id, String userId) {
        AgentMcpProfileEntity entity = agentMcpProfileRepository.queryById(id, userId);
        if (entity == null) {
            throw new AppException(ResponseCode.E0001.getCode(), "mcp profile not found");
        }
        return hydrateMcpEntity(entity);
    }

    private void validateSkillRequest(AgentSkillProfileEntity request, boolean update) {
        if (request == null) {
            throw new AppException(ResponseCode.ILLEGAL_PARAMETER.getCode(), "request is null");
        }
        if (update && (request.getId() == null || request.getId() <= 0)) {
            throw new AppException(ResponseCode.ILLEGAL_PARAMETER.getCode(), "id is invalid");
        }
        if (StringUtils.isBlank(request.getUserId())) {
            throw new AppException(ResponseCode.ILLEGAL_PARAMETER.getCode(), "userId is blank");
        }
        if (StringUtils.isBlank(request.getSkillName())) {
            throw new AppException(ResponseCode.ILLEGAL_PARAMETER.getCode(), "skillName is blank");
        }
        if (StringUtils.isBlank(request.getOssPath())) {
            throw new AppException(ResponseCode.ILLEGAL_PARAMETER.getCode(), "ossPath is blank");
        }
    }

    private AgentSkillProfileEntity normalizeSkillRequest(AgentSkillProfileEntity request, AgentSkillProfileEntity existed) {
        String normalizedOssPath = normalizeOssPath(request.getOssPath());

        return AgentSkillProfileEntity.builder()
                .id(existed == null ? request.getId() : existed.getId())
                .userId(request.getUserId().trim())
                .skillName(request.getSkillName().trim())
                .ossPath(normalizedOssPath)
                .createTime(existed == null ? request.getCreateTime() : existed.getCreateTime())
                .updateTime(request.getUpdateTime())
                .build();
    }

    private String normalizeOssPath(String ossPath) {
        String normalizedPath = StringUtils.trimToEmpty(ossPath).replace("\\", "/");
        normalizedPath = StringUtils.removeStart(normalizedPath, "/");
        normalizedPath = StringUtils.stripEnd(normalizedPath, "/");
        if (StringUtils.startsWithIgnoreCase(normalizedPath, "oss://")) {
            int skillsIndex = normalizedPath.toLowerCase().indexOf("/" + SKILL_ROOT_PREFIX);
            if (skillsIndex >= 0) {
                normalizedPath = normalizedPath.substring(skillsIndex + 1);
            } else {
                throw new AppException(ResponseCode.ILLEGAL_PARAMETER.getCode(), "ossPath must be under easyagent/skills/");
            }
        }
        if (StringUtils.startsWith(normalizedPath, LEGACY_SKILL_ROOT_PREFIX)) {
            throw new AppException(ResponseCode.ILLEGAL_PARAMETER.getCode(),
                    "legacy ossPath is not supported, use easyagent/skills/{skillName}");
        }
        if (!StringUtils.startsWith(normalizedPath, SKILL_ROOT_PREFIX)) {
            normalizedPath = SKILL_ROOT_PREFIX + StringUtils.removeStart(normalizedPath, SKILL_ROOT_PREFIX);
        }
        if ("easyagent/skills".equals(normalizedPath) || "easyagent/skills/".equals(normalizedPath)) {
            throw new AppException(ResponseCode.ILLEGAL_PARAMETER.getCode(), "ossPath is invalid");
        }
        if (StringUtils.contains(normalizedPath, "..")) {
            throw new AppException(ResponseCode.ILLEGAL_PARAMETER.getCode(), "ossPath is invalid");
        }
        return normalizedPath;
    }

    private AgentSkillProfileEntity requireSkill(Long id, String userId) {
        AgentSkillProfileEntity entity = agentSkillProfileRepository.queryById(id, userId);
        if (entity == null) {
            throw new AppException(ResponseCode.E0001.getCode(), "skill profile not found");
        }
        return entity;
    }

    private Map<String, Object> parseObjectMap(String json, String errorMessage) {
        try {
            Map<String, Object> map = OBJECT_MAPPER.readValue(json, new TypeReference<Map<String, Object>>() {
            });
            if (map == null) {
                throw new AppException(ResponseCode.ILLEGAL_PARAMETER.getCode(), errorMessage);
            }
            return map;
        } catch (AppException e) {
            throw e;
        } catch (Exception e) {
            throw new AppException(ResponseCode.ILLEGAL_PARAMETER.getCode(), errorMessage);
        }
    }

    private Map<String, Object> castObjectMap(Object raw, String errorMessage) {
        if (!(raw instanceof Map)) {
            throw new AppException(ResponseCode.ILLEGAL_PARAMETER.getCode(), errorMessage);
        }
        Map<?, ?> rawMap = (Map<?, ?>) raw;
        Map<String, Object> converted = new LinkedHashMap<>();
        rawMap.forEach((key, value) -> {
            if (key != null) {
                converted.put(key.toString(), value);
            }
        });
        return converted;
    }

    private Map<String, String> toStringMap(Object raw) {
        Map<String, String> result = new LinkedHashMap<>();
        if (!(raw instanceof Map)) {
            return result;
        }
        Map<?, ?> source = (Map<?, ?>) raw;
        source.forEach((key, value) -> {
            if (key == null || value == null) {
                return;
            }
            String k = key.toString().trim();
            if (StringUtils.isBlank(k)) {
                return;
            }
            result.put(k, value.toString());
        });
        return result;
    }

    private String asString(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof String) {
            return (String) value;
        }
        if (value instanceof Number || value instanceof Boolean) {
            return value.toString();
        }
        return null;
    }

    private int parsePositiveInt(Object value, int defaultValue) {
        if (value == null) {
            return defaultValue;
        }
        if (value instanceof Number) {
            int number = ((Number) value).intValue();
            return number > 0 ? number : defaultValue;
        }
        String text = StringUtils.trimToEmpty(asString(value));
        if (StringUtils.isBlank(text)) {
            return defaultValue;
        }
        try {
            int parsed = Integer.parseInt(text);
            return parsed > 0 ? parsed : defaultValue;
        } catch (Exception e) {
            return defaultValue;
        }
    }

    private Map<String, String> parseQueryString(String rawQuery) {
        Map<String, String> result = new LinkedHashMap<>();
        if (StringUtils.isBlank(rawQuery)) {
            return result;
        }
        String[] pairs = rawQuery.split("&");
        for (String pair : pairs) {
            if (StringUtils.isBlank(pair)) {
                continue;
            }
            int eq = pair.indexOf('=');
            String key = eq >= 0 ? pair.substring(0, eq) : pair;
            String value = eq >= 0 ? pair.substring(eq + 1) : "";
            String decodedKey = URLDecoder.decode(key, StandardCharsets.UTF_8).trim();
            if (StringUtils.isBlank(decodedKey)) {
                continue;
            }
            String decodedValue = URLDecoder.decode(value, StandardCharsets.UTF_8);
            result.put(decodedKey, decodedValue);
        }
        return result;
    }

    private String mapToJson(Map<String, String> map) {
        if (map == null || map.isEmpty()) {
            return "";
        }
        return writeJson(map);
    }

    private String writeJson(Object value) {
        try {
            return OBJECT_MAPPER.writeValueAsString(value);
        } catch (Exception e) {
            throw new AppException(ResponseCode.UN_ERROR.getCode(), "json serialize failed");
        }
    }

    private static class ParsedMcpConfig {
        private final String name;
        private final String description;
        private final String type;
        private final String baseUri;
        private final String endpoint;
        private final int requestTimeout;
        private final String authType;
        private final String authToken;
        private final String authKeyName;
        private final Map<String, String> headers;
        private final Map<String, String> query;
        private final String configJson;

        private ParsedMcpConfig(String name,
                                String description,
                                String type,
                                String baseUri,
                                String endpoint,
                                int requestTimeout,
                                String authType,
                                String authToken,
                                String authKeyName,
                                Map<String, String> headers,
                                Map<String, String> query,
                                String configJson) {
            this.name = name;
            this.description = description;
            this.type = type;
            this.baseUri = baseUri;
            this.endpoint = endpoint;
            this.requestTimeout = requestTimeout;
            this.authType = authType;
            this.authToken = authToken;
            this.authKeyName = authKeyName;
            this.headers = headers == null ? new LinkedHashMap<>() : new LinkedHashMap<>(headers);
            this.query = query == null ? new LinkedHashMap<>() : new LinkedHashMap<>(query);
            this.configJson = configJson;
        }
    }
}
