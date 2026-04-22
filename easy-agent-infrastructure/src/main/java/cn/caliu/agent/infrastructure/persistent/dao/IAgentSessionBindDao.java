package cn.caliu.agent.infrastructure.persistent.dao;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import cn.caliu.agent.infrastructure.persistent.po.AgentSessionBindPO;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface IAgentSessionBindDao extends BaseMapper<AgentSessionBindPO> {

}
