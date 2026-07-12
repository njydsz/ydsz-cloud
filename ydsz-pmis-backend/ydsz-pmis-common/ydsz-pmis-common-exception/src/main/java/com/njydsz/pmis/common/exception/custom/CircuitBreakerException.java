package com.njydsz.pmis.common.exception.custom;

import com.njydsz.pmis.common.exception.code.ExceptionCode;
import com.njydsz.pmis.common.exception.enums.ExceptionCategory;
import com.njydsz.pmis.common.exception.enums.ExceptionLevel;

/**
 * 熔断异常
 *
 * <p>当熔断器处于 OPEN 状态时抛出，表示下游服务不可用。
 *
 * @author ydsz-pmis-team
 * @since 2.0.0
 */
public class CircuitBreakerException extends AbstractPmisException {

    public CircuitBreakerException(String service) {
        super("Circuit breaker open for service: " + service);
        setHttpStatus(503);
        setLevel(ExceptionLevel.CRITICAL);
        setCategory(ExceptionCategory.INFRASTRUCTURE);
    }

    public CircuitBreakerException(String service, Throwable cause) {
        super("Circuit breaker open for service: " + service, cause);
        setHttpStatus(503);
        setLevel(ExceptionLevel.CRITICAL);
        setCategory(ExceptionCategory.INFRASTRUCTURE);
    }

    public CircuitBreakerException(ExceptionCode code, String message) {
        super(code, message);
        setHttpStatus(503);
        setLevel(ExceptionLevel.CRITICAL);
        setCategory(ExceptionCategory.INFRASTRUCTURE);
    }
}
