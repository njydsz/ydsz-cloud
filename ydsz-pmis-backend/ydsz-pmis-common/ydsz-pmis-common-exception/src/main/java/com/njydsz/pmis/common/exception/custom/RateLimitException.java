package com.njydsz.pmis.common.exception.custom;

import org.springframework.http.HttpStatus;
import com.njydsz.pmis.common.exception.code.UnifiedExceptionCode;
import com.njydsz.pmis.common.exception.core.ExceptionInfo;
import com.njydsz.pmis.common.exception.enums.ExceptionCategory;
import com.njydsz.pmis.common.exception.enums.ExceptionCode;
import com.njydsz.pmis.common.exception.enums.ExceptionLevel;
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
 * @author Marvin Lee
 * @email limw1888@126.com
 * @version 3.5.0
 * @since 3.0.0
 */
@ToString(callSuper = true)
public class RateLimitException extends AbstractRemiException {

    private static final long serialVersionUID = 1L;

    public RateLimitException() {
        super();
        this.httpStatus = HttpStatus.TOO_MANY_REQUESTS.value();
        this.level = ExceptionLevel.WARN;
        this.category = ExceptionCategory.RATE_LIMIT;
    }

    public RateLimitException(ExceptionCode exceptionCode) {
        super();
        this.httpStatus = exceptionCode.getHttpStatus();
        this.level = ExceptionLevel.WARN;
        this.category = ExceptionCategory.RATE_LIMIT;
        this.code = exceptionCode.getCode();
        this.key = exceptionCode.getKey();
        this.params = normalizeParams(new Object[]{});
        this.message = null;
        this.messageKey = exceptionCode.getKey();
        this.messageParams = this.params;
    }

    public RateLimitException(String key) {
        super();
        this.httpStatus = HttpStatus.TOO_MANY_REQUESTS.value();
        this.level = ExceptionLevel.WARN;
        this.category = ExceptionCategory.RATE_LIMIT;
        this.code = UnifiedExceptionCode.FAIL.getCode();
        this.key = key;
        this.params = normalizeParams(new Object[]{});
        this.message = null;
        this.messageKey = key;
        this.messageParams = this.params;
    }

    public RateLimitException(ExceptionCode exceptionCode, Object[] params) {
        super();
        this.httpStatus = exceptionCode.getHttpStatus();
        this.level = ExceptionLevel.WARN;
        this.category = ExceptionCategory.RATE_LIMIT;
        this.code = exceptionCode.getCode();
        this.key = exceptionCode.getKey();
        this.params = normalizeParams(params);
        this.message = null;
        this.messageKey = exceptionCode.getKey();
        this.messageParams = this.params;
    }

    public RateLimitException(Throwable cause) {
        super(cause);
        this.httpStatus = HttpStatus.TOO_MANY_REQUESTS.value();
        this.level = ExceptionLevel.WARN;
        this.category = ExceptionCategory.RATE_LIMIT;
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
    public static class RateLimitExceptionBuilder extends RemiExceptionBuilder<RateLimitException, RateLimitExceptionBuilder> {

        public RateLimitExceptionBuilder() {
            super();
            this.code = UnifiedExceptionCode.FAIL.getCode();
            this.httpStatus = HttpStatus.TOO_MANY_REQUESTS.value();
            this.level = ExceptionLevel.WARN;
            this.category = ExceptionCategory.RATE_LIMIT;
        }

        @Override
        protected RateLimitException doBuild(String code, String key, Object[] params, int httpStatus,
                                              ExceptionLevel level, ExceptionCategory category,
                                              Throwable cause, String path, Object extData, String message) {
            RateLimitException exception = new RateLimitException();
            exception.initFields(code, key, params);
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
