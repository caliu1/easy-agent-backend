package cn.caliu.agent.infrastructure.persistent.dao;

import cn.caliu.agent.infrastructure.persistent.po.UserAccountPO;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
/**
 * IUserAccountDao DAO 接口，定义数据库访问操作。
 */

@Mapper
public interface IUserAccountDao extends BaseMapper<UserAccountPO> {
}

