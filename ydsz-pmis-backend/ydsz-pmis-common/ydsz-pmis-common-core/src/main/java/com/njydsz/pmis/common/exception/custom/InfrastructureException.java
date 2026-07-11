package com.njydsz.pmis.common.exception.custom;

import com.njydsz.pmis.common.exception.code.ExceptionCode;
import com.njydsz.pmis.common.exception.code.UnifiedExceptionCode;

/**
 * 基础设施异常 —— Redis 连接失败、数据库超时、消息队列不可用等。
 * <p>
 * 对标 remi-comm InfrastructureException，默认 503 Service Unavailable。
 * </p>
 *
 * @author njydsz
 * @since 1.0.0
 */
public class InfrastructureException extends AbstractPmisException {

    private static final long serialVersionUID = 1L;

    public InfrastructureException(String message) {
        super(UnifiedExceptionCode.SERVICE_UNAVAILABLE, message);
    }

    public InfrastructureException(ExceptionCode code, String message) {
        super(code, message);
    }

    public InfrastructureException(ExceptionCode code, String message, Object... args) {
        super(code, message, args);
    }

    public static InfrastructureException of(String component, String reason) {
        return new InfrastructureException(
                UnifiedExceptionCode.SERVICE_UNAVAILABLE,
                "Infrastructure component '" + component + "' is unavailable: " + reason
        );
    }

    public static InfrastructureException of(String component, String reason, Throwable cause) {
        InfrastructureException ex = of(component, reason);
        ex.initCause(cause);
        return ex;
    }
}
