package cn.caliu.agent.infrastructure.persistent.dao;

import cn.caliu.agent.infrastructure.persistent.po.AgentSkillProfilePO;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;

/**
 * Skill 配置档案 DAO。
 */
@Mapper
public interface IAgentSkillProfileDao extends BaseMapper<AgentSkillProfilePO> {
}

