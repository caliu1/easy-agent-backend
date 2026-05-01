package cn.caliu.agent.infrastructure.persistent.repository;

import cn.caliu.agent.domain.agent.model.entity.AgentSkillProfileEntity;
import cn.caliu.agent.domain.agent.repository.IAgentSkillProfileRepository;
import cn.caliu.agent.infrastructure.persistent.dao.IAgentSkillProfileDao;
import cn.caliu.agent.infrastructure.persistent.po.AgentSkillProfilePO;
import cn.caliu.agent.types.enums.ResponseCode;
import cn.caliu.agent.types.exception.AppException;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import org.springframework.stereotype.Repository;

import javax.annotation.Resource;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Skill 配置档案仓储实现。
 */
@Repository
public class AgentSkillProfileRepository implements IAgentSkillProfileRepository {
    private static final String SYSTEM_USER_ID = "system";

    @Resource
    private IAgentSkillProfileDao agentSkillProfileDao;

    @Override
    public List<AgentSkillProfileEntity> queryByUserId(String userId) {
        LambdaQueryWrapper<AgentSkillProfilePO> queryWrapper = new LambdaQueryWrapper<AgentSkillProfilePO>()
                .and(wrapper -> wrapper
                        .eq(AgentSkillProfilePO::getUserId, userId)
                        .or()
                        .eq(AgentSkillProfilePO::getUserId, SYSTEM_USER_ID))
                .eq(AgentSkillProfilePO::getIsDeleted, 0)
                .orderByDesc(AgentSkillProfilePO::getUpdateTime);
        List<AgentSkillProfilePO> poList = agentSkillProfileDao.selectList(queryWrapper);
        if (poList == null || poList.isEmpty()) {
            return Collections.emptyList();
        }
        return poList.stream().map(this::toEntity).collect(Collectors.toList());
    }

    @Override
    public AgentSkillProfileEntity queryById(Long id, String userId) {
        LambdaQueryWrapper<AgentSkillProfilePO> queryWrapper = new LambdaQueryWrapper<AgentSkillProfilePO>()
                .eq(AgentSkillProfilePO::getId, id)
                .eq(AgentSkillProfilePO::getUserId, userId)
                .eq(AgentSkillProfilePO::getIsDeleted, 0)
                .last("LIMIT 1");
        return toEntity(agentSkillProfileDao.selectOne(queryWrapper));
    }

    @Override
    public boolean existsBySkillName(String userId, String skillName, Long excludeId) {
        LambdaQueryWrapper<AgentSkillProfilePO> queryWrapper = new LambdaQueryWrapper<AgentSkillProfilePO>()
                .eq(AgentSkillProfilePO::getUserId, userId)
                .eq(AgentSkillProfilePO::getSkillName, skillName)
                .eq(AgentSkillProfilePO::getIsDeleted, 0)
                .ne(excludeId != null, AgentSkillProfilePO::getId, excludeId);
        return agentSkillProfileDao.selectCount(queryWrapper) > 0;
    }

    @Override
    public void insert(AgentSkillProfileEntity entity) {
        AgentSkillProfilePO po = toPO(entity);
        po.setIsDeleted(0);
        int affected = agentSkillProfileDao.insert(po);
        if (affected <= 0) {
            throw new AppException(ResponseCode.UN_ERROR.getCode(), "insert skill profile failed");
        }
        entity.setId(po.getId());
    }

    @Override
    public void update(AgentSkillProfileEntity entity) {
        LambdaUpdateWrapper<AgentSkillProfilePO> updateWrapper = new LambdaUpdateWrapper<AgentSkillProfilePO>()
                .eq(AgentSkillProfilePO::getId, entity.getId())
                .eq(AgentSkillProfilePO::getUserId, entity.getUserId())
                .eq(AgentSkillProfilePO::getIsDeleted, 0)
                .set(AgentSkillProfilePO::getSkillName, entity.getSkillName())
                .set(AgentSkillProfilePO::getOssPath, entity.getOssPath())
                .set(AgentSkillProfilePO::getUpdateTime, LocalDateTime.now());
        int affected = agentSkillProfileDao.update(null, updateWrapper);
        if (affected <= 0) {
            throw new AppException(ResponseCode.E0001.getCode(), "skill profile not found");
        }
    }

    @Override
    public boolean softDelete(Long id, String userId) {
        LambdaUpdateWrapper<AgentSkillProfilePO> updateWrapper = new LambdaUpdateWrapper<AgentSkillProfilePO>()
                .eq(AgentSkillProfilePO::getId, id)
                .eq(AgentSkillProfilePO::getUserId, userId)
                .eq(AgentSkillProfilePO::getIsDeleted, 0)
                .set(AgentSkillProfilePO::getIsDeleted, 1)
                .set(AgentSkillProfilePO::getUpdateTime, LocalDateTime.now());
        return agentSkillProfileDao.update(null, updateWrapper) > 0;
    }

    private AgentSkillProfilePO toPO(AgentSkillProfileEntity source) {
        if (source == null) {
            return null;
        }
        AgentSkillProfilePO target = new AgentSkillProfilePO();
        target.setId(source.getId());
        target.setUserId(source.getUserId());
        target.setSkillName(source.getSkillName());
        target.setOssPath(source.getOssPath());
        return target;
    }

    private AgentSkillProfileEntity toEntity(AgentSkillProfilePO source) {
        if (source == null) {
            return null;
        }
        return AgentSkillProfileEntity.builder()
                .id(source.getId())
                .userId(source.getUserId())
                .skillName(source.getSkillName())
                .ossPath(source.getOssPath())
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
