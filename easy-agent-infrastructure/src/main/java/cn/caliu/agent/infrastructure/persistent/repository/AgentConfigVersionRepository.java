package cn.caliu.agent.infrastructure.persistent.repository;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import cn.caliu.agent.domain.agent.model.valobj.AgentConfigVersionVO;
import cn.caliu.agent.domain.agent.repository.IAgentConfigVersionRepository;
import cn.caliu.agent.infrastructure.persistent.dao.IAgentConfigVersionDao;
import cn.caliu.agent.infrastructure.persistent.po.AgentConfigVersionPO;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Repository;

import javax.annotation.Resource;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

@Repository
public class AgentConfigVersionRepository implements IAgentConfigVersionRepository {

    @Resource
    private IAgentConfigVersionDao agentConfigVersionDao;

    @Override
    public void insert(AgentConfigVersionVO versionVO) {
        AgentConfigVersionPO po = toPO(versionVO);
        po.setCreateTime(LocalDateTime.now());
        agentConfigVersionDao.insert(po);
    }

    @Override
    public void saveOrUpdate(AgentConfigVersionVO versionVO) {
        // 纯 MP 写法：先按业务唯一键更新，未命中再插入；并发冲突时兜底再更新一次。
        LocalDateTime now = LocalDateTime.now();
        LambdaUpdateWrapper<AgentConfigVersionPO> updateWrapper = new LambdaUpdateWrapper<AgentConfigVersionPO>()
                .eq(AgentConfigVersionPO::getAgentId, versionVO.getAgentId())
                .eq(AgentConfigVersionPO::getVersion, versionVO.getVersion())
                .set(AgentConfigVersionPO::getStatus, versionVO.getStatus())
                .set(AgentConfigVersionPO::getConfigJson, versionVO.getConfigJson())
                .set(AgentConfigVersionPO::getOperator, versionVO.getOperator());

        int affected = agentConfigVersionDao.update(null, updateWrapper);
        if (affected > 0) {
            return;
        }

        AgentConfigVersionPO insertPO = toPO(versionVO);
        insertPO.setCreateTime(now);
        try {
            agentConfigVersionDao.insert(insertPO);
        } catch (DuplicateKeyException e) {
            agentConfigVersionDao.update(null, updateWrapper);
        }
    }

    @Override
    public AgentConfigVersionVO queryByAgentIdAndVersion(String agentId, Long version) {
        LambdaQueryWrapper<AgentConfigVersionPO> queryWrapper = new LambdaQueryWrapper<AgentConfigVersionPO>()
                .eq(AgentConfigVersionPO::getAgentId, agentId)
                .eq(AgentConfigVersionPO::getVersion, version)
                .last("LIMIT 1");
        return toVO(agentConfigVersionDao.selectOne(queryWrapper));
    }

    @Override
    public List<AgentConfigVersionVO> queryListByAgentId(String agentId) {
        LambdaQueryWrapper<AgentConfigVersionPO> queryWrapper = new LambdaQueryWrapper<AgentConfigVersionPO>()
                .eq(AgentConfigVersionPO::getAgentId, agentId)
                .orderByDesc(AgentConfigVersionPO::getVersion);
        List<AgentConfigVersionPO> poList = agentConfigVersionDao.selectList(queryWrapper);
        if (poList == null || poList.isEmpty()) {
            return Collections.emptyList();
        }
        return poList.stream().map(this::toVO).collect(Collectors.toList());
    }

    private AgentConfigVersionPO toPO(AgentConfigVersionVO source) {
        if (source == null) {
            return null;
        }
        AgentConfigVersionPO target = new AgentConfigVersionPO();
        target.setAgentId(source.getAgentId());
        target.setVersion(source.getVersion());
        target.setStatus(source.getStatus());
        target.setConfigJson(source.getConfigJson());
        target.setOperator(source.getOperator());
        return target;
    }

    private AgentConfigVersionVO toVO(AgentConfigVersionPO source) {
        if (source == null) {
            return null;
        }
        return AgentConfigVersionVO.builder()
                .agentId(source.getAgentId())
                .version(source.getVersion())
                .status(source.getStatus())
                .configJson(source.getConfigJson())
                .operator(source.getOperator())
                .createTime(toEpochMilli(source.getCreateTime()))
                .build();
    }

    private Long toEpochMilli(LocalDateTime time) {
        if (time == null) {
            return null;
        }
        return time.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli();
    }

}
