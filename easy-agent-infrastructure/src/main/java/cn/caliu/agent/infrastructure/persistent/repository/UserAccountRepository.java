package cn.caliu.agent.infrastructure.persistent.repository;

import cn.caliu.agent.domain.user.model.entity.UserAccountEntity;
import cn.caliu.agent.domain.user.repository.IUserAccountRepository;
import cn.caliu.agent.infrastructure.persistent.dao.IUserAccountDao;
import cn.caliu.agent.infrastructure.persistent.po.UserAccountPO;
import cn.caliu.agent.types.enums.ResponseCode;
import cn.caliu.agent.types.exception.AppException;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import org.springframework.stereotype.Repository;

import javax.annotation.Resource;
import java.time.LocalDateTime;
import java.time.ZoneId;

@Repository
public class UserAccountRepository implements IUserAccountRepository {

    @Resource
    private IUserAccountDao userAccountDao;

    @Override
    public boolean existsActiveUser(String userId) {
        LambdaQueryWrapper<UserAccountPO> queryWrapper = new LambdaQueryWrapper<UserAccountPO>()
                .eq(UserAccountPO::getUserId, userId)
                .eq(UserAccountPO::getIsDeleted, 0)
                .last("LIMIT 1");
        return userAccountDao.selectCount(queryWrapper) > 0;
    }

    @Override
    public void insert(UserAccountEntity userAccount) {
        UserAccountPO po = toPO(userAccount);
        po.setIsDeleted(0);
        int affected = userAccountDao.insert(po);
        if (affected <= 0) {
            throw new AppException(ResponseCode.UN_ERROR.getCode(), "insert user account failed");
        }
    }

    @Override
    public UserAccountEntity queryByUserId(String userId) {
        LambdaQueryWrapper<UserAccountPO> queryWrapper = new LambdaQueryWrapper<UserAccountPO>()
                .eq(UserAccountPO::getUserId, userId)
                .last("LIMIT 1");
        return toEntity(userAccountDao.selectOne(queryWrapper));
    }

    @Override
    public void updateLastLoginTime(String userId, Long lastLoginTime) {
        LambdaUpdateWrapper<UserAccountPO> updateWrapper = new LambdaUpdateWrapper<UserAccountPO>()
                .eq(UserAccountPO::getUserId, userId)
                .eq(UserAccountPO::getIsDeleted, 0)
                .set(UserAccountPO::getLastLoginTime, toLocalDateTime(lastLoginTime))
                .set(UserAccountPO::getUpdateTime, LocalDateTime.now());
        userAccountDao.update(null, updateWrapper);
    }

    private UserAccountPO toPO(UserAccountEntity source) {
        if (source == null) {
            return null;
        }
        UserAccountPO target = new UserAccountPO();
        target.setUserId(source.getUserId());
        target.setNickname(source.getNickname());
        target.setPasswordHash(source.getPasswordHash());
        target.setPasswordSalt(source.getPasswordSalt());
        target.setStatus(source.getStatus());
        target.setLastLoginTime(toLocalDateTime(source.getLastLoginTime()));
        target.setIsDeleted(source.getIsDeleted());
        return target;
    }

    private UserAccountEntity toEntity(UserAccountPO source) {
        if (source == null) {
            return null;
        }
        return UserAccountEntity.builder()
                .userId(source.getUserId())
                .nickname(source.getNickname())
                .passwordHash(source.getPasswordHash())
                .passwordSalt(source.getPasswordSalt())
                .status(source.getStatus())
                .isDeleted(source.getIsDeleted())
                .lastLoginTime(toEpochMilli(source.getLastLoginTime()))
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

    private LocalDateTime toLocalDateTime(Long epochMilli) {
        if (epochMilli == null || epochMilli <= 0) {
            return null;
        }
        return LocalDateTime.ofInstant(java.time.Instant.ofEpochMilli(epochMilli), ZoneId.systemDefault());
    }
}
