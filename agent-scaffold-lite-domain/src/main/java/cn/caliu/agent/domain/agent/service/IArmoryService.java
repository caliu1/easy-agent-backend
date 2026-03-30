package cn.caliu.agent.domain.agent.service;

import cn.caliu.agent.domain.agent.model.valobj.AiAgentConfigTableVO;

import java.util.List;

public interface IArmoryService {

    void acceptArmoryAgents(List<AiAgentConfigTableVO> tables) throws Exception;
}
