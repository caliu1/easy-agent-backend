package cn.caliu.agent.infrastructure.persistent.repository;

import cn.caliu.agent.domain.user.repository.IAgentSubscribeRepository;
import cn.caliu.agent.infrastructure.persistent.dao.IAgentSubscribeDao;
import cn.caliu.agent.infrastructure.persistent.po.AgentSubscribePO;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Repository;

import javax.annotation.Resource;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

@Repository
public class AgentSubscribeRepository implements IAgentSubscribeRepository {

    @Resource
    private IAgentSubscribeDao agentSubscribeDao;

    @Override
    public void subscribe(String userId, String agentId) {
        if (StringUtils.isBlank(userId) || StringUtils.isBlank(agentId)) {
            return;
        }

        String normalizedUserId = userId.trim();
        String normalizedAgentId = agentId.trim();
        LambdaQueryWrapper<AgentSubscribePO> queryWrapper = new LambdaQueryWrapper<AgentSubscribePO>()
                .eq(AgentSubscribePO::getUserId, normalizedUserId)
                .eq(AgentSubscribePO::getAgentId, normalizedAgentId)
                .last("LIMIT 1");
        AgentSubscribePO existed = agentSubscribeDao.selectOne(queryWrapper);
        if (existed == null) {
            AgentSubscribePO po = new AgentSubscribePO();
            po.setUserId(normalizedUserId);
            po.setAgentId(normalizedAgentId);
            po.setIsDeleted(0);
            agentSubscribeDao.insert(po);
            return;
        }

        if (existed.getIsDeleted() != null && existed.getIsDeleted() == 0) {
            return;
        }

        LambdaUpdateWrapper<AgentSubscribePO> updateWrapper = new LambdaUpdateWrapper<AgentSubscribePO>()
                .eq(AgentSubscribePO::getId, existed.getId())
                .set(AgentSubscribePO::getIsDeleted, 0)
                .set(AgentSubscribePO::getUpdateTime, LocalDateTime.now());
        agentSubscribeDao.update(null, updateWrapper);
    }

    @Override
    public boolean unsubscribe(String userId, String agentId) {
        if (StringUtils.isBlank(userId) || StringUtils.isBlank(agentId)) {
            return false;
        }

        LambdaUpdateWrapper<AgentSubscribePO> updateWrapper = new LambdaUpdateWrapper<AgentSubscribePO>()
                .eq(AgentSubscribePO::getUserId, userId.trim())
                .eq(AgentSubscribePO::getAgentId, agentId.trim())
                .eq(AgentSubscribePO::getIsDeleted, 0)
                .set(AgentSubscribePO::getIsDeleted, 1)
                .set(AgentSubscribePO::getUpdateTime, LocalDateTime.now());
        return agentSubscribeDao.update(null, updateWrapper) > 0;
    }

    @Override
    public List<String> querySubscribedAgentIds(String userId) {
        if (StringUtils.isBlank(userId)) {
            return Collections.emptyList();
        }

        LambdaQueryWrapper<AgentSubscribePO> queryWrapper = new LambdaQueryWrapper<AgentSubscribePO>()
                .eq(AgentSubscribePO::getUserId, userId.trim())
                .eq(AgentSubscribePO::getIsDeleted, 0)
                .orderByDesc(AgentSubscribePO::getUpdateTime)
                .orderByDesc(AgentSubscribePO::getCreateTime);
        List<AgentSubscribePO> poList = agentSubscribeDao.selectList(queryWrapper);
        if (poList == null || poList.isEmpty()) {
            return Collections.emptyList();
        }

        return poList.stream()
                .map(AgentSubscribePO::getAgentId)
                .filter(StringUtils::isNotBlank)
                .map(String::trim)
                .collect(Collectors.toList());
    }

}
