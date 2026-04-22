package cn.caliu.agent.trigger.http;

import cn.caliu.agent.api.IUserAuthService;
import cn.caliu.agent.api.dto.UserAuthResponseDTO;
import cn.caliu.agent.api.dto.UserLoginRequestDTO;
import cn.caliu.agent.api.dto.UserRegisterRequestDTO;
import cn.caliu.agent.api.response.Response;
import cn.caliu.agent.domain.agent.model.valobj.UserAccountVO;
import cn.caliu.agent.domain.agent.service.IUserAuthManageService;
import cn.caliu.agent.types.enums.ResponseCode;
import cn.caliu.agent.types.exception.AppException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RestController;

import javax.annotation.Resource;

@Slf4j
@RestController
@RequestMapping("/api/v1/")
@CrossOrigin("*")
public class UserAuthController implements IUserAuthService {

    @Resource
    private IUserAuthManageService userAuthManageService;

    @RequestMapping(value = "user_register", method = RequestMethod.POST)
    @Override
    public Response<UserAuthResponseDTO> register(@RequestBody UserRegisterRequestDTO requestDTO) {
        try {
            if (requestDTO == null) {
                throw new AppException(ResponseCode.ILLEGAL_PARAMETER.getCode(), "request is null");
            }

            UserAccountVO account = userAuthManageService.register(
                    requestDTO.getUserId(),
                    requestDTO.getPassword(),
                    requestDTO.getNickname()
            );
            String token = userAuthManageService.issueToken(account.getUserId());
            return success(toResponse(account, token));
        } catch (AppException e) {
            log.error("user register failed", e);
            return fail(e.getCode(), e.getInfo());
        } catch (Exception e) {
            log.error("user register failed", e);
            return fail(ResponseCode.UN_ERROR.getCode(), ResponseCode.UN_ERROR.getInfo());
        }
    }

    @RequestMapping(value = "user_login", method = RequestMethod.POST)
    @Override
    public Response<UserAuthResponseDTO> login(@RequestBody UserLoginRequestDTO requestDTO) {
        try {
            if (requestDTO == null) {
                throw new AppException(ResponseCode.ILLEGAL_PARAMETER.getCode(), "request is null");
            }

            UserAccountVO account = userAuthManageService.login(requestDTO.getUserId(), requestDTO.getPassword());
            String token = userAuthManageService.issueToken(account.getUserId());
            return success(toResponse(account, token));
        } catch (AppException e) {
            log.error("user login failed", e);
            return fail(e.getCode(), e.getInfo());
        } catch (Exception e) {
            log.error("user login failed", e);
            return fail(ResponseCode.UN_ERROR.getCode(), ResponseCode.UN_ERROR.getInfo());
        }
    }

    private UserAuthResponseDTO toResponse(UserAccountVO source, String token) {
        UserAuthResponseDTO dto = new UserAuthResponseDTO();
        dto.setUserId(source.getUserId());
        dto.setNickname(source.getNickname());
        dto.setToken(token);
        return dto;
    }

    private Response<UserAuthResponseDTO> success(UserAuthResponseDTO data) {
        return Response.<UserAuthResponseDTO>builder()
                .code(ResponseCode.SUCCESS.getCode())
                .info(ResponseCode.SUCCESS.getInfo())
                .data(data)
                .build();
    }

    private Response<UserAuthResponseDTO> fail(String code, String info) {
        return Response.<UserAuthResponseDTO>builder()
                .code(code)
                .info(info)
                .build();
    }

}
