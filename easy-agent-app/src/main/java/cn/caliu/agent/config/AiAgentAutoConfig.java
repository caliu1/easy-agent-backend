package cn.caliu.agent.config;

import cn.caliu.agent.domain.agent.model.entity.AgentConfigEntity;
import cn.caliu.agent.domain.agent.model.valobj.AiAgentConfigTableVO;
import cn.caliu.agent.domain.agent.model.valobj.AiAgentRegisterVO;
import cn.caliu.agent.domain.agent.model.valobj.properties.AiAgentAutoConfigProperties;
import cn.caliu.agent.domain.agent.repository.IAgentConfigRepository;
import cn.caliu.agent.domain.agent.service.IArmoryService;
import cn.caliu.agent.domain.agent.service.armory.factory.DefaultArmoryFactory;
import cn.caliu.agent.domain.agent.service.runtime.AgentRuntimeAssembler;
import cn.caliu.agent.domain.agent.service.runtime.AgentRuntimeRegistry;
import com.alibaba.fastjson.JSON;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.ApplicationListener;
import org.springframework.context.annotation.Configuration;

import javax.annotation.Resource;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Agent 运行时自动装配入口。
 *
 * 启动策略：
 * 1. 先汇总 DB 与 yml 的全部配置为统一 tables（同 agentId 以内置 yml 为准）。
 * 2. 再一次性执行 Armory 装配。
 * 3. 最后一次性激活运行时注册表。
 */
@Slf4j
@Configuration
@EnableConfigurationProperties(AiAgentAutoConfigProperties.class)
public class AiAgentAutoConfig implements ApplicationListener<ApplicationReadyEvent> {

    @Resource
    private AiAgentAutoConfigProperties aiAgentAutoConfigProperties;
    @Resource
    private IArmoryService armoryService;
    @Resource
    private AgentRuntimeRegistry agentRuntimeRegistry;
    @Resource
    private DefaultArmoryFactory defaultArmoryFactory;
    @Resource
    private IAgentConfigRepository agentConfigRepository;
    @Resource
    private AgentRuntimeAssembler agentRuntimeAssembler;

    @Override
    public void onApplicationEvent(ApplicationReadyEvent event) {
        try {
            Map<String, StartupRuntimeEntry> mergedEntries = mergeStartupTables();
            if (mergedEntries.isEmpty()) {
                log.info("启动装配结束：未发现可加载的 Agent 配置");
                return;
            }

            List<AiAgentConfigTableVO> mergedTables = new ArrayList<>();
            for (StartupRuntimeEntry entry : mergedEntries.values()) {
                mergedTables.add(entry.getTable());
            }

            log.info("启动装配开始，合并后总数={}，tables={}", mergedTables.size(), JSON.toJSONString(mergedTables));
            armoryService.acceptArmoryAgents(mergedTables);

            activateMergedRuntime(mergedEntries);
        } catch (Exception e) {
            throw new RuntimeException("agent runtime init failed", e);
        }
    }

    /**
     * 合并启动配置：
     * - 先放 DB 已发布配置
     * - 再放 yml 内置配置（覆盖同 id 的 DB 项）
     */
    private Map<String, StartupRuntimeEntry> mergeStartupTables() {
        Map<String, StartupRuntimeEntry> merged = new LinkedHashMap<>();
        int dbCount = appendDbPublishedTables(merged);
        int ymlCount = appendYmlSystemTables(merged);
        log.info(
                "启动配置合并完成：dbCount={}, ymlCount={}, mergedCount={}（同ID以内置YML为准）",
                dbCount,
                ymlCount,
                merged.size()
        );
        return merged;
    }

    private int appendDbPublishedTables(Map<String, StartupRuntimeEntry> merged) {
        List<AgentConfigEntity> publishedConfigs = agentConfigRepository.queryPublishedList();
        if (publishedConfigs == null || publishedConfigs.isEmpty()) {
            return 0;
        }

        int count = 0;
        for (AgentConfigEntity config : publishedConfigs) {
            try {
                AiAgentConfigTableVO table = agentRuntimeAssembler.mergeConfigAndMetadata(config);
                String agentId = resolveAgentId(table);
                if (StringUtils.isBlank(agentId)) {
                    continue;
                }

                long activeVersion = resolveDbActiveVersion(config);
                merged.put(agentId, new StartupRuntimeEntry(table, activeVersion, "DB"));
                count++;
            } catch (Exception e) {
                log.error("加载 DB 启动配置失败，agentId={}", config == null ? "" : config.getAgentId(), e);
            }
        }
        return count;
    }

    private int appendYmlSystemTables(Map<String, StartupRuntimeEntry> merged) {
        Map<String, AiAgentConfigTableVO> ymlTables = aiAgentAutoConfigProperties.getTables();
        if (ymlTables == null || ymlTables.isEmpty()) {
            return 0;
        }

        int count = 0;
        for (AiAgentConfigTableVO table : ymlTables.values()) {
            String agentId = resolveAgentId(table);
            if (StringUtils.isBlank(agentId)) {
                continue;
            }

            StartupRuntimeEntry existed = merged.get(agentId);
            if (existed != null && "DB".equals(existed.getSource())) {
                log.info("内置配置覆盖 DB 配置，agentId={}", agentId);
            }
            merged.put(agentId, new StartupRuntimeEntry(table, 0L, "YML"));
            count++;
        }
        return count;
    }

    private void activateMergedRuntime(Map<String, StartupRuntimeEntry> mergedEntries) {
        agentRuntimeRegistry.clear();

        int activated = 0;
        for (Map.Entry<String, StartupRuntimeEntry> item : mergedEntries.entrySet()) {
            String agentId = item.getKey();
            StartupRuntimeEntry entry = item.getValue();

            AiAgentRegisterVO registerVO = defaultArmoryFactory.getAiAgentRegisterBean(agentId);
            if (registerVO == null) {
                log.warn("激活运行时失败：未找到装配结果，agentId={}", agentId);
                continue;
            }

            agentRuntimeRegistry.activate(agentId, entry.getVersion(), registerVO);
            activated++;
        }

        log.info("启动激活完成：activatedCount={}", activated);
    }

    private long resolveDbActiveVersion(AgentConfigEntity config) {
        long fallbackVersion = defaultLong(config.getCurrentVersion(), 1L);
        return defaultLong(config.getPublishedVersion(), fallbackVersion);
    }

    private long defaultLong(Long value, long fallback) {
        return value == null ? fallback : value;
    }

    private String resolveAgentId(AiAgentConfigTableVO table) {
        if (table == null || table.getAgent() == null) {
            return StringUtils.EMPTY;
        }
        return StringUtils.trimToEmpty(table.getAgent().getAgentId());
    }

    @Getter
    @AllArgsConstructor
    private static class StartupRuntimeEntry {
        private final AiAgentConfigTableVO table;
        private final Long version;
        private final String source;
    }

}
