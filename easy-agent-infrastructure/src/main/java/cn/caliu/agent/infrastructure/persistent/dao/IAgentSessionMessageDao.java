package cn.caliu.agent.infrastructure.persistent.dao;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import cn.caliu.agent.infrastructure.persistent.po.AgentSessionMessagePO;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface IAgentSessionMessageDao extends BaseMapper<AgentSessionMessagePO> {

}

