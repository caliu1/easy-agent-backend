package cn.caliu.agent.domain.session.service.impl;

import cn.caliu.agent.domain.agent.model.valobj.AiAgentRegisterVO;
import cn.caliu.agent.domain.agent.service.armory.factory.DefaultArmoryFactory;
import cn.caliu.agent.domain.agent.service.runtime.AgentRuntimeRegistry;
import cn.caliu.agent.domain.session.model.entity.AgentSessionBindEntity;
import cn.caliu.agent.domain.session.repository.IAgentSessionBindRepository;
import cn.caliu.agent.domain.session.service.ISessionHistoryService;
import cn.caliu.agent.domain.session.service.ISessionService;
import cn.caliu.agent.types.enums.ResponseCode;
import cn.caliu.agent.types.exception.AppException;
import com.google.adk.runner.InMemoryRunner;
import com.google.adk.sessions.Session;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;

@Service
public class SessionServiceImpl implements ISessionService {

    @Resource
    private DefaultArmoryFactory defaultArmoryFactory;
    @Resource
    private AgentRuntimeRegistry agentRuntimeRegistry;
    @Resource
    private IAgentSessionBindRepository agentSessionBindRepository;
    @Resource
    private ISessionHistoryService sessionHistoryService;

    @Override
    public String createSession(String agentId, String userId) {
        ResolvedAgentContext resolvedAgentContext = resolveActiveAgent(agentId);
        AiAgentRegisterVO registerVO = resolvedAgentContext.registerVO;

        InMemoryRunner runner = registerVO.getRunner();
        Session session = runner.sessionService().createSession(registerVO.getAppName(), userId).blockingGet();

        agentSessionBindRepository.bindSession(
                AgentSessionBindEntity.create(
                        session.id(),
                        registerVO.getAgentId(),
                        resolvedAgentContext.configVersion,
                        userId
                )
        );
        sessionHistoryService.createSession(session.id(), registerVO.getAgentId(), userId);
        return session.id();
    }

    private ResolvedAgentContext resolveActiveAgent(String agentId) {
        if (StringUtils.isBlank(agentId)) {
            throw new AppException(ResponseCode.ILLEGAL_PARAMETER.getCode(), "agentId is blank");
        }

        String key = agentId.trim();
        AgentRuntimeRegistry.ActiveAgentSlot activeSlot = agentRuntimeRegistry.getActiveSlot(key).orElse(null);
        if (activeSlot != null && activeSlot.getRegisterVO() != null) {
            return new ResolvedAgentContext(activeSlot.getRegisterVO(), defaultVersion(activeSlot.getConfigVersion()));
        }

        AiAgentRegisterVO fallbackVO = defaultArmoryFactory.getAiAgentRegisterBean(key);
        if (fallbackVO != null) {
            return new ResolvedAgentContext(fallbackVO, 0L);
        }

        throw new AppException(ResponseCode.E0001.getCode(), "agent not found: " + key);
    }

    private Long defaultVersion(Long version) {
        return version == null ? 0L : version;
    }

    private static class ResolvedAgentContext {
        private final AiAgentRegisterVO registerVO;
        private final Long configVersion;

        private ResolvedAgentContext(AiAgentRegisterVO registerVO, Long configVersion) {
            this.registerVO = registerVO;
            this.configVersion = configVersion;
        }
    }

}
