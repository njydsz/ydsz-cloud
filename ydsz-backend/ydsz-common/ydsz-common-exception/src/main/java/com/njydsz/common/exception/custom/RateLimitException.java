package com.njydsz.common.exception.custom;

import org.springframework.http.HttpStatus;

import com.njydsz.common.exception.code.UnifiedExceptionCode;
import com.njydsz.common.exception.core.ExceptionInfo;
import com.njydsz.common.exception.enums.ExceptionCategory;
import com.njydsz.common.exception.enums.ExceptionCode;
import com.njydsz.common.exception.enums.ExceptionLevel;

import lombok.ToString;

/**
 * 限流异常类
 *
 * <p>用于封装请求被限流、触发熔断等场景。
 * 默认 HTTP 状态码为 429（Too Many Requests），异常分类为 RATE_LIMIT。
 *
 * <p><b>使用示例：</b>
 * <pre>{@code
 * throw new RateLimitException(UnifiedExceptionCode.RATE_LIMIT_EXCEEDED);
 * throw RateLimitException.of(UnifiedExceptionCode.RATE_LIMIT_EXCEEDED).build();
 * }</pre>
 *
 * @author ydsz-team
 * @since 1.0.0
 */
@ToString(callSuper = true)
public class RateLimitException extends AbstractYdszException {

    private static final long serialVersionUID = 1L;

    /**
     * 默认构造函数，初始化为 429 Too Many Requests / WARN / RATE_LIMIT
     */
    public RateLimitException() {
        super();
        initDefaults(HttpStatus.TOO_MANY_REQUESTS.value(), ExceptionLevel.WARN, ExceptionCategory.RATE_LIMIT);
    }

    /**
     * 使用异常码枚举构造限流异常
     *
     * @param exceptionCode 异常码枚举
     */
    public RateLimitException(ExceptionCode exceptionCode) {
        super();
        init(exceptionCode, new Object[]{}, ExceptionLevel.WARN, ExceptionCategory.RATE_LIMIT);
    }

    /**
     * 使用国际化消息键构造限流异常
     *
     * @param key 国际化消息键
     */
    public RateLimitException(String key) {
        super();
        init(UnifiedExceptionCode.FAIL.getCode(), key, new Object[]{}, HttpStatus.TOO_MANY_REQUESTS.value(), ExceptionLevel.WARN, ExceptionCategory.RATE_LIMIT);
    }

    /**
     * 使用异常码枚举和参数构造限流异常
     *
     * @param exceptionCode 异常码枚举
     * @param params        消息参数
     */
    public RateLimitException(ExceptionCode exceptionCode, Object[] params) {
        super();
        init(exceptionCode, params, ExceptionLevel.WARN, ExceptionCategory.RATE_LIMIT);
    }

    /**
     * 使用原始异常构造限流异常
     *
     * @param cause 原始异常
     */
    public RateLimitException(Throwable cause) {
        super(cause);
        initDefaults(HttpStatus.TOO_MANY_REQUESTS.value(), ExceptionLevel.WARN, ExceptionCategory.RATE_LIMIT);
        this.code = UnifiedExceptionCode.FAIL.getCode();
    }

    public ExceptionInfo toExceptionInfo() {
        return buildExceptionInfo();
    }

    public static RateLimitException of(ExceptionCode exceptionCode) {
        return new RateLimitException(exceptionCode);
    }

    public static RateLimitExceptionBuilder builder() {
        return new RateLimitExceptionBuilder();
    }

    /**
     * 限流异常构建器
     */
    public static class RateLimitExceptionBuilder extends YdszExceptionBuilder<RateLimitException, RateLimitExceptionBuilder> {

        @Override
        protected RateLimitExceptionBuilder self() {
            return this;
        }

        public RateLimitExceptionBuilder() {
            super();
            this.code = UnifiedExceptionCode.FAIL.getCode();
            this.httpStatus = HttpStatus.TOO_MANY_REQUESTS.value();
            this.level = ExceptionLevel.WARN;
            this.category = ExceptionCategory.RATE_LIMIT;
        }

        @Override
        protected RateLimitException doBuild(String code, String subCode, String key, Object[] params, int httpStatus,
                                              ExceptionLevel level, ExceptionCategory category,
                                              Throwable cause, String path, Object extData, String message) {
            RateLimitException exception = new RateLimitException();
            exception.initFields(code, key, params);
            exception.setSubCode(subCode);
            exception.setHttpStatus(httpStatus);
            exception.setLevel(level);
            exception.setCategory(category);
            exception.setPath(path);
            exception.setExtData(extData);
            if (cause != null) {
                exception.initCause(cause);
            }
            return exception;
        }
    }
}
