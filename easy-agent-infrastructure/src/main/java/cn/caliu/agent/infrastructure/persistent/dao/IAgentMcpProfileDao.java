package cn.caliu.agent.infrastructure.persistent.dao;

import cn.caliu.agent.infrastructure.persistent.po.AgentMcpProfilePO;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;

/**
 * MCP 配置档案 DAO。
 */
@Mapper
public interface IAgentMcpProfileDao extends BaseMapper<AgentMcpProfilePO> {
}

