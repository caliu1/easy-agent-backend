package cn.caliu.agent.domain.user.model.valobj;

import cn.caliu.agent.types.enums.ResponseCode;
import cn.caliu.agent.types.exception.AppException;
import org.apache.commons.lang3.StringUtils;

import java.util.Locale;
import java.util.regex.Pattern;

public final class UserId {

    private static final Pattern USER_ID_PATTERN = Pattern.compile("^[a-zA-Z0-9_-]{3,32}$");

    private final String value;

    private UserId(String value) {
        this.value = value;
    }

    public static UserId of(String rawUserId) {
        String normalized = StringUtils.trimToEmpty(rawUserId).toLowerCase(Locale.ROOT);
        if (!USER_ID_PATTERN.matcher(normalized).matches()) {
            throw new AppException(ResponseCode.ILLEGAL_PARAMETER.getCode(), "userId must be 3-32 chars of letters/numbers/_/-");
        }
        return new UserId(normalized);
    }

    public String getValue() {
        return value;
    }

    @Override
    public String toString() {
        return value;
    }
}

