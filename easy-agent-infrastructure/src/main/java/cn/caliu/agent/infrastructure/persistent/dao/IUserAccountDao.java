package cn.caliu.agent.infrastructure.persistent.dao;

import cn.caliu.agent.infrastructure.persistent.po.UserAccountPO;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface IUserAccountDao extends BaseMapper<UserAccountPO> {
}

