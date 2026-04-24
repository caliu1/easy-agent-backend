package cn.caliu.agent.infrastructure.persistent.dao;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import cn.caliu.agent.infrastructure.persistent.po.AgentSessionHistoryPO;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface IAgentSessionHistoryDao extends BaseMapper<AgentSessionHistoryPO> {

}

