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

    /**
     * 默认构造函数，初始化为 503 Service Unavailable / ERROR / INFRASTRUCTURE
     */
    public CircuitBreakerException() {
        super();
        initDefaults(HttpStatus.SERVICE_UNAVAILABLE.value(), ExceptionLevel.ERROR, ExceptionCategory.INFRASTRUCTURE);
    }

    /**
     * 使用国际化消息键构造熔断器异常
     *
     * @param key 国际化消息键
     */
    public CircuitBreakerException(String key) {
        super();
        init(UnifiedExceptionCode.CIRCUIT_BREAKER_OPEN.getCode(), key, new Object[]{}, HttpStatus.SERVICE_UNAVAILABLE.value(), ExceptionLevel.ERROR, ExceptionCategory.INFRASTRUCTURE);
    }

    /**
     * 使用异常码枚举构造熔断器异常
     *
     * @param exceptionCode 异常码枚举
     */
    public CircuitBreakerException(ExceptionCode exceptionCode) {
        super();
        init(exceptionCode, new Object[]{}, ExceptionLevel.ERROR, ExceptionCategory.INFRASTRUCTURE);
    }

    /**
     * 使用国际化消息键和参数构造熔断器异常
     *
     * @param key    国际化消息键
     * @param params 消息参数
     */
    public CircuitBreakerException(String key, Object[] params) {
        super();
        init(UnifiedExceptionCode.CIRCUIT_BREAKER_OPEN.getCode(), key, params, HttpStatus.SERVICE_UNAVAILABLE.value(), ExceptionLevel.ERROR, ExceptionCategory.INFRASTRUCTURE);
    }

    /**
     * 使用异常码枚举和参数构造熔断器异常
     *
     * @param exceptionCode 异常码枚举
     * @param params        消息参数
     */
    public CircuitBreakerException(ExceptionCode exceptionCode, Object[] params) {
        super();
        init(exceptionCode, params, ExceptionLevel.ERROR, ExceptionCategory.INFRASTRUCTURE);
    }

    /**
     * 使用自定义错误码和消息键构造熔断器异常
     *
     * @param code 错误码字符串
     * @param key  国际化消息键
     */
    public CircuitBreakerException(String code, String key) {
        super();
        init(code, key, new Object[]{}, HttpStatus.SERVICE_UNAVAILABLE.value(), ExceptionLevel.ERROR, ExceptionCategory.INFRASTRUCTURE);
    }

    /**
     * 使用自定义错误码、消息键和参数构造熔断器异常
     *
     * @param code   错误码字符串
     * @param key    国际化消息键
     * @param params 消息参数
     */
    public CircuitBreakerException(String code, String key, Object[] params) {
        super();
        init(code, key, params, HttpStatus.SERVICE_UNAVAILABLE.value(), ExceptionLevel.ERROR, ExceptionCategory.INFRASTRUCTURE);
    }

    /**
     * 使用原始异常构造熔断器异常
     *
     * @param cause 原始异常
     */
    public CircuitBreakerException(Throwable cause) {
        super(cause);
        initDefaults(HttpStatus.SERVICE_UNAVAILABLE.value(), ExceptionLevel.ERROR, ExceptionCategory.INFRASTRUCTURE);
        this.code = UnifiedExceptionCode.CIRCUIT_BREAKER_OPEN.getCode();
    }

    /**
     * 使用自定义错误码和原始异常构造熔断器异常
     *
     * @param code  错误码字符串
     * @param cause 原始异常
     */
    public CircuitBreakerException(String code, Throwable cause) {
        super(cause);
        initDefaults(HttpStatus.SERVICE_UNAVAILABLE.value(), ExceptionLevel.ERROR, ExceptionCategory.INFRASTRUCTURE);
        this.code = code;
    }

    /**
     * 使用异常码枚举和原始异常构造熔断器异常
     *
     * @param exceptionCode 异常码枚举
     * @param cause         原始异常
     */
    public CircuitBreakerException(ExceptionCode exceptionCode, Throwable cause) {
        super(null, cause);
        init(exceptionCode, new Object[]{}, ExceptionLevel.ERROR, ExceptionCategory.INFRASTRUCTURE);
    }

    /**
     * 使用自定义错误码、消息键和原始异常构造熔断器异常
     *
     * @param code  错误码字符串
     * @param key   国际化消息键
     * @param cause 原始异常
     */
    public CircuitBreakerException(String code, String key, Throwable cause) {
        super(null, cause);
        init(code, key, new Object[]{}, HttpStatus.SERVICE_UNAVAILABLE.value(), ExceptionLevel.ERROR, ExceptionCategory.INFRASTRUCTURE);
    }

    /**
     * 使用自定义错误码、消息键、参数和原始异常构造熔断器异常
     *
     * @param code   错误码字符串
     * @param key    国际化消息键
     * @param params 消息参数
     * @param cause  原始异常
     */
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
