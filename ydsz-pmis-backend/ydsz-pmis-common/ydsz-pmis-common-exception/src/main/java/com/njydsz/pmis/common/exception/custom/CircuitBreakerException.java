package com.njydsz.pmis.common.exception.custom;

import org.springframework.http.HttpStatus;
import com.njydsz.pmis.common.exception.code.UnifiedExceptionCode;
import com.njydsz.pmis.common.exception.core.ExceptionInfo;
import com.njydsz.pmis.common.exception.enums.ExceptionCategory;
import com.njydsz.pmis.common.exception.enums.ExceptionCode;
import com.njydsz.pmis.common.exception.enums.ExceptionLevel;
import lombok.ToString;

/**
 * 熔断器异常类
 *
 * <p>用于封装熔断器开启时的异常，当下游服务故障率超过阈值触发熔断时抛出。
 * 默认 HTTP 状态码为 503（Service Unavailable），异常分类为 INFRASTRUCTURE。
 *
 * <p><b>使用示例：</b>
 * <pre>{@code
 * throw new CircuitBreakerException(UnifiedExceptionCode.CIRCUIT_BREAKER_OPEN);
 * throw new CircuitBreakerException("circuit.breaker.open");
 * throw new CircuitBreakerException(UnifiedExceptionCode.CIRCUIT_BREAKER_OPEN, cause);
 * throw CircuitBreakerException.of(UnifiedExceptionCode.CIRCUIT_BREAKER_OPEN).cause(cause).build();
 * }</pre>
 *
 * @author Marvin Lee
 * @email limw1888@126.com
 * @version 3.5.0
 * @since 3.0.0
 * @see UnifiedExceptionCode
 * @see ExceptionCategory#INFRASTRUCTURE
 */
@ToString(callSuper = true)
public class CircuitBreakerException extends AbstractYdszException {

    private static final long serialVersionUID = 1L;

    public CircuitBreakerException() {
        super();
        this.httpStatus = HttpStatus.SERVICE_UNAVAILABLE.value();
        this.level = ExceptionLevel.ERROR;
        this.category = ExceptionCategory.INFRASTRUCTURE;
    }

    public CircuitBreakerException(String key) {
        super();
        this.httpStatus = HttpStatus.SERVICE_UNAVAILABLE.value();
        this.level = ExceptionLevel.ERROR;
        this.category = ExceptionCategory.INFRASTRUCTURE;
        this.code = UnifiedExceptionCode.CIRCUIT_BREAKER_OPEN.getCode();
        this.key = key;
        this.params = normalizeParams(new Object[]{});
        this.message = null;
        this.messageKey = key;
        this.messageParams = this.params;
    }

    public CircuitBreakerException(ExceptionCode exceptionCode) {
        super();
        this.httpStatus = exceptionCode.getHttpStatus();
        this.level = ExceptionLevel.ERROR;
        this.category = ExceptionCategory.INFRASTRUCTURE;
        this.code = exceptionCode.getCode();
        this.key = exceptionCode.getKey();
        this.params = normalizeParams(new Object[]{});
        this.message = null;
        this.messageKey = exceptionCode.getKey();
        this.messageParams = this.params;
    }

    public CircuitBreakerException(String key, Object[] params) {
        super();
        this.httpStatus = HttpStatus.SERVICE_UNAVAILABLE.value();
        this.level = ExceptionLevel.ERROR;
        this.category = ExceptionCategory.INFRASTRUCTURE;
        this.code = UnifiedExceptionCode.CIRCUIT_BREAKER_OPEN.getCode();
        this.key = key;
        this.params = normalizeParams(params);
        this.message = null;
        this.messageKey = key;
        this.messageParams = this.params;
    }

    public CircuitBreakerException(ExceptionCode exceptionCode, Object[] params) {
        super();
        this.httpStatus = exceptionCode.getHttpStatus();
        this.level = ExceptionLevel.ERROR;
        this.category = ExceptionCategory.INFRASTRUCTURE;
        this.code = exceptionCode.getCode();
        this.key = exceptionCode.getKey();
        this.params = normalizeParams(params);
        this.message = null;
        this.messageKey = exceptionCode.getKey();
        this.messageParams = this.params;
    }

    public CircuitBreakerException(String code, String key) {
        super();
        this.httpStatus = HttpStatus.SERVICE_UNAVAILABLE.value();
        this.level = ExceptionLevel.ERROR;
        this.category = ExceptionCategory.INFRASTRUCTURE;
        this.code = code;
        this.key = key;
        this.params = normalizeParams(new Object[]{});
        this.message = null;
        this.messageKey = key;
        this.messageParams = this.params;
    }

    public CircuitBreakerException(String code, String key, Object[] params) {
        super();
        this.httpStatus = HttpStatus.SERVICE_UNAVAILABLE.value();
        this.level = ExceptionLevel.ERROR;
        this.category = ExceptionCategory.INFRASTRUCTURE;
        this.code = code;
        this.key = key;
        this.params = normalizeParams(params);
        this.message = null;
        this.messageKey = key;
        this.messageParams = this.params;
    }

    public CircuitBreakerException(Throwable cause) {
        super(cause);
        this.httpStatus = HttpStatus.SERVICE_UNAVAILABLE.value();
        this.level = ExceptionLevel.ERROR;
        this.category = ExceptionCategory.INFRASTRUCTURE;
        this.code = UnifiedExceptionCode.CIRCUIT_BREAKER_OPEN.getCode();
    }

    public CircuitBreakerException(String code, Throwable cause) {
        super(cause);
        this.httpStatus = HttpStatus.SERVICE_UNAVAILABLE.value();
        this.level = ExceptionLevel.ERROR;
        this.category = ExceptionCategory.INFRASTRUCTURE;
        this.code = code;
    }

    public CircuitBreakerException(ExceptionCode exceptionCode, Throwable cause) {
        super(null, cause);
        this.httpStatus = exceptionCode.getHttpStatus();
        this.level = ExceptionLevel.ERROR;
        this.category = ExceptionCategory.INFRASTRUCTURE;
        this.code = exceptionCode.getCode();
        this.key = exceptionCode.getKey();
        this.params = normalizeParams(new Object[]{});
        this.message = null;
        this.messageKey = exceptionCode.getKey();
        this.messageParams = this.params;
    }

    public CircuitBreakerException(String code, String key, Throwable cause) {
        super(null, cause);
        this.httpStatus = HttpStatus.SERVICE_UNAVAILABLE.value();
        this.level = ExceptionLevel.ERROR;
        this.category = ExceptionCategory.INFRASTRUCTURE;
        this.code = code;
        this.key = key;
        this.params = normalizeParams(new Object[]{});
        this.message = null;
        this.messageKey = key;
        this.messageParams = this.params;
    }

    public CircuitBreakerException(String code, String key, Object[] params, Throwable cause) {
        super(null, cause);
        this.httpStatus = HttpStatus.SERVICE_UNAVAILABLE.value();
        this.level = ExceptionLevel.ERROR;
        this.category = ExceptionCategory.INFRASTRUCTURE;
        this.code = code;
        this.key = key;
        this.params = normalizeParams(params);
        this.message = null;
        this.messageKey = key;
        this.messageParams = this.params;
    }

    public ExceptionInfo toExceptionInfo() {
        return buildExceptionInfo();
    }

    public static CircuitBreakerExceptionBuilder builder() {
        return new CircuitBreakerExceptionBuilder();
    }

    public static CircuitBreakerException of(String key) {
        return new CircuitBreakerException(key);
    }

    /**
     * 根据异常码创建熔断器异常
     *
     * @param exceptionCode 异常码枚举
     * @return 熔断器异常实例
     */
    public static CircuitBreakerException of(ExceptionCode exceptionCode) {
        return new CircuitBreakerException(exceptionCode);
    }

    public static CircuitBreakerException of(String code, String key) {
        return new CircuitBreakerException(code, key);
    }

    public static class CircuitBreakerExceptionBuilder extends YdszExceptionBuilder<CircuitBreakerException, CircuitBreakerExceptionBuilder> {

        public CircuitBreakerExceptionBuilder() {
            super();
            this.code = UnifiedExceptionCode.CIRCUIT_BREAKER_OPEN.getCode();
            this.httpStatus = HttpStatus.SERVICE_UNAVAILABLE.value();
            this.level = ExceptionLevel.ERROR;
            this.category = ExceptionCategory.INFRASTRUCTURE;
        }

        @Override
        protected CircuitBreakerException doBuild(String code, String key, Object[] params, int httpStatus,
                                                  ExceptionLevel level, ExceptionCategory category,
                                                  Throwable cause, String path, Object extData, String message) {
            CircuitBreakerException exception;
            if (cause != null) {
                exception = new CircuitBreakerException(code, key, params, cause);
            } else {
                exception = new CircuitBreakerException(code, key, params);
            }
            exception.setHttpStatus(httpStatus);
            exception.setLevel(level);
            exception.setCategory(category);
            exception.setPath(path);
            exception.setExtData(extData);
            return exception;
        }
    }
}
