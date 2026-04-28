package cn.caliu.agent.api.application;

import cn.caliu.agent.api.dto.user.auth.response.UserAuthResponseDTO;
import cn.caliu.agent.api.dto.user.auth.request.UserLoginRequestDTO;
import cn.caliu.agent.api.dto.user.auth.request.UserRegisterRequestDTO;

/**
 * 用户认证应用服务接口。
 *
 * 负责注册/登录用例编排，不直接处理 HTTP 协议细节。
 */
public interface IUserAuthApplicationService {

    UserAuthResponseDTO register(UserRegisterRequestDTO requestDTO);

    UserAuthResponseDTO login(UserLoginRequestDTO requestDTO);

}

