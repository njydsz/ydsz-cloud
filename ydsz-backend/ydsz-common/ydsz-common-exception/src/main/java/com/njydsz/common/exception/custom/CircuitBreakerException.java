package com.njydsz.common.exception.custom;

import org.springframework.http.HttpStatus;

import com.njydsz.common.exception.code.UnifiedExceptionCode;
import com.njydsz.common.exception.core.ExceptionInfo;
import com.njydsz.common.exception.enums.ExceptionCategory;
import com.njydsz.common.exception.enums.ExceptionCode;
import com.njydsz.common.exception.enums.ExceptionLevel;

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
 * @author ydsz-team
 * @since 1.0.0
 * @see UnifiedExceptionCode
 * @see ExceptionCategory#INFRASTRUCTURE
 */
@ToString(callSuper = true)
public class CircuitBreakerException extends AbstractYdszException {

    private static final long serialVersionUID = 1L;

    public CircuitBreakerException() {
        super();
        initDefaults(HttpStatus.SERVICE_UNAVAILABLE.value(), ExceptionLevel.ERROR, ExceptionCategory.INFRASTRUCTURE);
    }

    public CircuitBreakerException(String key) {
        super();
        init(UnifiedExceptionCode.CIRCUIT_BREAKER_OPEN.getCode(), key, new Object[]{}, HttpStatus.SERVICE_UNAVAILABLE.value(), ExceptionLevel.ERROR, ExceptionCategory.INFRASTRUCTURE);
    }

    public CircuitBreakerException(ExceptionCode exceptionCode) {
        super();
        init(exceptionCode, new Object[]{}, ExceptionLevel.ERROR, ExceptionCategory.INFRASTRUCTURE);
    }

    public CircuitBreakerException(String key, Object[] params) {
        super();
        init(UnifiedExceptionCode.CIRCUIT_BREAKER_OPEN.getCode(), key, params, HttpStatus.SERVICE_UNAVAILABLE.value(), ExceptionLevel.ERROR, ExceptionCategory.INFRASTRUCTURE);
    }

    public CircuitBreakerException(ExceptionCode exceptionCode, Object[] params) {
        super();
        init(exceptionCode, params, ExceptionLevel.ERROR, ExceptionCategory.INFRASTRUCTURE);
    }

    public CircuitBreakerException(String code, String key) {
        super();
        init(code, key, new Object[]{}, HttpStatus.SERVICE_UNAVAILABLE.value(), ExceptionLevel.ERROR, ExceptionCategory.INFRASTRUCTURE);
    }

    public CircuitBreakerException(String code, String key, Object[] params) {
        super();
        init(code, key, params, HttpStatus.SERVICE_UNAVAILABLE.value(), ExceptionLevel.ERROR, ExceptionCategory.INFRASTRUCTURE);
    }

    public CircuitBreakerException(Throwable cause) {
        super(cause);
        initDefaults(HttpStatus.SERVICE_UNAVAILABLE.value(), ExceptionLevel.ERROR, ExceptionCategory.INFRASTRUCTURE);
        this.code = UnifiedExceptionCode.CIRCUIT_BREAKER_OPEN.getCode();
    }

    public CircuitBreakerException(String code, Throwable cause) {
        super(cause);
        initDefaults(HttpStatus.SERVICE_UNAVAILABLE.value(), ExceptionLevel.ERROR, ExceptionCategory.INFRASTRUCTURE);
        this.code = code;
    }

    public CircuitBreakerException(ExceptionCode exceptionCode, Throwable cause) {
        super(null, cause);
        init(exceptionCode, new Object[]{}, ExceptionLevel.ERROR, ExceptionCategory.INFRASTRUCTURE);
    }

    public CircuitBreakerException(String code, String key, Throwable cause) {
        super(null, cause);
        init(code, key, new Object[]{}, HttpStatus.SERVICE_UNAVAILABLE.value(), ExceptionLevel.ERROR, ExceptionCategory.INFRASTRUCTURE);
    }

    public CircuitBreakerException(String code, String key, Object[] params, Throwable cause) {
        super(null, cause);
        init(code, key, params, HttpStatus.SERVICE_UNAVAILABLE.value(), ExceptionLevel.ERROR, ExceptionCategory.INFRASTRUCTURE);
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

        @Override
        protected CircuitBreakerExceptionBuilder self() {
            return this;
        }

        public CircuitBreakerExceptionBuilder() {
            super();
            this.code = UnifiedExceptionCode.CIRCUIT_BREAKER_OPEN.getCode();
            this.httpStatus = HttpStatus.SERVICE_UNAVAILABLE.value();
            this.level = ExceptionLevel.ERROR;
            this.category = ExceptionCategory.INFRASTRUCTURE;
        }

        @Override
        protected CircuitBreakerException doBuild(String code, String subCode, String key, Object[] params, int httpStatus,
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
