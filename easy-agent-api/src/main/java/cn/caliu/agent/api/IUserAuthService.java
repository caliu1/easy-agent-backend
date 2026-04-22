package cn.caliu.agent.api;

import cn.caliu.agent.api.dto.UserAuthResponseDTO;
import cn.caliu.agent.api.dto.UserLoginRequestDTO;
import cn.caliu.agent.api.dto.UserRegisterRequestDTO;
import cn.caliu.agent.api.response.Response;

public interface IUserAuthService {

    Response<UserAuthResponseDTO> register(UserRegisterRequestDTO requestDTO);

    Response<UserAuthResponseDTO> login(UserLoginRequestDTO requestDTO);

}

