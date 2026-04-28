package cn.caliu.agent.api;

import cn.caliu.agent.api.dto.user.auth.response.UserAuthResponseDTO;
import cn.caliu.agent.api.dto.user.auth.request.UserLoginRequestDTO;
import cn.caliu.agent.api.dto.user.auth.request.UserRegisterRequestDTO;
import cn.caliu.agent.api.response.Response;

/**
 * 用户认证对外接口。
 *
 * 提供注册与登录能力，返回统一认证响应（含 token）。
 */
public interface IUserAuthService {

    Response<UserAuthResponseDTO> register(UserRegisterRequestDTO requestDTO);

    Response<UserAuthResponseDTO> login(UserLoginRequestDTO requestDTO);

}


