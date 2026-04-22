package cn.caliu.agent.config;

import cn.caliu.agent.domain.agent.model.valobj.AiAgentConfigTableVO;
import cn.caliu.agent.domain.agent.model.valobj.AiAgentRegisterVO;
import cn.caliu.agent.domain.agent.model.valobj.properties.AiAgentAutoConfigProperties;
import cn.caliu.agent.domain.agent.service.IAgentConfigManageService;
import cn.caliu.agent.domain.agent.service.IArmoryService;
import cn.caliu.agent.domain.agent.service.armory.factory.DefaultArmoryFactory;
import cn.caliu.agent.domain.agent.service.runtime.AgentRuntimeRegistry;
import com.alibaba.fastjson.JSON;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.ApplicationListener;
import org.springframework.context.annotation.Configuration;

import javax.annotation.Resource;
import java.util.ArrayList;
import java.util.Map;

@Slf4j
@Configuration
@EnableConfigurationProperties(AiAgentAutoConfigProperties.class)
public class AiAgentAutoConfig implements ApplicationListener<ApplicationReadyEvent> {

    @Resource
    private AiAgentAutoConfigProperties aiAgentAutoConfigProperties;
    @Resource
    private IArmoryService armoryService;
    @Resource
    private IAgentConfigManageService agentConfigManageService;
    @Resource
    private AgentRuntimeRegistry agentRuntimeRegistry;
    @Resource
    private DefaultArmoryFactory defaultArmoryFactory;

    @Override
    public void onApplicationEvent(ApplicationReadyEvent event) {
        // Load published runtime from DB first.
        int dbLoaded = 0;
        try {
            dbLoaded = agentConfigManageService.reloadPublishedAgentRuntime();
            log.info("agent runtime loaded from db. count={}", dbLoaded);
        } catch (Exception e) {
            log.error("load runtime from db failed, continue with yml system agents", e);
        }

        // Load system agents from yml as well (not only fallback), so they can coexist with DB runtime.
        Map<String, AiAgentConfigTableVO> tables = aiAgentAutoConfigProperties.getTables();
        if (tables == null || tables.isEmpty()) {
            log.info("no yml agent config found. dbLoaded={}", dbLoaded);
            return;
        }

        try {
            log.info("load runtime from yml system config. tables={}", JSON.toJSONString(tables.values()));
            armoryService.acceptArmoryAgents(new ArrayList<>(tables.values()));

            // YML system agents use version 0.
            // If an active runtime already exists with the same agentId (usually from DB), skip activation.
            for (AiAgentConfigTableVO table : tables.values()) {
                if (table == null || table.getAgent() == null || StringUtils.isBlank(table.getAgent().getAgentId())) {
                    continue;
                }

                String agentId = table.getAgent().getAgentId();
                if (agentRuntimeRegistry.getActiveSlot(agentId).isPresent()) {
                    log.info("skip yml system agent activation because active runtime already exists. agentId={}", agentId);
                    continue;
                }

                AiAgentRegisterVO registerVO = defaultArmoryFactory.getAiAgentRegisterBean(agentId);
                if (registerVO != null) {
                    agentRuntimeRegistry.activate(agentId, 0L, registerVO);
                }
            }
        } catch (Exception e) {
            throw new RuntimeException("agent runtime init failed", e);
        }
    }

}

