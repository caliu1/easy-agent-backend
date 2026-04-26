package cn.caliu.agent.infrastructure.persistent.repository;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import cn.caliu.agent.domain.session.model.entity.AgentSessionBindEntity;
import cn.caliu.agent.domain.session.repository.IAgentSessionBindRepository;
import cn.caliu.agent.infrastructure.persistent.dao.IAgentSessionBindDao;
import cn.caliu.agent.infrastructure.persistent.po.AgentSessionBindPO;
import org.apache.commons.lang3.StringUtils;
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
    public void bindSession(AgentSessionBindEntity bindVO) {
        // Upsert semantics: update first, insert when absent, retry update on duplicate insert.
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
    public AgentSessionBindEntity queryBySessionId(String sessionId) {
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

    @Override
    public void deleteBySessionId(String sessionId) {
        if (StringUtils.isBlank(sessionId)) {
            return;
        }
        LambdaUpdateWrapper<AgentSessionBindPO> updateWrapper = new LambdaUpdateWrapper<AgentSessionBindPO>()
                .eq(AgentSessionBindPO::getSessionId, sessionId.trim());
        agentSessionBindDao.delete(updateWrapper);
    }

    private AgentSessionBindPO toPO(AgentSessionBindEntity source) {
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

    private AgentSessionBindEntity toVO(AgentSessionBindPO source) {
        if (source == null) {
            return null;
        }
        return AgentSessionBindEntity.builder()
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

