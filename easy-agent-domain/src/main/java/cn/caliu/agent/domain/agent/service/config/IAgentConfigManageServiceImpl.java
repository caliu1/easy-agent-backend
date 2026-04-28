package cn.caliu.agent.domain.agent.service.config;

import cn.caliu.agent.domain.agent.model.entity.AgentConfigEntity;
import cn.caliu.agent.domain.agent.model.valobj.AgentConfigPageQueryVO;
import cn.caliu.agent.domain.agent.model.valobj.AgentConfigPageQueryResult;
import cn.caliu.agent.domain.agent.model.valobj.AgentConfigVersionVO;
import cn.caliu.agent.domain.agent.model.valobj.AiAgentConfigTableVO;
import cn.caliu.agent.domain.agent.model.valobj.AiAgentRegisterVO;
import cn.caliu.agent.domain.agent.model.valobj.properties.AiAgentAutoConfigProperties;
import cn.caliu.agent.domain.agent.repository.IAgentConfigRepository;
import cn.caliu.agent.domain.agent.repository.IAgentConfigVersionRepository;
import cn.caliu.agent.domain.session.repository.IAgentSessionBindRepository;
import cn.caliu.agent.domain.agent.service.IAgentConfigManageService;
import cn.caliu.agent.domain.agent.service.runtime.AgentRuntimeAssembler;
import cn.caliu.agent.domain.agent.service.runtime.AgentRuntimeRegistry;
import cn.caliu.agent.types.enums.ResponseCode;
import cn.caliu.agent.types.exception.AppException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import javax.annotation.Resource;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Agent 配置领域服务实现。
 *
 * 负责：
 * 1. 配置生命周期管理（草稿、发布、下线、回滚、删除）。
 * 2. 配置版本快照写入与恢复。
 * 3. 配置变更后运行时激活/下线编排（事务提交后执行）。
 */
@Slf4j
@Service
public class IAgentConfigManageServiceImpl implements IAgentConfigManageService {

    private static final String SYSTEM_OPERATOR = "system";
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    private static final int AGENT_ID_GENERATE_RETRY_TIMES = 10;
    private static final String AGENT_ID_PREFIX = "2";

    @Resource
    private IAgentConfigRepository agentConfigRepository;
    @Resource
    private IAgentConfigVersionRepository agentConfigVersionRepository;
    @Resource
    private IAgentSessionBindRepository agentSessionBindRepository;
    @Resource
    private AgentRuntimeRegistry agentRuntimeRegistry;
    @Resource
    private AgentRuntimeAssembler agentRuntimeAssembler;
    @Resource
    private AiAgentAutoConfigProperties aiAgentAutoConfigProperties;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public synchronized AgentConfigEntity createAgentConfig(AgentConfigEntity request) {
        validateCreateRequest(request);

        String agentId = generateAvailableAgentId();
        String normalizedConfigJson = normalizeCreateConfigJson(request.getConfigJson(), agentId);

        ConfigMeta configMeta = resolveConfigMeta(
                agentId,
                normalizedConfigJson,
                request.getAppName(),
                request.getAgentName(),
                request.getAgentDesc()
        );

        long now = System.currentTimeMillis();
        AgentConfigEntity created = AgentConfigEntity.createUserDraft(
                agentId,
                configMeta.appName,
                configMeta.agentName,
                configMeta.agentDesc,
                normalizedConfigJson,
                request.getOperator(),
                request.getOwnerUserId(),
                now
        );

        agentConfigRepository.insert(created);
        insertVersionSnapshot(created);
        return requireConfig(agentId);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public synchronized AgentConfigEntity updateAgentConfig(AgentConfigEntity request) {
        validateUpdateRequest(request);

        String agentId = request.getAgentId().trim();
        AgentConfigEntity existed = requireConfig(agentId);

        String mergedConfigJson = choose(request.getConfigJson(), existed.getConfigJson());
        if (StringUtils.isBlank(mergedConfigJson)) {
            throw new AppException(ResponseCode.ILLEGAL_PARAMETER.getCode(), "configJson is blank");
        }

        ConfigMeta configMeta = resolveConfigMeta(
                agentId,
                mergedConfigJson,
                choose(request.getAppName(), existed.getAppName()),
                choose(request.getAgentName(), existed.getAgentName()),
                choose(request.getAgentDesc(), existed.getAgentDesc())
        );

        long nextVersion = defaultLong(existed.getCurrentVersion(), 1L) + 1;
        AgentConfigEntity updated = existed.toDraftUpdate(
                configMeta.appName,
                configMeta.agentName,
                configMeta.agentDesc,
                mergedConfigJson,
                nextVersion,
                request.getOperator(),
                System.currentTimeMillis()
        );

        agentConfigRepository.update(updated);
        insertVersionSnapshot(updated);
        return requireConfig(agentId);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public synchronized boolean deleteAgentConfig(String agentId, String operator) {
        if (isBlank(agentId)) {
            throw new AppException(ResponseCode.ILLEGAL_PARAMETER.getCode(), "agentId is blank");
        }

        String key = agentId.trim();
        boolean deleted = agentConfigRepository.softDelete(key, trimToEmpty(operator));
        if (deleted) {
            // Delete DB bindings first, then clear runtime after transaction commit.
            agentSessionBindRepository.deleteByAgentId(key);
            runAfterCommit(() -> agentRuntimeRegistry.remove(key));
        }
        return deleted;
    }

    @Override
    public AgentConfigEntity queryAgentConfigDetail(String agentId) {
        if (isBlank(agentId)) {
            throw new AppException(ResponseCode.ILLEGAL_PARAMETER.getCode(), "agentId is blank");
        }
        return requireConfig(agentId.trim());
    }

    @Override
    public List<AgentConfigEntity> queryAgentPlazaList() {
        Map<String, AgentConfigEntity> merged = new LinkedHashMap<>();

        // Put official agents first, then allow user-published agents to override by id.
        for (AgentConfigEntity official : buildOfficialPlazaAgents()) {
            merged.put(official.getAgentId(), official);
        }
        for (AgentConfigEntity userAgent : agentConfigRepository.queryPlazaList()) {
            merged.put(userAgent.getAgentId(), userAgent);
        }
        return new ArrayList<>(merged.values());
    }

    @Override
    public AgentConfigPageQueryResult queryAgentConfigPage(AgentConfigPageQueryVO queryVO) {
        AgentConfigPageQueryVO safeQuery = queryVO == null
                ? AgentConfigPageQueryVO.builder().build()
                : queryVO;
        return agentConfigRepository.queryPage(safeQuery);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public synchronized AgentConfigEntity publishAgentConfig(String agentId, String operator) {
        if (isBlank(agentId)) {
            throw new AppException(ResponseCode.ILLEGAL_PARAMETER.getCode(), "agentId is blank");
        }

        String key = agentId.trim();
        AgentConfigEntity existed = requireConfig(key);
        if (isBlank(existed.getConfigJson())) {
            throw new AppException(ResponseCode.ILLEGAL_PARAMETER.getCode(), "configJson is blank");
        }

        ConfigMeta configMeta = resolveConfigMeta(
                key,
                existed.getConfigJson(),
                existed.getAppName(),
                existed.getAgentName(),
                existed.getAgentDesc()
        );
        AgentConfigEntity publishCandidate = AgentConfigEntity.builder()
                .agentId(key)
                .appName(configMeta.appName)
                .agentName(configMeta.agentName)
                .agentDesc(configMeta.agentDesc)
                .configJson(existed.getConfigJson())
                .build();

        // Validate assembly before publish to ensure runtime availability.
        AiAgentRegisterVO registerVO = agentRuntimeAssembler.assemble(publishCandidate);

        Long publishedVersion = defaultLong(existed.getCurrentVersion(), 1L);
        AgentConfigEntity published = existed.toPublished(
                configMeta.appName,
                configMeta.agentName,
                configMeta.agentDesc,
                publishedVersion,
                operator,
                System.currentTimeMillis()
        );

        agentConfigRepository.update(published);
        saveOrUpdateVersionSnapshot(published);
        runAfterCommit(() -> agentRuntimeRegistry.activate(key, publishedVersion, registerVO));
        return requireConfig(key);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public synchronized AgentConfigEntity offlineAgentConfig(String agentId, String operator) {
        if (isBlank(agentId)) {
            throw new AppException(ResponseCode.ILLEGAL_PARAMETER.getCode(), "agentId is blank");
        }

        String key = agentId.trim();
        AgentConfigEntity existed = requireConfig(key);

        AgentConfigEntity offline = existed.toOffline(operator, System.currentTimeMillis());

        agentConfigRepository.update(offline);
        saveOrUpdateVersionSnapshot(offline);
        // Switch runtime state after commit to keep DB/memory consistent.
        runAfterCommit(() -> agentRuntimeRegistry.deactivate(key));
        return requireConfig(key);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public synchronized AgentConfigEntity rollbackAgentConfig(String agentId, Long targetVersion, String operator) {
        if (isBlank(agentId)) {
            throw new AppException(ResponseCode.ILLEGAL_PARAMETER.getCode(), "agentId is blank");
        }
        if (targetVersion == null || targetVersion <= 0) {
            throw new AppException(ResponseCode.ILLEGAL_PARAMETER.getCode(), "targetVersion is invalid");
        }

        String key = agentId.trim();
        AgentConfigVersionVO targetSnapshot = agentConfigVersionRepository.queryByAgentIdAndVersion(key, targetVersion);
        if (targetSnapshot == null) {
            throw new AppException(ResponseCode.E0001.getCode(), "target version not found: " + targetVersion);
        }

        AgentConfigEntity existed = requireConfig(key);
        long nextVersion = defaultLong(existed.getCurrentVersion(), 1L) + 1;

        ConfigMeta configMeta = resolveConfigMeta(
                key,
                targetSnapshot.getConfigJson(),
                existed.getAppName(),
                existed.getAgentName(),
                existed.getAgentDesc()
        );

        AgentConfigEntity rollbackConfig = existed.toRollbackPublished(
                configMeta.appName,
                configMeta.agentName,
                configMeta.agentDesc,
                targetSnapshot.getConfigJson(),
                nextVersion,
                choose(operator, targetSnapshot.getOperator()),
                System.currentTimeMillis()
        );

        AiAgentRegisterVO registerVO = agentRuntimeAssembler.assemble(rollbackConfig);

        agentConfigRepository.update(rollbackConfig);
        insertVersionSnapshot(rollbackConfig);
        runAfterCommit(() -> agentRuntimeRegistry.activate(key, nextVersion, registerVO));
        return requireConfig(key);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public synchronized AgentConfigEntity publishAgentToPlaza(String agentId, String operator) {
        if (isBlank(agentId)) {
            throw new AppException(ResponseCode.ILLEGAL_PARAMETER.getCode(), "agentId is blank");
        }

        String key = agentId.trim();
        AgentConfigEntity existed = requireConfig(key);
        existed.assertCanOperatePlaza(operator);
        existed.assertCanBeListedInPlaza();

        long now = System.currentTimeMillis();
        AgentConfigEntity updated = existed.toPlazaPublished(operator, now, now);
        agentConfigRepository.update(updated);
        return requireConfig(key);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public synchronized AgentConfigEntity unpublishAgentFromPlaza(String agentId, String operator) {
        if (isBlank(agentId)) {
            throw new AppException(ResponseCode.ILLEGAL_PARAMETER.getCode(), "agentId is blank");
        }

        String key = agentId.trim();
        AgentConfigEntity existed = requireConfig(key);
        existed.assertCanOperatePlaza(operator);

        AgentConfigEntity updated = existed.toPlazaUnpublished(operator, System.currentTimeMillis());
        agentConfigRepository.update(updated);
        return requireConfig(key);
    }
    @Override
    public synchronized int reloadPublishedAgentRuntime() {
        List<AgentConfigEntity> publishedConfigs = agentConfigRepository.queryPublishedList();
        agentRuntimeRegistry.clear();

        if (publishedConfigs == null || publishedConfigs.isEmpty()) {
            return 0;
        }

        int loaded = 0;
        for (AgentConfigEntity config : publishedConfigs) {
            try {
                ConfigMeta configMeta = resolveConfigMeta(
                        config.getAgentId(),
                        config.getConfigJson(),
                        config.getAppName(),
                        config.getAgentName(),
                        config.getAgentDesc()
                );

                AgentConfigEntity candidate = AgentConfigEntity.builder()
                        .agentId(config.getAgentId())
                        .appName(configMeta.appName)
                        .agentName(configMeta.agentName)
                        .agentDesc(configMeta.agentDesc)
                        .configJson(config.getConfigJson())
                        .build();

                AiAgentRegisterVO registerVO = agentRuntimeAssembler.assemble(candidate);
                long fallbackVersion = defaultLong(config.getCurrentVersion(), 1L);
                Long activeVersion = defaultLong(config.getPublishedVersion(), fallbackVersion);
                agentRuntimeRegistry.activate(config.getAgentId(), activeVersion, registerVO);
                loaded++;
            } catch (Exception e) {
                log.error("reload published runtime failed. agentId={}", config.getAgentId(), e);
            }
        }
        return loaded;
    }

    private AgentConfigEntity requireConfig(String agentId) {
        AgentConfigEntity config = agentConfigRepository.queryByAgentId(agentId);
        if (config == null) {
            throw new AppException(ResponseCode.E0001.getCode(), "agent config not found: " + agentId);
        }
        return config;
    }

    private void insertVersionSnapshot(AgentConfigEntity config) {
        agentConfigVersionRepository.insert(AgentConfigVersionVO.builder()
                .agentId(config.getAgentId())
                .version(config.getCurrentVersion())
                .status(config.getStatus())
                .configJson(config.getConfigJson())
                .operator(config.getOperator())
                .createTime(System.currentTimeMillis())
                .build());
    }

    private void saveOrUpdateVersionSnapshot(AgentConfigEntity config) {
        agentConfigVersionRepository.saveOrUpdate(AgentConfigVersionVO.builder()
                .agentId(config.getAgentId())
                .version(config.getCurrentVersion())
                .status(config.getStatus())
                .configJson(config.getConfigJson())
                .operator(config.getOperator())
                .createTime(System.currentTimeMillis())
                .build());
    }

    private void runAfterCommit(Runnable runnable) {
        // Defer runtime mutation until transaction commit when possible.
        if (TransactionSynchronizationManager.isActualTransactionActive()
                && TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    runnable.run();
                }
            });
            return;
        }
        // Execute directly when there is no active transaction.
        runnable.run();
    }

    private List<AgentConfigEntity> buildOfficialPlazaAgents() {
        Map<String, AiAgentConfigTableVO> tables = aiAgentAutoConfigProperties.getTables();
        if (tables == null || tables.isEmpty()) {
            return new ArrayList<>();
        }

        List<AgentConfigEntity> officialAgents = new ArrayList<>();
        for (AiAgentConfigTableVO table : tables.values()) {
            if (table == null || table.getAgent() == null || isBlank(table.getAgent().getAgentId())) {
                continue;
            }

            AiAgentConfigTableVO.Agent agent = table.getAgent();
            officialAgents.add(AgentConfigEntity.builder()
                    .agentId(agent.getAgentId())
                    .appName(StringUtils.defaultString(table.getAppName()))
                    .agentName(StringUtils.defaultString(agent.getAgentName()))
                    .agentDesc(StringUtils.defaultString(agent.getAgentDesc()))
                    .configJson(null)
                    .status(AgentConfigEntity.STATUS_PUBLISHED)
                    .currentVersion(0L)
                    .publishedVersion(0L)
                    .operator(SYSTEM_OPERATOR)
                    .ownerUserId(SYSTEM_OPERATOR)
                    .sourceType(AgentConfigEntity.SOURCE_OFFICIAL)
                    .plazaStatus(AgentConfigEntity.PLAZA_ON)
                    .plazaPublishTime(null)
                    .createTime(null)
                    .updateTime(null)
                    .build());
        }
        return officialAgents;
    }
    /**
     * 解析并归一化 Agent 配置元信息（appName / agentName / agentDesc）。
     *
     * 设计目的：
     * 1. 统一处理“请求参数”与“configJson 内字段”的优先级合并，避免在 create/update/publish/rollback/reload 多处重复逻辑。
     * 2. 在进入装配与持久化前，提前完成关键一致性校验（例如 agentId 一致性）。
     * 3. 返回一个小型只读元对象，作为后续构建 AgentConfigEntity 的标准输入。
     *
     * 字段优先级规则：
     * - appName: appNameCandidate > configJson.table.appName
     * - agentName: agentNameCandidate > configJson.agent.agentName
     * - agentDesc: agentDescCandidate > configJson.agent.agentDesc
     *
     * 注意：
     * - choose(...) 会做 trim，并保证不会返回 null（最差返回空字符串）。
     */
    private ConfigMeta resolveConfigMeta(
            String expectedAgentId,
            String configJson,
            String appNameCandidate,
            String agentNameCandidate,
            String agentDescCandidate
    ) {
        // 先把 JSON 字符串解析为结构化配置对象，后续从中提取元信息字段。
        AiAgentConfigTableVO tableVO = agentRuntimeAssembler.parseConfigJson(configJson);

        // 先定义为 null，便于处理“agent 节点缺失”的场景。
        String jsonAgentId = null;
        String jsonAgentName = null;
        String jsonAgentDesc = null;

        // 安全提取 configJson.agent 下的字段。
        if (tableVO.getAgent() != null) {
            jsonAgentId = tableVO.getAgent().getAgentId();
            jsonAgentName = tableVO.getAgent().getAgentName();
            jsonAgentDesc = tableVO.getAgent().getAgentDesc();
        }

        // 关键校验：若 JSON 里显式带了 agentId，必须与外部期望 agentId 一致。
        // 这样可以防止“请求 agentId 与配置内容不一致”导致装配到错误 Agent。
        if (StringUtils.isNotBlank(jsonAgentId) && !expectedAgentId.equals(jsonAgentId.trim())) {
            throw new AppException(
                    ResponseCode.ILLEGAL_PARAMETER.getCode(),
                    "agentId mismatch between request and configJson"
            );
        }

        // appName 为必填业务字段：优先使用请求传入值，否则回退 configJson。
        String appName = choose(appNameCandidate, tableVO.getAppName());
        if (StringUtils.isBlank(appName)) {
            throw new AppException(ResponseCode.ILLEGAL_PARAMETER.getCode(), "appName is blank");
        }

        // agentName/agentDesc 允许为空：通过 defaultString 统一收敛为非 null 字符串。
        return new ConfigMeta(
                appName,
                StringUtils.defaultString(choose(agentNameCandidate, jsonAgentName)),
                StringUtils.defaultString(choose(agentDescCandidate, jsonAgentDesc))
        );
    }

    private void validateCreateRequest(AgentConfigEntity request) {
        if (request == null) {
            throw new AppException(ResponseCode.ILLEGAL_PARAMETER.getCode(), "request is null");
        }
        if (isBlank(request.getConfigJson())) {
            throw new AppException(ResponseCode.ILLEGAL_PARAMETER.getCode(), "configJson is blank");
        }
    }

    private String generateAvailableAgentId() {
        for (int index = 0; index < AGENT_ID_GENERATE_RETRY_TIMES; index++) {
            String candidate = AGENT_ID_PREFIX + System.currentTimeMillis() + ThreadLocalRandom.current().nextInt(100, 1000);
            if (!agentConfigRepository.exists(candidate)) {
                return candidate;
            }
        }
        throw new AppException(ResponseCode.UN_ERROR.getCode(), "failed to generate unique agentId");
    }

    private String normalizeCreateConfigJson(String rawConfigJson, String agentId) {
        AiAgentConfigTableVO tableVO = agentRuntimeAssembler.parseConfigJson(rawConfigJson);
        if (tableVO.getAgent() == null) {
            tableVO.setAgent(new AiAgentConfigTableVO.Agent());
        }
        tableVO.getAgent().setAgentId(agentId);
        try {
            return OBJECT_MAPPER.writeValueAsString(tableVO);
        } catch (Exception e) {
            throw new AppException(ResponseCode.UN_ERROR.getCode(), "configJson normalize failed: " + e.getMessage());
        }
    }

    private void validateUpdateRequest(AgentConfigEntity request) {
        if (request == null) {
            throw new AppException(ResponseCode.ILLEGAL_PARAMETER.getCode(), "request is null");
        }
        if (isBlank(request.getAgentId())) {
            throw new AppException(ResponseCode.ILLEGAL_PARAMETER.getCode(), "agentId is blank");
        }
    }

    /**
     * 二选一取值工具：
     * - candidate 有效（非 null / 非空 / 非全空格）时优先使用 candidate
     * - 否则回退 fallback
     * - 返回值统一做 trim，且保证不为 null（fallback 为空时返回 ""）
     */
    private String choose(String candidate, String fallback) {
        return StringUtils.isNotBlank(candidate) ? candidate.trim() : StringUtils.trimToEmpty(fallback);
    }

    private long defaultLong(Long value, long fallback) {
        return value == null ? fallback : value;
    }

    private String trimToEmpty(String value) {
        return value == null ? "" : value.trim();
    }

    private boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }

    /**
     * 配置元信息聚合对象。
     * 用于承载 resolveConfigMeta 的多返回值，避免返回 Map/数组造成语义不清。
     */
    private static class ConfigMeta {
        private final String appName;
        private final String agentName;
        private final String agentDesc;

        private ConfigMeta(String appName, String agentName, String agentDesc) {
            this.appName = appName;
            this.agentName = agentName;
            this.agentDesc = agentDesc;
        }
    }

}


