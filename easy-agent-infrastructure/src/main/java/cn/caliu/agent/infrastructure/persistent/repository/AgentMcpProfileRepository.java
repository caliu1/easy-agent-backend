package cn.caliu.agent.infrastructure.persistent.repository;

import cn.caliu.agent.domain.agent.model.entity.AgentMcpProfileEntity;
import cn.caliu.agent.domain.agent.repository.IAgentMcpProfileRepository;
import cn.caliu.agent.infrastructure.persistent.dao.IAgentMcpProfileDao;
import cn.caliu.agent.infrastructure.persistent.po.AgentMcpProfilePO;
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
 * MCP 配置档案仓储实现。
 */
@Repository
public class AgentMcpProfileRepository implements IAgentMcpProfileRepository {
    private static final String SYSTEM_USER_ID = "system";

    @Resource
    private IAgentMcpProfileDao agentMcpProfileDao;

    @Override
    public List<AgentMcpProfileEntity> queryByUserId(String userId) {
        LambdaQueryWrapper<AgentMcpProfilePO> queryWrapper = new LambdaQueryWrapper<AgentMcpProfilePO>()
                .and(wrapper -> wrapper
                        .eq(AgentMcpProfilePO::getUserId, userId)
                        .or()
                        .eq(AgentMcpProfilePO::getUserId, SYSTEM_USER_ID))
                .eq(AgentMcpProfilePO::getIsDeleted, 0)
                .orderByDesc(AgentMcpProfilePO::getUpdateTime);
        List<AgentMcpProfilePO> poList = agentMcpProfileDao.selectList(queryWrapper);
        if (poList == null || poList.isEmpty()) {
            return Collections.emptyList();
        }
        return poList.stream().map(this::toEntity).collect(Collectors.toList());
    }

    @Override
    public AgentMcpProfileEntity queryById(Long id, String userId) {
        LambdaQueryWrapper<AgentMcpProfilePO> queryWrapper = new LambdaQueryWrapper<AgentMcpProfilePO>()
                .eq(AgentMcpProfilePO::getId, id)
                .eq(AgentMcpProfilePO::getUserId, userId)
                .eq(AgentMcpProfilePO::getIsDeleted, 0)
                .last("LIMIT 1");
        return toEntity(agentMcpProfileDao.selectOne(queryWrapper));
    }

    @Override
    public boolean existsByMcpName(String userId, String mcpName, String mcpType, Long excludeId) {
        LambdaQueryWrapper<AgentMcpProfilePO> queryWrapper = new LambdaQueryWrapper<AgentMcpProfilePO>()
                .eq(AgentMcpProfilePO::getUserId, userId)
                .eq(AgentMcpProfilePO::getMcpName, mcpName)
                .eq(AgentMcpProfilePO::getMcpType, mcpType)
                .eq(AgentMcpProfilePO::getIsDeleted, 0)
                .ne(excludeId != null, AgentMcpProfilePO::getId, excludeId);
        return agentMcpProfileDao.selectCount(queryWrapper) > 0;
    }

    @Override
    public void insert(AgentMcpProfileEntity entity) {
        AgentMcpProfilePO po = toPO(entity);
        po.setIsDeleted(0);
        int affected = agentMcpProfileDao.insert(po);
        if (affected <= 0) {
            throw new AppException(ResponseCode.UN_ERROR.getCode(), "insert mcp profile failed");
        }
        entity.setId(po.getId());
    }

    @Override
    public void update(AgentMcpProfileEntity entity) {
        LambdaUpdateWrapper<AgentMcpProfilePO> updateWrapper = new LambdaUpdateWrapper<AgentMcpProfilePO>()
                .eq(AgentMcpProfilePO::getId, entity.getId())
                .eq(AgentMcpProfilePO::getUserId, entity.getUserId())
                .eq(AgentMcpProfilePO::getIsDeleted, 0)
                .set(AgentMcpProfilePO::getMcpType, entity.getType())
                .set(AgentMcpProfilePO::getMcpName, entity.getName())
                .set(AgentMcpProfilePO::getMcpDesc, entity.getDescription())
                .set(AgentMcpProfilePO::getConfigJson, entity.getConfigJson())
                .set(AgentMcpProfilePO::getUpdateTime, LocalDateTime.now());
        int affected = agentMcpProfileDao.update(null, updateWrapper);
        if (affected <= 0) {
            throw new AppException(ResponseCode.E0001.getCode(), "mcp profile not found");
        }
    }

    @Override
    public boolean softDelete(Long id, String userId) {
        LambdaUpdateWrapper<AgentMcpProfilePO> updateWrapper = new LambdaUpdateWrapper<AgentMcpProfilePO>()
                .eq(AgentMcpProfilePO::getId, id)
                .eq(AgentMcpProfilePO::getUserId, userId)
                .eq(AgentMcpProfilePO::getIsDeleted, 0)
                .set(AgentMcpProfilePO::getIsDeleted, 1)
                .set(AgentMcpProfilePO::getUpdateTime, LocalDateTime.now());
        return agentMcpProfileDao.update(null, updateWrapper) > 0;
    }

    private AgentMcpProfilePO toPO(AgentMcpProfileEntity source) {
        if (source == null) {
            return null;
        }
        AgentMcpProfilePO target = new AgentMcpProfilePO();
        target.setId(source.getId());
        target.setUserId(source.getUserId());
        target.setMcpType(source.getType());
        target.setMcpName(source.getName());
        target.setMcpDesc(source.getDescription());
        target.setConfigJson(source.getConfigJson());
        return target;
    }

    private AgentMcpProfileEntity toEntity(AgentMcpProfilePO source) {
        if (source == null) {
            return null;
        }
        return AgentMcpProfileEntity.builder()
                .id(source.getId())
                .userId(source.getUserId())
                .type(source.getMcpType())
                .name(source.getMcpName())
                .description(source.getMcpDesc())
                .configJson(source.getConfigJson())
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
