package cn.caliu.agent.domain.agent.service.runtime;

import cn.caliu.agent.domain.agent.model.valobj.AiAgentRegisterVO;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * Agent 运行时注册表。
 *
 * 数据结构说明：
 * 1. versionSlotMap：维护每个 Agent 的多版本运行时对象。
 * 2. activeSlotMap：维护当前对外生效的版本指针。
 *
 * 使用场景：
 * - 发布或回滚时注册新版本并切换 active。
 * - 聊天请求根据 session 绑定版本或 active 版本解析 runner。
 */
@Component
public class AgentRuntimeRegistry {

    /**
     * 当前激活版本索引：agentId -> activeSlot
     */
    private final ConcurrentMap<String, ActiveAgentSlot> activeSlotMap = new ConcurrentHashMap<>();

    /**
     * 版本运行时索引：agentId -> (version -> registerVO)
     */
    private final ConcurrentMap<String, ConcurrentMap<Long, AiAgentRegisterVO>> versionSlotMap = new ConcurrentHashMap<>();

    public Optional<ActiveAgentSlot> getActiveSlot(String agentId) {
        return Optional.ofNullable(activeSlotMap.get(agentId));
    }

    public Optional<AiAgentRegisterVO> getActiveRegisterVO(String agentId) {
        return getActiveSlot(agentId).map(ActiveAgentSlot::getRegisterVO);
    }

    public Optional<AiAgentRegisterVO> getRegisterVO(String agentId, Long configVersion) {
        if (configVersion == null || configVersion <= 0) {
            return Optional.empty();
        }

        ConcurrentMap<Long, AiAgentRegisterVO> agentVersionMap = versionSlotMap.get(agentId);
        if (agentVersionMap == null) {
            return Optional.empty();
        }
        return Optional.ofNullable(agentVersionMap.get(configVersion));
    }

    /**
     * 注册指定版本的运行时对象。
     */
    public void registerVersion(String agentId, Long configVersion, AiAgentRegisterVO registerVO) {
        if (agentId == null || configVersion == null || configVersion <= 0 || registerVO == null) {
            return;
        }

        versionSlotMap
                .computeIfAbsent(agentId, key -> new ConcurrentHashMap<>())
                .put(configVersion, registerVO);
    }

    /**
     * 激活指定版本。
     * 若传入 registerVO 为空，则尝试从 versionSlotMap 回查。
     */
    public void activate(String agentId, Long configVersion, AiAgentRegisterVO registerVO) {
        if (registerVO != null) {
            registerVersion(agentId, configVersion, registerVO);
        }

        AiAgentRegisterVO activeRegister = registerVO;
        if (activeRegister == null) {
            activeRegister = getRegisterVO(agentId, configVersion).orElse(null);
        }

        ActiveAgentSlot slot = ActiveAgentSlot.builder()
                .agentId(agentId)
                .configVersion(configVersion)
                .registerVO(activeRegister)
                .activeAt(System.currentTimeMillis())
                .build();
        activeSlotMap.put(agentId, slot);
    }

    public void activate(String agentId, Long configVersion) {
        activate(agentId, configVersion, null);
    }

    /**
     * 仅下线 active 指针，保留版本历史。
     */
    public void deactivate(String agentId) {
        activeSlotMap.remove(agentId);
    }

    /**
     * 删除 Agent 的全部运行时信息（active + versions）。
     */
    public void remove(String agentId) {
        activeSlotMap.remove(agentId);
        versionSlotMap.remove(agentId);
    }

    public void clear() {
        activeSlotMap.clear();
        versionSlotMap.clear();
    }

    /**
     * 返回 active 快照只读视图，供查询场景使用。
     */
    public Map<String, ActiveAgentSlot> snapshot() {
        return Collections.unmodifiableMap(new HashMap<>(activeSlotMap));
    }

    /**
     * 激活槽位信息。
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ActiveAgentSlot {
        private String agentId;
        private Long configVersion;
        private Long activeAt;
        private AiAgentRegisterVO registerVO;
    }

}
