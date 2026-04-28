package cn.caliu.agent.domain.user.service;

import cn.caliu.agent.domain.user.model.entity.UserAccountEntity;

/**
 * 用户认证领域服务接口。
 *
 * 包含账号注册、登录校验与令牌签发能力。
 */
public interface IUserAuthManageService {

    UserAccountEntity register(String userId, String rawPassword, String nickname);

    UserAccountEntity login(String userId, String rawPassword);

    String issueToken(String userId);

}

