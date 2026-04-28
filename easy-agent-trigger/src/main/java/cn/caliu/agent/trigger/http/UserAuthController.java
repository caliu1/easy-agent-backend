package cn.caliu.agent.trigger.http;

import cn.caliu.agent.api.IUserAuthService;
import cn.caliu.agent.api.application.IUserAuthApplicationService;
import cn.caliu.agent.api.dto.user.auth.response.UserAuthResponseDTO;
import cn.caliu.agent.api.dto.user.auth.request.UserLoginRequestDTO;
import cn.caliu.agent.api.dto.user.auth.request.UserRegisterRequestDTO;
import cn.caliu.agent.api.response.Response;
import cn.caliu.agent.types.enums.ResponseCode;
import cn.caliu.agent.types.exception.AppException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RestController;

import javax.annotation.Resource;

/**
 * 用户认证 HTTP 控制器。
 *
 * 提供用户注册与登录接口，返回统一认证响应。
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/")
@CrossOrigin("*")
public class UserAuthController implements IUserAuthService {

    @Resource
    private IUserAuthApplicationService userAuthApplicationService;

    @RequestMapping(value = "user_register", method = RequestMethod.POST)
    @Override
    public Response<UserAuthResponseDTO> register(@RequestBody UserRegisterRequestDTO requestDTO) {
        try {
            return success(userAuthApplicationService.register(requestDTO));
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
            return success(userAuthApplicationService.login(requestDTO));
        } catch (AppException e) {
            log.error("user login failed", e);
            return fail(e.getCode(), e.getInfo());
        } catch (Exception e) {
            log.error("user login failed", e);
            return fail(ResponseCode.UN_ERROR.getCode(), ResponseCode.UN_ERROR.getInfo());
        }
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
