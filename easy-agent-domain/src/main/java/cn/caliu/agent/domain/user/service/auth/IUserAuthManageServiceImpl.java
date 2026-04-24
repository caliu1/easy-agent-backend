package cn.caliu.agent.domain.user.service.auth;

import cn.caliu.agent.domain.user.model.entity.UserAccountEntity;
import cn.caliu.agent.domain.user.model.valobj.UserId;
import cn.caliu.agent.domain.user.repository.IUserAccountRepository;
import cn.caliu.agent.domain.user.service.IUserAuthManageService;
import cn.caliu.agent.types.enums.ResponseCode;
import cn.caliu.agent.types.exception.AppException;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Base64;
import java.util.UUID;

@Service
public class IUserAuthManageServiceImpl implements IUserAuthManageService {

    @Resource
    private IUserAccountRepository userAccountRepository;

    @Override
    public synchronized UserAccountEntity register(String userId, String rawPassword, String nickname) {
        UserId normalizedUserId = UserId.of(userId);
        validatePassword(rawPassword);

        if (userAccountRepository.queryByUserId(normalizedUserId.getValue()) != null) {
            throw new AppException(ResponseCode.ILLEGAL_PARAMETER.getCode(), "user already exists");
        }

        UserAccountEntity userAccount = UserAccountEntity.createForRegister(normalizedUserId.getValue(), nickname, rawPassword);

        userAccountRepository.insert(userAccount);
        UserAccountEntity created = userAccountRepository.queryByUserId(normalizedUserId.getValue());
        if (created == null) {
            throw new AppException(ResponseCode.UN_ERROR.getCode(), "register failed");
        }
        return created;
    }

    @Override
    public UserAccountEntity login(String userId, String rawPassword) {
        UserId normalizedUserId = UserId.of(userId);
        if (StringUtils.isBlank(rawPassword)) {
            throw new AppException(ResponseCode.ILLEGAL_PARAMETER.getCode(), "password is blank");
        }

        UserAccountEntity account = userAccountRepository.queryByUserId(normalizedUserId.getValue());
        if (account == null || !account.isLoginEnabled()) {
            throw new AppException(ResponseCode.ILLEGAL_PARAMETER.getCode(), "invalid username or password");
        }

        if (!account.passwordMatched(rawPassword)) {
            throw new AppException(ResponseCode.ILLEGAL_PARAMETER.getCode(), "invalid username or password");
        }

        long now = System.currentTimeMillis();
        userAccountRepository.updateLastLoginTime(normalizedUserId.getValue(), now);
        account.markLogin(now);
        return account;
    }

    @Override
    public String issueToken(String userId) {
        String payload = userId + ":" + Instant.now().toEpochMilli() + ":" + UUID.randomUUID();
        return Base64.getUrlEncoder()
                .withoutPadding()
                .encodeToString(payload.getBytes(StandardCharsets.UTF_8));
    }

    private void validatePassword(String rawPassword) {
        if (!UserAccountEntity.isValidRawPassword(rawPassword)) {
            throw new AppException(ResponseCode.ILLEGAL_PARAMETER.getCode(), "password must be at least 6 characters");
        }
    }

}
