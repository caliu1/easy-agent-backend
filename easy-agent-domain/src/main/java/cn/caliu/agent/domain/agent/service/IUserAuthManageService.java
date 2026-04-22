package cn.caliu.agent.domain.agent.service;

import cn.caliu.agent.domain.agent.model.valobj.UserAccountVO;

public interface IUserAuthManageService {

    UserAccountVO register(String userId, String rawPassword, String nickname);

    UserAccountVO login(String userId, String rawPassword);

    String issueToken(String userId);

}

