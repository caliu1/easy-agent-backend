package cn.caliu.agent.api.application;

import cn.caliu.agent.api.dto.user.auth.response.UserAuthResponseDTO;
import cn.caliu.agent.api.dto.user.auth.request.UserLoginRequestDTO;
import cn.caliu.agent.api.dto.user.auth.request.UserRegisterRequestDTO;

public interface IUserAuthApplicationService {

    UserAuthResponseDTO register(UserRegisterRequestDTO requestDTO);

    UserAuthResponseDTO login(UserLoginRequestDTO requestDTO);

}

