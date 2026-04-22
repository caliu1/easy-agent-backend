package cn.caliu.agent.infrastructure.persistent.repository;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import cn.caliu.agent.domain.agent.model.valobj.AgentSessionBindVO;
import cn.caliu.agent.domain.agent.repository.IAgentSessionBindRepository;
import cn.caliu.agent.infrastructure.persistent.dao.IAgentSessionBindDao;
import cn.caliu.agent.infrastructure.persistent.po.AgentSessionBindPO;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Repository;

import javax.annotation.Resource;
import java.time.LocalDateTime;
import java.time.ZoneId;

@Repository
public class AgentSessionBindRepository implements IAgentSessionBindRepository {

    @Resource
    private IAgentSessionBindDao agentSessionBindDao;

    @Override
    public void bindSession(AgentSessionBindVO bindVO) {
        // 纯 MP 写法：优先更新，若不存在则插入；并发插入冲突时回退更新。
        LocalDateTime now = LocalDateTime.now();
        LambdaUpdateWrapper<AgentSessionBindPO> updateWrapper = new LambdaUpdateWrapper<AgentSessionBindPO>()
                .eq(AgentSessionBindPO::getSessionId, bindVO.getSessionId())
                .set(AgentSessionBindPO::getAgentId, bindVO.getAgentId())
                .set(AgentSessionBindPO::getConfigVersion, bindVO.getConfigVersion())
                .set(AgentSessionBindPO::getUserId, bindVO.getUserId())
                .set(AgentSessionBindPO::getUpdateTime, now);

        int affected = agentSessionBindDao.update(null, updateWrapper);
        if (affected > 0) {
            return;
        }

        AgentSessionBindPO insertPO = toPO(bindVO);
        insertPO.setCreateTime(now);
        insertPO.setUpdateTime(now);
        try {
            agentSessionBindDao.insert(insertPO);
        } catch (DuplicateKeyException e) {
            agentSessionBindDao.update(null, updateWrapper);
        }
    }

    @Override
    public AgentSessionBindVO queryBySessionId(String sessionId) {
        LambdaQueryWrapper<AgentSessionBindPO> queryWrapper = new LambdaQueryWrapper<AgentSessionBindPO>()
                .eq(AgentSessionBindPO::getSessionId, sessionId)
                .last("LIMIT 1");
        return toVO(agentSessionBindDao.selectOne(queryWrapper));
    }

    @Override
    public void deleteByAgentId(String agentId) {
        LambdaUpdateWrapper<AgentSessionBindPO> updateWrapper = new LambdaUpdateWrapper<AgentSessionBindPO>()
                .eq(AgentSessionBindPO::getAgentId, agentId);
        agentSessionBindDao.delete(updateWrapper);
    }

    private AgentSessionBindPO toPO(AgentSessionBindVO source) {
        if (source == null) {
            return null;
        }
        AgentSessionBindPO target = new AgentSessionBindPO();
        target.setSessionId(source.getSessionId());
        target.setAgentId(source.getAgentId());
        target.setConfigVersion(source.getConfigVersion());
        target.setUserId(source.getUserId());
        return target;
    }

    private AgentSessionBindVO toVO(AgentSessionBindPO source) {
        if (source == null) {
            return null;
        }
        return AgentSessionBindVO.builder()
                .sessionId(source.getSessionId())
                .agentId(source.getAgentId())
                .configVersion(source.getConfigVersion())
                .userId(source.getUserId())
                .createTime(toEpochMilli(source.getCreateTime()))
                .updateTime(toEpochMilli(source.getUpdateTime()))
                .build();
    }

    private Long toEpochMilli(LocalDateTime time) {
        if (time == null) {
            return null;
        }
        return time.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli();
    }

}
