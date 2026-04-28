package cn.caliu.agent.types.exception;

import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 业务异常基类。
 *
 * 特点：
 * 1. 统一携带 code + info，便于跨层透传。
 * 2. 继承 RuntimeException，支持事务回滚与简化调用栈处理。
 */
@EqualsAndHashCode(callSuper = true)
@Data
public class AppException extends RuntimeException {

    private static final long serialVersionUID = 5317680961212299217L;

    /** 业务错误码。 */
    private String code;

    /** 面向调用方的错误信息。 */
    private String info;

    public AppException(String code) {
        super(code);
        this.code = code;
        this.info = code;
    }

    public AppException(String code, Throwable cause) {
        super(code, cause);
        this.code = code;
        this.info = code;
    }

    public AppException(String code, String message) {
        super(message);
        this.code = code;
        this.info = message;
    }

    public AppException(String code, String message, Throwable cause) {
        super(message, cause);
        this.code = code;
        this.info = message;
    }

    @Override
    public String toString() {
        return "cn.caliu.agent.types.exception.AppException{" +
                "code='" + code + '\'' +
                ", info='" + info + '\'' +
                '}';
    }
}

