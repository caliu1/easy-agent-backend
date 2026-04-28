package cn.caliu.agent.domain.agent.service;

import cn.caliu.agent.domain.agent.model.valobj.AiAgentConfigTableVO;

import java.util.List;

/**
 * Agent 装配服务接口（Armory）。
 *
 * 作用：
 * - 将配置表结构装配为可运行 Agent（含 workflow 与 runner）。
 */
public interface IArmoryService {

    void acceptArmoryAgents(List<AiAgentConfigTableVO> tables) throws Exception;
}

