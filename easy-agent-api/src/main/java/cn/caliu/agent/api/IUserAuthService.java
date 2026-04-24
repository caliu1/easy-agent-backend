package cn.caliu.agent.api;

import cn.caliu.agent.api.dto.user.auth.response.UserAuthResponseDTO;
import cn.caliu.agent.api.dto.user.auth.request.UserLoginRequestDTO;
import cn.caliu.agent.api.dto.user.auth.request.UserRegisterRequestDTO;
import cn.caliu.agent.api.response.Response;

public interface IUserAuthService {

    Response<UserAuthResponseDTO> register(UserRegisterRequestDTO requestDTO);

    Response<UserAuthResponseDTO> login(UserLoginRequestDTO requestDTO);

}


