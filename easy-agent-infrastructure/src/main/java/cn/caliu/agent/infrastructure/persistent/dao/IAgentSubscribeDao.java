package cn.caliu.agent.infrastructure.persistent.dao;

import cn.caliu.agent.infrastructure.persistent.po.AgentSubscribePO;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
/**
 * IAgentSubscribeDao DAO 接口，定义数据库访问操作。
 */

@Mapper
public interface IAgentSubscribeDao extends BaseMapper<AgentSubscribePO> {
}
