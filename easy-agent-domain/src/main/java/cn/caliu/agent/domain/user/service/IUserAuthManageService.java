package cn.caliu.agent.domain.user.service;

import cn.caliu.agent.domain.user.model.entity.UserAccountEntity;

public interface IUserAuthManageService {

    UserAccountEntity register(String userId, String rawPassword, String nickname);

    UserAccountEntity login(String userId, String rawPassword);

    String issueToken(String userId);

}
