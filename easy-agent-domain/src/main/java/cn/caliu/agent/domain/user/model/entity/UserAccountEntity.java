package cn.caliu.agent.domain.user.model.entity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.apache.commons.lang3.StringUtils;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Locale;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserAccountEntity {

    public static final String STATUS_ACTIVE = "ACTIVE";
    public static final int MIN_PASSWORD_LENGTH = 6;
    public static final int MAX_NICKNAME_LENGTH = 64;
    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    private String userId;
    private String nickname;
    private String passwordHash;
    private String passwordSalt;
    private String status;
    private Integer isDeleted;
    private Long lastLoginTime;
    private Long createTime;
    private Long updateTime;

    public static UserAccountEntity createForRegister(String normalizedUserId, String nickname, String rawPassword) {
        PasswordCredential credential = PasswordCredential.fromRawPassword(rawPassword);
        return UserAccountEntity.builder()
                .userId(normalizedUserId)
                .nickname(resolveNickname(normalizedUserId, nickname))
                .passwordHash(credential.passwordHash)
                .passwordSalt(credential.passwordSalt)
                .status(STATUS_ACTIVE)
                .isDeleted(0)
                .build();
    }

    public boolean isLoginEnabled() {
        return Integer.valueOf(0).equals(isDeleted)
                && STATUS_ACTIVE.equalsIgnoreCase(StringUtils.defaultString(status));
    }

    public boolean passwordMatched(String rawPassword) {
        if (StringUtils.isBlank(rawPassword) || StringUtils.isBlank(passwordSalt) || StringUtils.isBlank(passwordHash)) {
            return false;
        }
        String actualHash = hashPassword(rawPassword, passwordSalt);
        return actualHash.equalsIgnoreCase(passwordHash);
    }

    public void markLogin(Long loginTime) {
        this.lastLoginTime = loginTime;
    }

    public static boolean isValidRawPassword(String rawPassword) {
        return StringUtils.isNotBlank(rawPassword) && rawPassword.length() >= MIN_PASSWORD_LENGTH;
    }

    private static String resolveNickname(String normalizedUserId, String nickname) {
        String candidate = StringUtils.trimToEmpty(nickname);
        if (StringUtils.isBlank(candidate)) {
            return normalizedUserId;
        }
        if (candidate.length() > MAX_NICKNAME_LENGTH) {
            return candidate.substring(0, MAX_NICKNAME_LENGTH);
        }
        return candidate;
    }

    private static String hashPassword(String rawPassword, String salt) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hashed = digest.digest((salt + ":" + rawPassword).getBytes(StandardCharsets.UTF_8));
            return toHex(hashed);
        } catch (Exception e) {
            throw new IllegalStateException("password hash failed", e);
        }
    }

    private static String generateSaltHex(int size) {
        byte[] bytes = new byte[size];
        SECURE_RANDOM.nextBytes(bytes);
        return toHex(bytes);
    }

    private static String toHex(byte[] bytes) {
        StringBuilder builder = new StringBuilder(bytes.length * 2);
        for (byte b : bytes) {
            builder.append(String.format(Locale.ROOT, "%02x", b));
        }
        return builder.toString();
    }

    private static class PasswordCredential {
        private final String passwordSalt;
        private final String passwordHash;

        private PasswordCredential(String passwordSalt, String passwordHash) {
            this.passwordSalt = passwordSalt;
            this.passwordHash = passwordHash;
        }

        private static PasswordCredential fromRawPassword(String rawPassword) {
            String salt = generateSaltHex(16);
            String hash = hashPassword(rawPassword, salt);
            return new PasswordCredential(salt, hash);
        }
    }

}
