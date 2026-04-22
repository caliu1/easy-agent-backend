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
 * 运行时 Agent 注册表。
 * 1. versionSlotMap：保存每个 Agent 的多版本 Runner。
 * 2. activeSlotMap：保存当前对外生效的版本指针。
 */
@Component
public class AgentRuntimeRegistry {

    private final ConcurrentMap<String, ActiveAgentSlot> activeSlotMap = new ConcurrentHashMap<>();
    private final ConcurrentMap<String, ConcurrentMap<Long, AiAgentRegisterVO>> versionSlotMap = new ConcurrentHashMap<>();

    public Optional<ActiveAgentSlot> getActiveSlot(String agentId) {
        return Optional.ofNullable(activeSlotMap.get(agentId));
    }

    public Optional<AiAgentRegisterVO> getActiveRegisterVO(String agentId) {
        ActiveAgentSlot slot = activeSlotMap.get(agentId);
        if (slot == null) {
            return Optional.empty();
        }
        return Optional.ofNullable(slot.getRegisterVO());
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

    public void registerVersion(String agentId, Long configVersion, AiAgentRegisterVO registerVO) {
        if (agentId == null || configVersion == null || configVersion <= 0 || registerVO == null) {
            return;
        }

        versionSlotMap
                .computeIfAbsent(agentId, key -> new ConcurrentHashMap<>())
                .put(configVersion, registerVO);
    }

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

    public void deactivate(String agentId) {
        activeSlotMap.remove(agentId);
    }

    public void remove(String agentId) {
        activeSlotMap.remove(agentId);
        versionSlotMap.remove(agentId);
    }

    public void clear() {
        activeSlotMap.clear();
        versionSlotMap.clear();
    }

    public Map<String, ActiveAgentSlot> snapshot() {
        return Collections.unmodifiableMap(new HashMap<>(activeSlotMap));
    }

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
