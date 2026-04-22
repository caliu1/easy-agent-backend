package cn.caliu.agent.domain.agent.repository;

import cn.caliu.agent.domain.agent.model.valobj.UserAccountVO;

public interface IUserAccountRepository {

    boolean existsActiveUser(String userId);

    void insert(UserAccountVO userAccount);

    UserAccountVO queryByUserId(String userId);

    void updateLastLoginTime(String userId, Long lastLoginTime);

}

