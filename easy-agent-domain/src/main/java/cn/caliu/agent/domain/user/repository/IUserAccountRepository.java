package cn.caliu.agent.domain.user.repository;

import cn.caliu.agent.domain.user.model.entity.UserAccountEntity;

public interface IUserAccountRepository {

    boolean existsActiveUser(String userId);

    void insert(UserAccountEntity userAccount);

    UserAccountEntity queryByUserId(String userId);

    void updateLastLoginTime(String userId, Long lastLoginTime);

}
