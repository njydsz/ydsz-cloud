package com.njydsz.pmis.common.exception.custom;

import org.springframework.http.HttpStatus;

import com.njydsz.pmis.common.exception.code.UnifiedExceptionCode;
import com.njydsz.pmis.common.exception.core.ExceptionInfo;
import com.njydsz.pmis.common.exception.enums.ExceptionCategory;
import com.njydsz.pmis.common.exception.enums.ExceptionCode;
import com.njydsz.pmis.common.exception.enums.ExceptionLevel;

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
 * @author ydsz-pmis-team
 * @since 1.0.0
 * 
 * @since 3.5.0
 * @see UnifiedExceptionCode#SERVICE_DEGRADED
 * @see ExceptionCategory#INFRASTRUCTURE
 * @see ExceptionLevel#WARN
 */
@ToString(callSuper = true)
public class DegradeException extends AbstractYdszException {

    private static final long serialVersionUID = 1L;

    public DegradeException() {
        super();
        this.httpStatus = HttpStatus.SERVICE_UNAVAILABLE.value();
        this.level = ExceptionLevel.WARN;
        this.category = ExceptionCategory.INFRASTRUCTURE;
    }

    public DegradeException(String key) {
        super();
        this.httpStatus = HttpStatus.SERVICE_UNAVAILABLE.value();
        this.level = ExceptionLevel.WARN;
        this.category = ExceptionCategory.INFRASTRUCTURE;
        this.code = UnifiedExceptionCode.SERVICE_DEGRADED.getCode();
        this.key = key;
        this.params = normalizeParams(new Object[]{});
        this.message = null;
        this.messageKey = key;
        this.messageParams = this.params;
    }

    public DegradeException(ExceptionCode exceptionCode) {
        super();
        this.httpStatus = exceptionCode.getHttpStatus();
        this.level = ExceptionLevel.WARN;
        this.category = ExceptionCategory.INFRASTRUCTURE;
        this.code = exceptionCode.getCode();
        this.key = exceptionCode.getKey();
        this.params = normalizeParams(new Object[]{});
        this.message = null;
        this.messageKey = exceptionCode.getKey();
        this.messageParams = this.params;
    }

    public DegradeException(String key, Object[] params) {
        super();
        this.httpStatus = HttpStatus.SERVICE_UNAVAILABLE.value();
        this.level = ExceptionLevel.WARN;
        this.category = ExceptionCategory.INFRASTRUCTURE;
        this.code = UnifiedExceptionCode.SERVICE_DEGRADED.getCode();
        this.key = key;
        this.params = normalizeParams(params);
        this.message = null;
        this.messageKey = key;
        this.messageParams = this.params;
    }

    public DegradeException(ExceptionCode exceptionCode, Object[] params) {
        super();
        this.httpStatus = exceptionCode.getHttpStatus();
        this.level = ExceptionLevel.WARN;
        this.category = ExceptionCategory.INFRASTRUCTURE;
        this.code = exceptionCode.getCode();
        this.key = exceptionCode.getKey();
        this.params = normalizeParams(params);
        this.message = null;
        this.messageKey = exceptionCode.getKey();
        this.messageParams = this.params;
    }

    public DegradeException(String code, String key) {
        super();
        this.httpStatus = HttpStatus.SERVICE_UNAVAILABLE.value();
        this.level = ExceptionLevel.WARN;
        this.category = ExceptionCategory.INFRASTRUCTURE;
        this.code = code;
        this.key = key;
        this.params = normalizeParams(new Object[]{});
        this.message = null;
        this.messageKey = key;
        this.messageParams = this.params;
    }

    public DegradeException(String code, String key, Object[] params) {
        super();
        this.httpStatus = HttpStatus.SERVICE_UNAVAILABLE.value();
        this.level = ExceptionLevel.WARN;
        this.category = ExceptionCategory.INFRASTRUCTURE;
        this.code = code;
        this.key = key;
        this.params = normalizeParams(params);
        this.message = null;
        this.messageKey = key;
        this.messageParams = this.params;
    }

    public DegradeException(Throwable cause) {
        super(cause);
        this.httpStatus = HttpStatus.SERVICE_UNAVAILABLE.value();
        this.level = ExceptionLevel.WARN;
        this.category = ExceptionCategory.INFRASTRUCTURE;
        this.code = UnifiedExceptionCode.SERVICE_DEGRADED.getCode();
    }

    public DegradeException(String code, Throwable cause) {
        super(cause);
        this.httpStatus = HttpStatus.SERVICE_UNAVAILABLE.value();
        this.level = ExceptionLevel.WARN;
        this.category = ExceptionCategory.INFRASTRUCTURE;
        this.code = code;
    }

    public DegradeException(ExceptionCode exceptionCode, Throwable cause) {
        super(null, cause);
        this.httpStatus = exceptionCode.getHttpStatus();
        this.level = ExceptionLevel.WARN;
        this.category = ExceptionCategory.INFRASTRUCTURE;
        this.code = exceptionCode.getCode();
        this.key = exceptionCode.getKey();
        this.params = normalizeParams(new Object[]{});
        this.message = null;
        this.messageKey = exceptionCode.getKey();
        this.messageParams = this.params;
    }

    public DegradeException(String code, String key, Throwable cause) {
        super(null, cause);
        this.httpStatus = HttpStatus.SERVICE_UNAVAILABLE.value();
        this.level = ExceptionLevel.WARN;
        this.category = ExceptionCategory.INFRASTRUCTURE;
        this.code = code;
        this.key = key;
        this.params = normalizeParams(new Object[]{});
        this.message = null;
        this.messageKey = key;
        this.messageParams = this.params;
    }

    public DegradeException(String code, String key, Object[] params, Throwable cause) {
        super(null, cause);
        this.httpStatus = HttpStatus.SERVICE_UNAVAILABLE.value();
        this.level = ExceptionLevel.WARN;
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
        protected DegradeException doBuild(String code, String key, Object[] params, int httpStatus,
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
