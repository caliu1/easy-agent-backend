package cn.caliu.agent.infrastructure.persistent.dao;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import cn.caliu.agent.infrastructure.persistent.po.AgentConfigVersionPO;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface IAgentConfigVersionDao extends BaseMapper<AgentConfigVersionPO> {

}
