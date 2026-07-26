package com.njydsz.common.exception.custom;

import org.springframework.http.HttpStatus;

import com.njydsz.common.exception.code.UnifiedExceptionCode;
import com.njydsz.common.exception.core.ExceptionInfo;
import com.njydsz.common.exception.enums.ExceptionCategory;
import com.njydsz.common.exception.enums.ExceptionCode;
import com.njydsz.common.exception.enums.ExceptionLevel;

import lombok.ToString;

/**
 * 服务降级异常类
 *
 * <p>用于封装服务降级异常，当系统负载过高或下游服务不可用时主动降级处理。
 * 默认 HTTP 状态码为 503（SERVICE_UNAVAILABLE），异常分类为 INFRASTRUCTURE，
 * 异常级别为 WARN（降级不是错误，是预期内的保护行为）。
 *
 * <p><b>与 CircuitBreakerException 的区别：</b>
 * <ul>
 *   <li>熔断（Circuit Breaker）是<b>被动触发</b>，当下游服务故障率超过阈值时自动切换到熔断状态</li>
 *   <li>降级（Degrade）是<b>主动策略</b>，基于预见性保护机制，在系统压力增大或预判风险时主动启用降级</li>
 * </ul>
 *
 * <p><b>使用示例：</b>
 * <pre>{@code
 * throw new DegradeException();
 * throw new DegradeException(UnifiedExceptionCode.SERVICE_DEGRADED);
 * throw new DegradeException("service.degraded");
 * throw new DegradeException(UnifiedExceptionCode.SERVICE_DEGRADED, cause);
 * throw DegradeException.of(UnifiedExceptionCode.SERVICE_DEGRADED).cause(cause).build();
 * }</pre>
 *
 * @author ydsz-team
 * @since 1.0.0
 * @see UnifiedExceptionCode#SERVICE_DEGRADED
 * @see ExceptionCategory#INFRASTRUCTURE
 * @see ExceptionLevel#WARN
 */
@ToString(callSuper = true)
public class DegradeException extends AbstractYdszException {

    private static final long serialVersionUID = 1L;

    /**
     * 默认构造函数，初始化为 503 Service Unavailable / WARN / INFRASTRUCTURE
     */
    public DegradeException() {
        super();
        initDefaults(HttpStatus.SERVICE_UNAVAILABLE.value(), ExceptionLevel.WARN, ExceptionCategory.INFRASTRUCTURE);
    }

    /**
     * 使用国际化消息键构造服务降级异常
     *
     * @param key 国际化消息键
     */
    public DegradeException(String key) {
        super();
        init(UnifiedExceptionCode.SERVICE_DEGRADED.getCode(), key, new Object[]{}, HttpStatus.SERVICE_UNAVAILABLE.value(), ExceptionLevel.WARN, ExceptionCategory.INFRASTRUCTURE);
    }

    /**
     * 使用异常码枚举构造服务降级异常
     *
     * @param exceptionCode 异常码枚举
     */
    public DegradeException(ExceptionCode exceptionCode) {
        super();
        init(exceptionCode, new Object[]{}, ExceptionLevel.WARN, ExceptionCategory.INFRASTRUCTURE);
    }

    /**
     * 使用国际化消息键和参数构造服务降级异常
     *
     * @param key    国际化消息键
     * @param params 消息参数
     */
    public DegradeException(String key, Object[] params) {
        super();
        init(UnifiedExceptionCode.SERVICE_DEGRADED.getCode(), key, params, HttpStatus.SERVICE_UNAVAILABLE.value(), ExceptionLevel.WARN, ExceptionCategory.INFRASTRUCTURE);
    }

    /**
     * 使用异常码枚举和参数构造服务降级异常
     *
     * @param exceptionCode 异常码枚举
     * @param params        消息参数
     */
    public DegradeException(ExceptionCode exceptionCode, Object[] params) {
        super();
        init(exceptionCode, params, ExceptionLevel.WARN, ExceptionCategory.INFRASTRUCTURE);
    }

    /**
     * 使用自定义错误码和消息键构造服务降级异常
     *
     * @param code 错误码字符串
     * @param key  国际化消息键
     */
    public DegradeException(String code, String key) {
        super();
        init(code, key, new Object[]{}, HttpStatus.SERVICE_UNAVAILABLE.value(), ExceptionLevel.WARN, ExceptionCategory.INFRASTRUCTURE);
    }

    /**
     * 使用自定义错误码、消息键和参数构造服务降级异常
     *
     * @param code   错误码字符串
     * @param key    国际化消息键
     * @param params 消息参数
     */
    public DegradeException(String code, String key, Object[] params) {
        super();
        init(code, key, params, HttpStatus.SERVICE_UNAVAILABLE.value(), ExceptionLevel.WARN, ExceptionCategory.INFRASTRUCTURE);
    }

    /**
     * 使用原始异常构造服务降级异常
     *
     * @param cause 原始异常
     */
    public DegradeException(Throwable cause) {
        super(cause);
        initDefaults(HttpStatus.SERVICE_UNAVAILABLE.value(), ExceptionLevel.WARN, ExceptionCategory.INFRASTRUCTURE);
        this.code = UnifiedExceptionCode.SERVICE_DEGRADED.getCode();
    }

    /**
     * 使用自定义错误码和原始异常构造服务降级异常
     *
     * @param code  错误码字符串
     * @param cause 原始异常
     */
    public DegradeException(String code, Throwable cause) {
        super(cause);
        initDefaults(HttpStatus.SERVICE_UNAVAILABLE.value(), ExceptionLevel.WARN, ExceptionCategory.INFRASTRUCTURE);
        this.code = code;
    }

    /**
     * 使用异常码枚举和原始异常构造服务降级异常
     *
     * @param exceptionCode 异常码枚举
     * @param cause         原始异常
     */
    public DegradeException(ExceptionCode exceptionCode, Throwable cause) {
        super(null, cause);
        init(exceptionCode, new Object[]{}, ExceptionLevel.WARN, ExceptionCategory.INFRASTRUCTURE);
    }

    /**
     * 使用自定义错误码、消息键和原始异常构造服务降级异常
     *
     * @param code  错误码字符串
     * @param key   国际化消息键
     * @param cause 原始异常
     */
    public DegradeException(String code, String key, Throwable cause) {
        super(null, cause);
        init(code, key, new Object[]{}, HttpStatus.SERVICE_UNAVAILABLE.value(), ExceptionLevel.WARN, ExceptionCategory.INFRASTRUCTURE);
    }

    /**
     * 使用自定义错误码、消息键、参数和原始异常构造服务降级异常
     *
     * @param code   错误码字符串
     * @param key    国际化消息键
     * @param params 消息参数
     * @param cause  原始异常
     */
    public DegradeException(String code, String key, Object[] params, Throwable cause) {
        super(null, cause);
        init(code, key, params, HttpStatus.SERVICE_UNAVAILABLE.value(), ExceptionLevel.WARN, ExceptionCategory.INFRASTRUCTURE);
    }

    public ExceptionInfo toExceptionInfo() {
        return buildExceptionInfo();
    }

    public static DegradeExceptionBuilder builder() {
        return new DegradeExceptionBuilder();
    }

    public static DegradeException of(String key) {
        return new DegradeException(key);
    }

    /**
     * 根据异常码创建服务降级异常
     *
     * @param exceptionCode 异常码枚举
     * @return 服务降级异常实例
     */
    public static DegradeException of(ExceptionCode exceptionCode) {
        return new DegradeException(exceptionCode);
    }

    public static DegradeException of(String code, String key) {
        return new DegradeException(code, key);
    }

    public static class DegradeExceptionBuilder extends YdszExceptionBuilder<DegradeException, DegradeExceptionBuilder> {

        @Override
        protected DegradeExceptionBuilder self() {
            return this;
        }

        public DegradeExceptionBuilder() {
            super();
            this.code = UnifiedExceptionCode.SERVICE_DEGRADED.getCode();
            this.httpStatus = HttpStatus.SERVICE_UNAVAILABLE.value();
            this.level = ExceptionLevel.WARN;
            this.category = ExceptionCategory.INFRASTRUCTURE;
        }

        @Override
        protected DegradeException doBuild(String code, String subCode, String key, Object[] params, int httpStatus,
                                          ExceptionLevel level, ExceptionCategory category,
                                          Throwable cause, String path, Object extData, String message) {
            DegradeException exception;
            if (cause != null) {
                exception = new DegradeException(code, key, params, cause);
            } else {
                exception = new DegradeException(code, key, params);
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
