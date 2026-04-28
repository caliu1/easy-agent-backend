package cn.caliu.agent.infrastructure.persistent.repository;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import cn.caliu.agent.domain.agent.model.entity.AgentConfigEntity;
import cn.caliu.agent.domain.agent.model.valobj.AgentConfigPageQueryVO;
import cn.caliu.agent.domain.agent.model.valobj.AgentConfigPageQueryResult;
import cn.caliu.agent.domain.agent.repository.IAgentConfigRepository;
import cn.caliu.agent.infrastructure.persistent.dao.IAgentConfigDao;
import cn.caliu.agent.infrastructure.persistent.po.AgentConfigPO;
import cn.caliu.agent.types.enums.ResponseCode;
import cn.caliu.agent.types.exception.AppException;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Repository;

import javax.annotation.Resource;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;
/**
 * AgentConfigRepository 仓储实现，负责领域对象与持久化对象转换。
 */

@Repository
public class AgentConfigRepository implements IAgentConfigRepository {

    private static final String STATUS_PUBLISHED = "PUBLISHED";
    private static final String SOURCE_USER = "USER";
    private static final String PLAZA_ON = "ON";
    private static final String PLAZA_OFF = "OFF";

    @Resource
    private IAgentConfigDao agentConfigDao;

    @Override
    public boolean exists(String agentId) {
        LambdaQueryWrapper<AgentConfigPO> queryWrapper = new LambdaQueryWrapper<AgentConfigPO>()
                .eq(AgentConfigPO::getAgentId, agentId)
                .eq(AgentConfigPO::getIsDeleted, 0);
        return agentConfigDao.selectCount(queryWrapper) > 0;
    }

    @Override
    public void insert(AgentConfigEntity config) {
        AgentConfigPO po = toPO(config);
        po.setIsDeleted(0);
        if (StringUtils.isBlank(po.getOwnerUserId())) {
            po.setOwnerUserId(StringUtils.trimToEmpty(po.getOperator()));
        }
        if (StringUtils.isBlank(po.getSourceType())) {
            po.setSourceType(SOURCE_USER);
        }
        if (StringUtils.isBlank(po.getPlazaStatus())) {
            po.setPlazaStatus(PLAZA_OFF);
        }
        int affected = agentConfigDao.insert(po);
        if (affected <= 0) {
            throw new AppException(ResponseCode.UN_ERROR.getCode(), "insert agent config failed");
        }
    }

    @Override
    public void update(AgentConfigEntity config) {
        LambdaUpdateWrapper<AgentConfigPO> updateWrapper = new LambdaUpdateWrapper<AgentConfigPO>()
                .eq(AgentConfigPO::getAgentId, config.getAgentId())
                .eq(AgentConfigPO::getIsDeleted, 0)
                .set(AgentConfigPO::getAppName, config.getAppName())
                .set(AgentConfigPO::getAgentName, config.getAgentName())
                .set(AgentConfigPO::getAgentDesc, config.getAgentDesc())
                .set(AgentConfigPO::getConfigJson, config.getConfigJson())
                .set(AgentConfigPO::getStatus, config.getStatus())
                .set(AgentConfigPO::getCurrentVersion, config.getCurrentVersion())
                .set(AgentConfigPO::getPublishedVersion, config.getPublishedVersion())
                .set(AgentConfigPO::getOperator, config.getOperator())
                .set(AgentConfigPO::getOwnerUserId, config.getOwnerUserId())
                .set(AgentConfigPO::getSourceType, config.getSourceType())
                .set(AgentConfigPO::getPlazaStatus, config.getPlazaStatus())
                .set(AgentConfigPO::getPlazaPublishTime, toLocalDateTime(config.getPlazaPublishTime()))
                .set(AgentConfigPO::getUpdateTime, LocalDateTime.now());
        int affected = agentConfigDao.update(null, updateWrapper);
        if (affected <= 0) {
            throw new AppException(ResponseCode.E0001.getCode(), "agent config not found: " + config.getAgentId());
        }
    }

    @Override
    public boolean softDelete(String agentId, String operator) {
        LambdaUpdateWrapper<AgentConfigPO> updateWrapper = new LambdaUpdateWrapper<AgentConfigPO>()
                .eq(AgentConfigPO::getAgentId, agentId)
                .eq(AgentConfigPO::getIsDeleted, 0)
                .set(AgentConfigPO::getIsDeleted, 1)
                .set(AgentConfigPO::getStatus, "OFFLINE")
                .set(AgentConfigPO::getPlazaStatus, PLAZA_OFF)
                .set(AgentConfigPO::getPlazaPublishTime, null)
                .set(AgentConfigPO::getOperator, operator)
                .set(AgentConfigPO::getUpdateTime, LocalDateTime.now());
        return agentConfigDao.update(null, updateWrapper) > 0;
    }

    @Override
    public AgentConfigEntity queryByAgentId(String agentId) {
        LambdaQueryWrapper<AgentConfigPO> queryWrapper = new LambdaQueryWrapper<AgentConfigPO>()
                .eq(AgentConfigPO::getAgentId, agentId)
                .eq(AgentConfigPO::getIsDeleted, 0)
                .last("LIMIT 1");
        return toVO(agentConfigDao.selectOne(queryWrapper));
    }

    @Override
    public List<AgentConfigEntity> queryPublishedList() {
        LambdaQueryWrapper<AgentConfigPO> queryWrapper = new LambdaQueryWrapper<AgentConfigPO>()
                .eq(AgentConfigPO::getIsDeleted, 0)
                .eq(AgentConfigPO::getStatus, STATUS_PUBLISHED)
                .orderByDesc(AgentConfigPO::getUpdateTime);
        List<AgentConfigPO> poList = agentConfigDao.selectList(queryWrapper);
        if (poList == null || poList.isEmpty()) {
            return Collections.emptyList();
        }
        return poList.stream().map(this::toVO).collect(Collectors.toList());
    }

    @Override
    public List<AgentConfigEntity> queryPlazaList() {
        LambdaQueryWrapper<AgentConfigPO> queryWrapper = new LambdaQueryWrapper<AgentConfigPO>()
                .eq(AgentConfigPO::getIsDeleted, 0)
                .eq(AgentConfigPO::getStatus, STATUS_PUBLISHED)
                .eq(AgentConfigPO::getPlazaStatus, PLAZA_ON)
                .orderByDesc(AgentConfigPO::getPlazaPublishTime)
                .orderByDesc(AgentConfigPO::getUpdateTime);
        List<AgentConfigPO> poList = agentConfigDao.selectList(queryWrapper);
        if (poList == null || poList.isEmpty()) {
            return Collections.emptyList();
        }
        return poList.stream().map(this::toVO).collect(Collectors.toList());
    }

    @Override
    public AgentConfigPageQueryResult queryPage(AgentConfigPageQueryVO queryVO) {
        long pageNo = normalizePageNo(queryVO.getPageNo());
        long pageSize = normalizePageSize(queryVO.getPageSize());
        String agentId = trimToNull(queryVO.getAgentId());
        String appName = trimToNull(queryVO.getAppName());
        String agentName = trimToNull(queryVO.getAgentName());
        String status = trimToNull(queryVO.getStatus());
        String operator = trimToNull(queryVO.getOperator());
        String ownerUserId = trimToNull(queryVO.getOwnerUserId());
        String sourceType = trimToNull(queryVO.getSourceType());
        String plazaStatus = trimToNull(queryVO.getPlazaStatus());

        LambdaQueryWrapper<AgentConfigPO> queryWrapper = new LambdaQueryWrapper<AgentConfigPO>()
                .eq(AgentConfigPO::getIsDeleted, 0)
                .like(StringUtils.isNotBlank(agentId), AgentConfigPO::getAgentId, agentId)
                .like(StringUtils.isNotBlank(appName), AgentConfigPO::getAppName, appName)
                .like(StringUtils.isNotBlank(agentName), AgentConfigPO::getAgentName, agentName)
                .eq(StringUtils.isNotBlank(status), AgentConfigPO::getStatus, status)
                .like(StringUtils.isNotBlank(operator), AgentConfigPO::getOperator, operator)
                .like(StringUtils.isNotBlank(ownerUserId), AgentConfigPO::getOwnerUserId, ownerUserId)
                .eq(StringUtils.isNotBlank(sourceType), AgentConfigPO::getSourceType, sourceType)
                .eq(StringUtils.isNotBlank(plazaStatus), AgentConfigPO::getPlazaStatus, plazaStatus)
                .orderByDesc(AgentConfigPO::getUpdateTime);

        // Use MyBatis-Plus native pagination to avoid handwritten paging SQL.
        Page<AgentConfigPO> page = agentConfigDao.selectPage(new Page<>(pageNo, pageSize), queryWrapper);
        List<AgentConfigEntity> records = page.getRecords() == null
                ? Collections.emptyList()
                : page.getRecords().stream().map(this::toVO).collect(Collectors.toList());

        return AgentConfigPageQueryResult.builder()
                .pageNo(page.getCurrent())
                .pageSize(page.getSize())
                .total(page.getTotal())
                .records(records)
                .build();
    }

    private AgentConfigPO toPO(AgentConfigEntity source) {
        if (source == null) {
            return null;
        }
        AgentConfigPO target = new AgentConfigPO();
        target.setAgentId(source.getAgentId());
        target.setAppName(source.getAppName());
        target.setAgentName(source.getAgentName());
        target.setAgentDesc(source.getAgentDesc());
        target.setConfigJson(source.getConfigJson());
        target.setStatus(source.getStatus());
        target.setCurrentVersion(source.getCurrentVersion());
        target.setPublishedVersion(source.getPublishedVersion());
        target.setOperator(source.getOperator());
        target.setOwnerUserId(source.getOwnerUserId());
        target.setSourceType(source.getSourceType());
        target.setPlazaStatus(source.getPlazaStatus());
        target.setPlazaPublishTime(toLocalDateTime(source.getPlazaPublishTime()));
        return target;
    }

    private AgentConfigEntity toVO(AgentConfigPO source) {
        if (source == null) {
            return null;
        }
        return AgentConfigEntity.builder()
                .agentId(source.getAgentId())
                .appName(source.getAppName())
                .agentName(source.getAgentName())
                .agentDesc(source.getAgentDesc())
                .configJson(source.getConfigJson())
                .status(source.getStatus())
                .currentVersion(source.getCurrentVersion())
                .publishedVersion(source.getPublishedVersion())
                .operator(source.getOperator())
                .ownerUserId(resolveOwnerUserId(source))
                .sourceType(StringUtils.defaultIfBlank(source.getSourceType(), SOURCE_USER))
                .plazaStatus(StringUtils.defaultIfBlank(source.getPlazaStatus(), PLAZA_OFF))
                .plazaPublishTime(toEpochMilli(source.getPlazaPublishTime()))
                .createTime(toEpochMilli(source.getCreateTime()))
                .updateTime(toEpochMilli(source.getUpdateTime()))
                .build();
    }

    private String resolveOwnerUserId(AgentConfigPO source) {
        if (source == null) {
            return "";
        }
        if (StringUtils.isNotBlank(source.getOwnerUserId())) {
            return source.getOwnerUserId().trim();
        }
        return StringUtils.trimToEmpty(source.getOperator());
    }

    private Long toEpochMilli(LocalDateTime time) {
        if (time == null) {
            return null;
        }
        return time.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli();
    }

    private LocalDateTime toLocalDateTime(Long epochMilli) {
        if (epochMilli == null || epochMilli <= 0) {
            return null;
        }
        return LocalDateTime.ofInstant(java.time.Instant.ofEpochMilli(epochMilli), ZoneId.systemDefault());
    }

    private long normalizePageNo(Long pageNo) {
        return pageNo == null || pageNo < 1 ? 1L : pageNo;
    }

    private long normalizePageSize(Long pageSize) {
        if (pageSize == null || pageSize < 1) {
            return 10L;
        }
        return Math.min(pageSize, 200L);
    }

    private String trimToNull(String source) {
        if (source == null) {
            return null;
        }
        String trimmed = source.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

}


