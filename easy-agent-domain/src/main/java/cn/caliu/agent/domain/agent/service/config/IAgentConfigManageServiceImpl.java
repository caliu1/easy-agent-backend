package cn.caliu.agent.domain.agent.service.config;

import cn.caliu.agent.domain.agent.model.valobj.AgentConfigManageVO;
import cn.caliu.agent.domain.agent.model.valobj.AgentConfigPageQueryVO;
import cn.caliu.agent.domain.agent.model.valobj.AgentConfigPageResultVO;
import cn.caliu.agent.domain.agent.model.valobj.AgentConfigVersionVO;
import cn.caliu.agent.domain.agent.model.valobj.AiAgentConfigTableVO;
import cn.caliu.agent.domain.agent.model.valobj.AiAgentRegisterVO;
import cn.caliu.agent.domain.agent.model.valobj.properties.AiAgentAutoConfigProperties;
import cn.caliu.agent.domain.agent.repository.IAgentConfigRepository;
import cn.caliu.agent.domain.agent.repository.IAgentConfigVersionRepository;
import cn.caliu.agent.domain.agent.repository.IAgentSubscribeRepository;
import cn.caliu.agent.domain.agent.repository.IAgentSessionBindRepository;
import cn.caliu.agent.domain.agent.service.IAgentConfigManageService;
import cn.caliu.agent.domain.agent.service.runtime.AgentRuntimeAssembler;
import cn.caliu.agent.domain.agent.service.runtime.AgentRuntimeRegistry;
import cn.caliu.agent.types.enums.ResponseCode;
import cn.caliu.agent.types.exception.AppException;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import javax.annotation.Resource;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Slf4j
@Service
public class IAgentConfigManageServiceImpl implements IAgentConfigManageService {

    private static final String STATUS_DRAFT = "DRAFT";
    private static final String STATUS_PUBLISHED = "PUBLISHED";
    private static final String STATUS_OFFLINE = "OFFLINE";
    private static final String SOURCE_USER = "USER";
    private static final String SOURCE_OFFICIAL = "OFFICIAL";
    private static final String PLAZA_ON = "ON";
    private static final String PLAZA_OFF = "OFF";
    private static final String SYSTEM_OPERATOR = "system";

    @Resource
    private IAgentConfigRepository agentConfigRepository;
    @Resource
    private IAgentConfigVersionRepository agentConfigVersionRepository;
    @Resource
    private IAgentSessionBindRepository agentSessionBindRepository;
    @Resource
    private IAgentSubscribeRepository agentSubscribeRepository;
    @Resource
    private AgentRuntimeRegistry agentRuntimeRegistry;
    @Resource
    private AgentRuntimeAssembler agentRuntimeAssembler;
    @Resource
    private AiAgentAutoConfigProperties aiAgentAutoConfigProperties;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public synchronized AgentConfigManageVO createAgentConfig(AgentConfigManageVO request) {
        validateCreateRequest(request);

        String agentId = request.getAgentId().trim();
        if (agentConfigRepository.exists(agentId)) {
            throw new AppException(ResponseCode.ILLEGAL_PARAMETER.getCode(), "agent config already exists: " + agentId);
        }

        ConfigMeta configMeta = resolveConfigMeta(
                agentId,
                request.getConfigJson(),
                request.getAppName(),
                request.getAgentName(),
                request.getAgentDesc()
        );

        long now = System.currentTimeMillis();
        AgentConfigManageVO created = AgentConfigManageVO.builder()
                .agentId(agentId)
                .appName(configMeta.appName)
                .agentName(configMeta.agentName)
                .agentDesc(configMeta.agentDesc)
                .configJson(request.getConfigJson())
                .status(STATUS_DRAFT)
                .currentVersion(1L)
                .publishedVersion(null)
                .operator(trimToEmpty(request.getOperator()))
                // 新建动态 Agent 默认归属创建人，来源为 USER，且默认不在广场中。
                .ownerUserId(resolveOwnerUserId(request.getOwnerUserId(), request.getOperator()))
                .sourceType(SOURCE_USER)
                .plazaStatus(PLAZA_OFF)
                .plazaPublishTime(null)
                .createTime(now)
                .updateTime(now)
                .build();

        agentConfigRepository.insert(created);
        insertVersionSnapshot(created);
        return requireConfig(agentId);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public synchronized AgentConfigManageVO updateAgentConfig(AgentConfigManageVO request) {
        validateUpdateRequest(request);

        String agentId = request.getAgentId().trim();
        AgentConfigManageVO existed = requireConfig(agentId);

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
        AgentConfigManageVO updated = AgentConfigManageVO.builder()
                .agentId(agentId)
                .appName(configMeta.appName)
                .agentName(configMeta.agentName)
                .agentDesc(configMeta.agentDesc)
                .configJson(mergedConfigJson)
                .status(STATUS_DRAFT)
                .currentVersion(nextVersion)
                .publishedVersion(existed.getPublishedVersion())
                .operator(choose(request.getOperator(), existed.getOperator()))
                .ownerUserId(resolveOwnerUserId(existed))
                .sourceType(normalizeSourceType(existed.getSourceType()))
                .plazaStatus(normalizePlazaStatus(existed.getPlazaStatus()))
                .plazaPublishTime(existed.getPlazaPublishTime())
                .createTime(existed.getCreateTime())
                .updateTime(System.currentTimeMillis())
                .build();

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
            // 先删除数据库绑定，再在事务提交后清理运行时，避免回滚导致内存态不一致。
            agentSessionBindRepository.deleteByAgentId(key);
            runAfterCommit(() -> agentRuntimeRegistry.remove(key));
        }
        return deleted;
    }

    @Override
    public AgentConfigManageVO queryAgentConfigDetail(String agentId) {
        if (isBlank(agentId)) {
            throw new AppException(ResponseCode.ILLEGAL_PARAMETER.getCode(), "agentId is blank");
        }
        return requireConfig(agentId.trim());
    }

    @Override
    public List<AgentConfigManageVO> queryAgentConfigList() {
        return agentConfigRepository.queryList().stream()
                .sorted(Comparator.comparingLong(item -> -defaultLong(item.getUpdateTime(), 0L)))
                .collect(Collectors.toList());
    }

    @Override
    public List<AgentConfigManageVO> queryMyAgentConfigList(String userId) {
        if (isBlank(userId)) {
            return new ArrayList<>();
        }
        return agentConfigRepository.queryMyList(userId.trim()).stream()
                .sorted(Comparator.comparingLong(item -> -defaultLong(item.getUpdateTime(), 0L)))
                .collect(Collectors.toList());
    }

    @Override
    public List<AgentConfigManageVO> queryAgentPlazaList() {
        Map<String, AgentConfigManageVO> merged = new LinkedHashMap<>();

        // 先放官方 Agent，再用用户发布到广场的 Agent 覆盖同名项。
        for (AgentConfigManageVO official : buildOfficialPlazaAgents()) {
            merged.put(official.getAgentId(), official);
        }
        for (AgentConfigManageVO userAgent : agentConfigRepository.queryPlazaList()) {
            merged.put(userAgent.getAgentId(), userAgent);
        }
        return new ArrayList<>(merged.values());
    }

    @Override
    public List<AgentConfigManageVO> queryMySubscribedAgentList(String userId) {
        if (isBlank(userId)) {
            return new ArrayList<>();
        }

        List<String> subscribedAgentIds = agentSubscribeRepository.querySubscribedAgentIds(userId.trim());
        if (subscribedAgentIds == null || subscribedAgentIds.isEmpty()) {
            return new ArrayList<>();
        }

        Map<String, AgentConfigManageVO> plazaAgentMap = queryAgentPlazaList().stream()
                .collect(Collectors.toMap(
                        AgentConfigManageVO::getAgentId,
                        item -> item,
                        (left, right) -> right,
                        LinkedHashMap::new
                ));

        List<AgentConfigManageVO> subscribedAgents = new ArrayList<>();
        for (String subscribedAgentId : subscribedAgentIds) {
            AgentConfigManageVO subscribed = plazaAgentMap.get(subscribedAgentId);
            if (subscribed != null) {
                subscribedAgents.add(subscribed);
            }
        }
        return subscribedAgents;
    }

    @Override
    public List<AgentConfigManageVO> queryPublishedAgentConfigList() {
        return agentConfigRepository.queryPublishedList().stream()
                .sorted(Comparator.comparingLong(item -> -defaultLong(item.getUpdateTime(), 0L)))
                .collect(Collectors.toList());
    }

    @Override
    public AgentConfigPageResultVO queryAgentConfigPage(AgentConfigPageQueryVO queryVO) {
        AgentConfigPageQueryVO safeQuery = queryVO == null
                ? AgentConfigPageQueryVO.builder().build()
                : queryVO;
        return agentConfigRepository.queryPage(safeQuery);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public synchronized AgentConfigManageVO publishAgentConfig(String agentId, String operator) {
        if (isBlank(agentId)) {
            throw new AppException(ResponseCode.ILLEGAL_PARAMETER.getCode(), "agentId is blank");
        }

        String key = agentId.trim();
        AgentConfigManageVO existed = requireConfig(key);
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
        AgentConfigManageVO publishCandidate = AgentConfigManageVO.builder()
                .agentId(key)
                .appName(configMeta.appName)
                .agentName(configMeta.agentName)
                .agentDesc(configMeta.agentDesc)
                .configJson(existed.getConfigJson())
                .build();

        // 发布前先做装配校验, 保证发布后可运行
        AiAgentRegisterVO registerVO = agentRuntimeAssembler.assemble(publishCandidate);

        Long publishedVersion = defaultLong(existed.getCurrentVersion(), 1L);
        AgentConfigManageVO published = AgentConfigManageVO.builder()
                .agentId(key)
                .appName(configMeta.appName)
                .agentName(configMeta.agentName)
                .agentDesc(configMeta.agentDesc)
                .configJson(existed.getConfigJson())
                .status(STATUS_PUBLISHED)
                .currentVersion(publishedVersion)
                .publishedVersion(publishedVersion)
                .operator(choose(operator, existed.getOperator()))
                .ownerUserId(resolveOwnerUserId(existed))
                .sourceType(normalizeSourceType(existed.getSourceType()))
                .plazaStatus(normalizePlazaStatus(existed.getPlazaStatus()))
                .plazaPublishTime(existed.getPlazaPublishTime())
                .createTime(existed.getCreateTime())
                .updateTime(System.currentTimeMillis())
                .build();

        agentConfigRepository.update(published);
        saveOrUpdateVersionSnapshot(published);
        runAfterCommit(() -> agentRuntimeRegistry.activate(key, publishedVersion, registerVO));
        return requireConfig(key);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public synchronized AgentConfigManageVO offlineAgentConfig(String agentId, String operator) {
        if (isBlank(agentId)) {
            throw new AppException(ResponseCode.ILLEGAL_PARAMETER.getCode(), "agentId is blank");
        }

        String key = agentId.trim();
        AgentConfigManageVO existed = requireConfig(key);

        AgentConfigManageVO offline = AgentConfigManageVO.builder()
                .agentId(key)
                .appName(existed.getAppName())
                .agentName(existed.getAgentName())
                .agentDesc(existed.getAgentDesc())
                .configJson(existed.getConfigJson())
                .status(STATUS_OFFLINE)
                .currentVersion(existed.getCurrentVersion())
                .publishedVersion(existed.getPublishedVersion())
                .operator(choose(operator, existed.getOperator()))
                .ownerUserId(resolveOwnerUserId(existed))
                .sourceType(normalizeSourceType(existed.getSourceType()))
                // 下线后强制从广场移除，避免广场出现不可用 Agent。
                .plazaStatus(PLAZA_OFF)
                .plazaPublishTime(null)
                .createTime(existed.getCreateTime())
                .updateTime(System.currentTimeMillis())
                .build();

        agentConfigRepository.update(offline);
        saveOrUpdateVersionSnapshot(offline);
        // 事务提交后再变更运行时状态，避免数据库回滚后内存状态已切换。
        runAfterCommit(() -> agentRuntimeRegistry.deactivate(key));
        return requireConfig(key);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public synchronized AgentConfigManageVO rollbackAgentConfig(String agentId, Long targetVersion, String operator) {
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

        AgentConfigManageVO existed = requireConfig(key);
        long nextVersion = defaultLong(existed.getCurrentVersion(), 1L) + 1;

        ConfigMeta configMeta = resolveConfigMeta(
                key,
                targetSnapshot.getConfigJson(),
                existed.getAppName(),
                existed.getAgentName(),
                existed.getAgentDesc()
        );

        AgentConfigManageVO rollbackConfig = AgentConfigManageVO.builder()
                .agentId(key)
                .appName(configMeta.appName)
                .agentName(configMeta.agentName)
                .agentDesc(configMeta.agentDesc)
                .configJson(targetSnapshot.getConfigJson())
                .status(STATUS_PUBLISHED)
                .currentVersion(nextVersion)
                .publishedVersion(nextVersion)
                .operator(choose(operator, targetSnapshot.getOperator()))
                .ownerUserId(resolveOwnerUserId(existed))
                .sourceType(normalizeSourceType(existed.getSourceType()))
                .plazaStatus(normalizePlazaStatus(existed.getPlazaStatus()))
                .plazaPublishTime(existed.getPlazaPublishTime())
                .createTime(existed.getCreateTime())
                .updateTime(System.currentTimeMillis())
                .build();

        AiAgentRegisterVO registerVO = agentRuntimeAssembler.assemble(rollbackConfig);

        agentConfigRepository.update(rollbackConfig);
        insertVersionSnapshot(rollbackConfig);
        runAfterCommit(() -> agentRuntimeRegistry.activate(key, nextVersion, registerVO));
        return requireConfig(key);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public synchronized AgentConfigManageVO publishAgentToPlaza(String agentId, String operator) {
        if (isBlank(agentId)) {
            throw new AppException(ResponseCode.ILLEGAL_PARAMETER.getCode(), "agentId is blank");
        }

        String key = agentId.trim();
        AgentConfigManageVO existed = requireConfig(key);
        ensurePlazaPermission(existed, operator);
        if (!STATUS_PUBLISHED.equals(existed.getStatus())) {
            throw new AppException(ResponseCode.ILLEGAL_PARAMETER.getCode(), "only published agent can be listed in plaza");
        }

        AgentConfigManageVO updated = AgentConfigManageVO.builder()
                .agentId(key)
                .appName(existed.getAppName())
                .agentName(existed.getAgentName())
                .agentDesc(existed.getAgentDesc())
                .configJson(existed.getConfigJson())
                .status(existed.getStatus())
                .currentVersion(existed.getCurrentVersion())
                .publishedVersion(existed.getPublishedVersion())
                .operator(choose(operator, existed.getOperator()))
                .ownerUserId(resolveOwnerUserId(existed))
                .sourceType(normalizeSourceType(existed.getSourceType()))
                .plazaStatus(PLAZA_ON)
                .plazaPublishTime(System.currentTimeMillis())
                .createTime(existed.getCreateTime())
                .updateTime(System.currentTimeMillis())
                .build();
        agentConfigRepository.update(updated);
        return requireConfig(key);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public synchronized AgentConfigManageVO unpublishAgentFromPlaza(String agentId, String operator) {
        if (isBlank(agentId)) {
            throw new AppException(ResponseCode.ILLEGAL_PARAMETER.getCode(), "agentId is blank");
        }

        String key = agentId.trim();
        AgentConfigManageVO existed = requireConfig(key);
        ensurePlazaPermission(existed, operator);

        AgentConfigManageVO updated = AgentConfigManageVO.builder()
                .agentId(key)
                .appName(existed.getAppName())
                .agentName(existed.getAgentName())
                .agentDesc(existed.getAgentDesc())
                .configJson(existed.getConfigJson())
                .status(existed.getStatus())
                .currentVersion(existed.getCurrentVersion())
                .publishedVersion(existed.getPublishedVersion())
                .operator(choose(operator, existed.getOperator()))
                .ownerUserId(resolveOwnerUserId(existed))
                .sourceType(normalizeSourceType(existed.getSourceType()))
                .plazaStatus(PLAZA_OFF)
                .plazaPublishTime(null)
                .createTime(existed.getCreateTime())
                .updateTime(System.currentTimeMillis())
                .build();
        agentConfigRepository.update(updated);
        return requireConfig(key);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean subscribeAgent(String userId, String agentId) {
        if (isBlank(userId)) {
            throw new AppException(ResponseCode.ILLEGAL_PARAMETER.getCode(), "userId is blank");
        }
        if (isBlank(agentId)) {
            throw new AppException(ResponseCode.ILLEGAL_PARAMETER.getCode(), "agentId is blank");
        }

        String normalizedAgentId = agentId.trim();
        if (!existsInPlaza(normalizedAgentId)) {
            throw new AppException(ResponseCode.ILLEGAL_PARAMETER.getCode(), "agent is not available in plaza");
        }

        agentSubscribeRepository.subscribe(userId.trim(), normalizedAgentId);
        return true;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean unsubscribeAgent(String userId, String agentId) {
        if (isBlank(userId)) {
            throw new AppException(ResponseCode.ILLEGAL_PARAMETER.getCode(), "userId is blank");
        }
        if (isBlank(agentId)) {
            throw new AppException(ResponseCode.ILLEGAL_PARAMETER.getCode(), "agentId is blank");
        }

        return agentSubscribeRepository.unsubscribe(userId.trim(), agentId.trim());
    }

    @Override
    public synchronized int reloadPublishedAgentRuntime() {
        List<AgentConfigManageVO> publishedConfigs = agentConfigRepository.queryPublishedList();
        agentRuntimeRegistry.clear();

        if (publishedConfigs == null || publishedConfigs.isEmpty()) {
            return 0;
        }

        int loaded = 0;
        for (AgentConfigManageVO config : publishedConfigs) {
            try {
                ConfigMeta configMeta = resolveConfigMeta(
                        config.getAgentId(),
                        config.getConfigJson(),
                        config.getAppName(),
                        config.getAgentName(),
                        config.getAgentDesc()
                );

                AgentConfigManageVO candidate = AgentConfigManageVO.builder()
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

    private AgentConfigManageVO requireConfig(String agentId) {
        AgentConfigManageVO config = agentConfigRepository.queryByAgentId(agentId);
        if (config == null) {
            throw new AppException(ResponseCode.E0001.getCode(), "agent config not found: " + agentId);
        }
        return config;
    }

    private void insertVersionSnapshot(AgentConfigManageVO config) {
        agentConfigVersionRepository.insert(AgentConfigVersionVO.builder()
                .agentId(config.getAgentId())
                .version(config.getCurrentVersion())
                .status(config.getStatus())
                .configJson(config.getConfigJson())
                .operator(config.getOperator())
                .createTime(System.currentTimeMillis())
                .build());
    }

    private void saveOrUpdateVersionSnapshot(AgentConfigManageVO config) {
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
        // 事务中延迟到提交后执行，保证内存态与数据库最终状态一致。
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
        // 无事务场景（如应用初始化）直接执行。
        runnable.run();
    }

    private List<AgentConfigManageVO> buildOfficialPlazaAgents() {
        Map<String, AiAgentConfigTableVO> tables = aiAgentAutoConfigProperties.getTables();
        if (tables == null || tables.isEmpty()) {
            return new ArrayList<>();
        }

        List<AgentConfigManageVO> officialAgents = new ArrayList<>();
        for (AiAgentConfigTableVO table : tables.values()) {
            if (table == null || table.getAgent() == null || isBlank(table.getAgent().getAgentId())) {
                continue;
            }

            AiAgentConfigTableVO.Agent agent = table.getAgent();
            officialAgents.add(AgentConfigManageVO.builder()
                    .agentId(agent.getAgentId())
                    .appName(StringUtils.defaultString(table.getAppName()))
                    .agentName(StringUtils.defaultString(agent.getAgentName()))
                    .agentDesc(StringUtils.defaultString(agent.getAgentDesc()))
                    .configJson(null)
                    .status(STATUS_PUBLISHED)
                    .currentVersion(0L)
                    .publishedVersion(0L)
                    .operator(SYSTEM_OPERATOR)
                    .ownerUserId(SYSTEM_OPERATOR)
                    .sourceType(SOURCE_OFFICIAL)
                    .plazaStatus(PLAZA_ON)
                    .plazaPublishTime(null)
                    .createTime(null)
                    .updateTime(null)
                    .build());
        }
        return officialAgents;
    }

    private boolean existsInPlaza(String agentId) {
        if (isBlank(agentId)) {
            return false;
        }
        String targetAgentId = agentId.trim();
        return queryAgentPlazaList().stream().anyMatch(item -> targetAgentId.equals(item.getAgentId()));
    }

    private void ensurePlazaPermission(AgentConfigManageVO existed, String operator) {
        if (SOURCE_OFFICIAL.equalsIgnoreCase(normalizeSourceType(existed.getSourceType()))) {
            throw new AppException(ResponseCode.ILLEGAL_PARAMETER.getCode(), "official agent can not be changed by this api");
        }
        if (isBlank(operator)) {
            throw new AppException(ResponseCode.ILLEGAL_PARAMETER.getCode(), "operator is blank");
        }

        String owner = resolveOwnerUserId(existed);
        String currentOperator = operator.trim();
        if (isBlank(owner) || !owner.equals(currentOperator)) {
            throw new AppException(ResponseCode.ILLEGAL_PARAMETER.getCode(), "no permission to update plaza status");
        }
    }

    private String resolveOwnerUserId(AgentConfigManageVO existed) {
        return resolveOwnerUserId(existed.getOwnerUserId(), existed.getOperator());
    }

    private String resolveOwnerUserId(String ownerUserId, String operator) {
        if (StringUtils.isNotBlank(ownerUserId)) {
            return ownerUserId.trim();
        }
        return trimToEmpty(operator);
    }

    private String normalizeSourceType(String sourceType) {
        return StringUtils.isBlank(sourceType) ? SOURCE_USER : sourceType.trim();
    }

    private String normalizePlazaStatus(String plazaStatus) {
        return StringUtils.isBlank(plazaStatus) ? PLAZA_OFF : plazaStatus.trim();
    }

    private ConfigMeta resolveConfigMeta(
            String expectedAgentId,
            String configJson,
            String appNameCandidate,
            String agentNameCandidate,
            String agentDescCandidate
    ) {
        AiAgentConfigTableVO tableVO = agentRuntimeAssembler.parseConfigJson(configJson);

        String jsonAgentId = null;
        String jsonAgentName = null;
        String jsonAgentDesc = null;
        if (tableVO.getAgent() != null) {
            jsonAgentId = tableVO.getAgent().getAgentId();
            jsonAgentName = tableVO.getAgent().getAgentName();
            jsonAgentDesc = tableVO.getAgent().getAgentDesc();
        }

        if (StringUtils.isNotBlank(jsonAgentId) && !expectedAgentId.equals(jsonAgentId.trim())) {
            throw new AppException(
                    ResponseCode.ILLEGAL_PARAMETER.getCode(),
                    "agentId mismatch between request and configJson"
            );
        }

        String appName = choose(appNameCandidate, tableVO.getAppName());
        if (StringUtils.isBlank(appName)) {
            throw new AppException(ResponseCode.ILLEGAL_PARAMETER.getCode(), "appName is blank");
        }

        return new ConfigMeta(
                appName,
                StringUtils.defaultString(choose(agentNameCandidate, jsonAgentName)),
                StringUtils.defaultString(choose(agentDescCandidate, jsonAgentDesc))
        );
    }

    private void validateCreateRequest(AgentConfigManageVO request) {
        if (request == null) {
            throw new AppException(ResponseCode.ILLEGAL_PARAMETER.getCode(), "request is null");
        }
        if (isBlank(request.getAgentId())) {
            throw new AppException(ResponseCode.ILLEGAL_PARAMETER.getCode(), "agentId is blank");
        }
        if (isBlank(request.getConfigJson())) {
            throw new AppException(ResponseCode.ILLEGAL_PARAMETER.getCode(), "configJson is blank");
        }
    }

    private void validateUpdateRequest(AgentConfigManageVO request) {
        if (request == null) {
            throw new AppException(ResponseCode.ILLEGAL_PARAMETER.getCode(), "request is null");
        }
        if (isBlank(request.getAgentId())) {
            throw new AppException(ResponseCode.ILLEGAL_PARAMETER.getCode(), "agentId is blank");
        }
    }

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
