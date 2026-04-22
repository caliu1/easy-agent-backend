package cn.caliu.agent.domain.agent.service.auth;

import cn.caliu.agent.domain.agent.model.valobj.UserAccountVO;
import cn.caliu.agent.domain.agent.repository.IUserAccountRepository;
import cn.caliu.agent.domain.agent.service.IUserAuthManageService;
import cn.caliu.agent.types.enums.ResponseCode;
import cn.caliu.agent.types.exception.AppException;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.Base64;
import java.util.UUID;
import java.util.regex.Pattern;

@Service
public class IUserAuthManageServiceImpl implements IUserAuthManageService {

    private static final String STATUS_ACTIVE = "ACTIVE";
    private static final int MIN_PASSWORD_LENGTH = 6;
    private static final int MAX_NICKNAME_LENGTH = 64;
    private static final Pattern USER_ID_PATTERN = Pattern.compile("^[a-zA-Z0-9_-]{3,32}$");
    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    @Resource
    private IUserAccountRepository userAccountRepository;

    @Override
    public synchronized UserAccountVO register(String userId, String rawPassword, String nickname) {
        String normalizedUserId = normalizeUserId(userId);
        validateUserId(normalizedUserId);
        validatePassword(rawPassword);

        if (userAccountRepository.queryByUserId(normalizedUserId) != null) {
            throw new AppException(ResponseCode.ILLEGAL_PARAMETER.getCode(), "user already exists");
        }

        String resolvedNickname = resolveNickname(normalizedUserId, nickname);
        String passwordSalt = generateSaltHex(16);
        String passwordHash = hashPassword(rawPassword, passwordSalt);

        UserAccountVO userAccount = UserAccountVO.builder()
                .userId(normalizedUserId)
                .nickname(resolvedNickname)
                .passwordHash(passwordHash)
                .passwordSalt(passwordSalt)
                .status(STATUS_ACTIVE)
                .isDeleted(0)
                .build();

        userAccountRepository.insert(userAccount);
        UserAccountVO created = userAccountRepository.queryByUserId(normalizedUserId);
        if (created == null) {
            throw new AppException(ResponseCode.UN_ERROR.getCode(), "register failed");
        }
        return created;
    }

    @Override
    public UserAccountVO login(String userId, String rawPassword) {
        String normalizedUserId = normalizeUserId(userId);
        validateUserId(normalizedUserId);
        if (StringUtils.isBlank(rawPassword)) {
            throw new AppException(ResponseCode.ILLEGAL_PARAMETER.getCode(), "password is blank");
        }

        UserAccountVO account = userAccountRepository.queryByUserId(normalizedUserId);
        if (account == null
                || account.getIsDeleted() == null
                || account.getIsDeleted() != 0
                || !STATUS_ACTIVE.equalsIgnoreCase(StringUtils.defaultString(account.getStatus()))) {
            throw new AppException(ResponseCode.ILLEGAL_PARAMETER.getCode(), "invalid username or password");
        }

        String expectedHash = hashPassword(rawPassword, StringUtils.defaultString(account.getPasswordSalt()));
        if (!expectedHash.equalsIgnoreCase(StringUtils.defaultString(account.getPasswordHash()))) {
            throw new AppException(ResponseCode.ILLEGAL_PARAMETER.getCode(), "invalid username or password");
        }

        long now = System.currentTimeMillis();
        userAccountRepository.updateLastLoginTime(normalizedUserId, now);
        account.setLastLoginTime(now);
        return account;
    }

    @Override
    public String issueToken(String userId) {
        String payload = userId + ":" + Instant.now().toEpochMilli() + ":" + UUID.randomUUID();
        return Base64.getUrlEncoder()
                .withoutPadding()
                .encodeToString(payload.getBytes(StandardCharsets.UTF_8));
    }

    private String normalizeUserId(String userId) {
        return StringUtils.trimToEmpty(userId).toLowerCase();
    }

    private void validateUserId(String userId) {
        if (!USER_ID_PATTERN.matcher(userId).matches()) {
            throw new AppException(ResponseCode.ILLEGAL_PARAMETER.getCode(), "userId must be 3-32 chars of letters/numbers/_/-");
        }
    }

    private void validatePassword(String rawPassword) {
        if (StringUtils.isBlank(rawPassword) || rawPassword.length() < MIN_PASSWORD_LENGTH) {
            throw new AppException(ResponseCode.ILLEGAL_PARAMETER.getCode(), "password must be at least 6 characters");
        }
    }

    private String resolveNickname(String normalizedUserId, String nickname) {
        String candidate = StringUtils.trimToEmpty(nickname);
        if (StringUtils.isBlank(candidate)) {
            return normalizedUserId;
        }
        if (candidate.length() > MAX_NICKNAME_LENGTH) {
            return candidate.substring(0, MAX_NICKNAME_LENGTH);
        }
        return candidate;
    }

    private String generateSaltHex(int size) {
        byte[] bytes = new byte[size];
        SECURE_RANDOM.nextBytes(bytes);
        return toHex(bytes);
    }

    private String hashPassword(String rawPassword, String salt) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hashed = digest.digest((salt + ":" + rawPassword).getBytes(StandardCharsets.UTF_8));
            return toHex(hashed);
        } catch (Exception e) {
            throw new AppException(ResponseCode.UN_ERROR.getCode(), "password hash failed");
        }
    }

    private String toHex(byte[] bytes) {
        StringBuilder builder = new StringBuilder(bytes.length * 2);
        for (byte b : bytes) {
            builder.append(String.format("%02x", b));
        }
        return builder.toString();
    }

}
