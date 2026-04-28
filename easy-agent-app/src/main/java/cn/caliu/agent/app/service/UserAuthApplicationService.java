package cn.caliu.agent.app.service;

import cn.caliu.agent.api.application.IUserAuthApplicationService;
import cn.caliu.agent.api.dto.user.auth.response.UserAuthResponseDTO;
import cn.caliu.agent.api.dto.user.auth.request.UserLoginRequestDTO;
import cn.caliu.agent.api.dto.user.auth.request.UserRegisterRequestDTO;
import cn.caliu.agent.domain.user.model.entity.UserAccountEntity;
import cn.caliu.agent.domain.user.service.IUserAuthManageService;
import cn.caliu.agent.types.enums.ResponseCode;
import cn.caliu.agent.types.exception.AppException;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;

/**
 * 用户认证应用服务实现。
 *
 * 负责注册/登录用例编排，并统一组装登录态响应。
 */
@Service
public class UserAuthApplicationService implements IUserAuthApplicationService {

    @Resource
    private IUserAuthManageService userAuthManageService;

    @Override
    public UserAuthResponseDTO register(UserRegisterRequestDTO requestDTO) {
        validateRequest(requestDTO);

        UserAccountEntity account = userAuthManageService.register(
                requestDTO.getUserId(),
                requestDTO.getPassword(),
                requestDTO.getNickname()
        );
        String token = userAuthManageService.issueToken(account.getUserId());
        return toResponse(account, token);
    }

    @Override
    public UserAuthResponseDTO login(UserLoginRequestDTO requestDTO) {
        validateRequest(requestDTO);

        UserAccountEntity account = userAuthManageService.login(requestDTO.getUserId(), requestDTO.getPassword());
        String token = userAuthManageService.issueToken(account.getUserId());
        return toResponse(account, token);
    }

    private UserAuthResponseDTO toResponse(UserAccountEntity source, String token) {
        UserAuthResponseDTO dto = new UserAuthResponseDTO();
        dto.setUserId(source.getUserId());
        dto.setNickname(source.getNickname());
        dto.setToken(token);
        return dto;
    }

    private void validateRequest(Object requestDTO) {
        if (requestDTO == null) {
            throw new AppException(ResponseCode.ILLEGAL_PARAMETER.getCode(), "request is null");
        }
    }

}

