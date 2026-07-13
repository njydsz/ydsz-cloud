package com.njydsz.pmis.common.exception.custom;

import org.springframework.http.HttpStatus;

import com.njydsz.pmis.common.exception.code.UnifiedExceptionCode;
import com.njydsz.pmis.common.exception.core.ExceptionInfo;
import com.njydsz.pmis.common.exception.enums.ExceptionCategory;
import com.njydsz.pmis.common.exception.enums.ExceptionCode;
import com.njydsz.pmis.common.exception.enums.ExceptionLevel;

import lombok.ToString;

/**
 * 超时异常类
 *
 * <p>用于封装各类超时场景异常，如接口调用超时、数据库查询超时、第三方服务超时等。
 * 默认 HTTP 状态码为 504，异常分类为 TIMEOUT。
 *
 * <p><b>使用示例：</b>
 * <pre>{@code
 * throw new YdszTimeoutException(UnifiedExceptionCode.EXTERNAL_SERVICE_TIMEOUT);
 * throw new YdszTimeoutException("external.service.timeout", new Object[]{serviceName});
 * throw YdszTimeoutException.of(UnifiedExceptionCode.EXTERNAL_SERVICE_TIMEOUT).build();
 * }</pre>
 *
 * @author Marvin Lee
 * @email limw1888@126.com
 * @version 3.5.0
 * @since 3.0.0
 * @see UnifiedExceptionCode
 * @see ExceptionCategory#TIMEOUT
 */
@ToString(callSuper = true)
public class YdszTimeoutException extends AbstractYdszException {

    private static final long serialVersionUID = 1L;

    public YdszTimeoutException() {
        super();
        this.httpStatus = HttpStatus.GATEWAY_TIMEOUT.value();
        this.level = ExceptionLevel.ERROR;
        this.category = ExceptionCategory.TIMEOUT;
    }

    public YdszTimeoutException(String key) {
        super();
        this.httpStatus = HttpStatus.GATEWAY_TIMEOUT.value();
        this.level = ExceptionLevel.ERROR;
        this.category = ExceptionCategory.TIMEOUT;
        this.code = UnifiedExceptionCode.FAIL.getCode();
        this.key = key;
        this.params = normalizeParams(new Object[]{});
        this.message = null;
        this.messageKey = key;
        this.messageParams = this.params;
    }

    public YdszTimeoutException(ExceptionCode exceptionCode) {
        super();
        this.httpStatus = exceptionCode.getHttpStatus();
        this.level = ExceptionLevel.ERROR;
        this.category = ExceptionCategory.TIMEOUT;
        this.code = exceptionCode.getCode();
        this.key = exceptionCode.getKey();
        this.params = normalizeParams(new Object[]{});
        this.message = null;
        this.messageKey = exceptionCode.getKey();
        this.messageParams = this.params;
    }

    public YdszTimeoutException(String key, Object[] params) {
        super();
        this.httpStatus = HttpStatus.GATEWAY_TIMEOUT.value();
        this.level = ExceptionLevel.ERROR;
        this.category = ExceptionCategory.TIMEOUT;
        this.code = UnifiedExceptionCode.FAIL.getCode();
        this.key = key;
        this.params = normalizeParams(params);
        this.message = null;
        this.messageKey = key;
        this.messageParams = this.params;
    }

    public YdszTimeoutException(ExceptionCode exceptionCode, Object[] params) {
        super();
        this.httpStatus = exceptionCode.getHttpStatus();
        this.level = ExceptionLevel.ERROR;
        this.category = ExceptionCategory.TIMEOUT;
        this.code = exceptionCode.getCode();
        this.key = exceptionCode.getKey();
        this.params = normalizeParams(params);
        this.message = null;
        this.messageKey = exceptionCode.getKey();
        this.messageParams = this.params;
    }

    public YdszTimeoutException(String code, String key) {
        super();
        this.httpStatus = HttpStatus.GATEWAY_TIMEOUT.value();
        this.level = ExceptionLevel.ERROR;
        this.category = ExceptionCategory.TIMEOUT;
        this.code = code;
        this.key = key;
        this.params = normalizeParams(new Object[]{});
        this.message = null;
        this.messageKey = key;
        this.messageParams = this.params;
    }

    public YdszTimeoutException(String code, String key, Object[] params) {
        super();
        this.httpStatus = HttpStatus.GATEWAY_TIMEOUT.value();
        this.level = ExceptionLevel.ERROR;
        this.category = ExceptionCategory.TIMEOUT;
        this.code = code;
        this.key = key;
        this.params = normalizeParams(params);
        this.message = null;
        this.messageKey = key;
        this.messageParams = this.params;
    }

    public YdszTimeoutException(Throwable cause) {
        super(cause);
        this.httpStatus = HttpStatus.GATEWAY_TIMEOUT.value();
        this.level = ExceptionLevel.ERROR;
        this.category = ExceptionCategory.TIMEOUT;
        this.code = UnifiedExceptionCode.FAIL.getCode();
    }

    public YdszTimeoutException(String code, Throwable cause) {
        super(cause);
        this.httpStatus = HttpStatus.GATEWAY_TIMEOUT.value();
        this.level = ExceptionLevel.ERROR;
        this.category = ExceptionCategory.TIMEOUT;
        this.code = code;
    }

    public YdszTimeoutException(ExceptionCode exceptionCode, Throwable cause) {
        super(null, cause);
        this.httpStatus = exceptionCode.getHttpStatus();
        this.level = ExceptionLevel.ERROR;
        this.category = ExceptionCategory.TIMEOUT;
        this.code = exceptionCode.getCode();
        this.key = exceptionCode.getKey();
        this.params = normalizeParams(new Object[]{});
        this.message = null;
        this.messageKey = exceptionCode.getKey();
        this.messageParams = this.params;
    }

    public YdszTimeoutException(String code, String key, Throwable cause) {
        super(null, cause);
        this.httpStatus = HttpStatus.GATEWAY_TIMEOUT.value();
        this.level = ExceptionLevel.ERROR;
        this.category = ExceptionCategory.TIMEOUT;
        this.code = code;
        this.key = key;
        this.params = normalizeParams(new Object[]{});
        this.message = null;
        this.messageKey = key;
        this.messageParams = this.params;
    }

    public YdszTimeoutException(String code, String key, Object[] params, Throwable cause) {
        super(null, cause);
        this.httpStatus = HttpStatus.GATEWAY_TIMEOUT.value();
        this.level = ExceptionLevel.ERROR;
        this.category = ExceptionCategory.TIMEOUT;
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

    public static YdszTimeoutExceptionBuilder builder() {
        return new YdszTimeoutExceptionBuilder();
    }

    public static YdszTimeoutException of(String key) {
        return new YdszTimeoutException(key);
    }

    public static YdszTimeoutException of(ExceptionCode exceptionCode) {
        return new YdszTimeoutException(exceptionCode);
    }

    public static YdszTimeoutException of(String code, String key) {
        return new YdszTimeoutException(code, key);
    }

    /**
     * 超时异常构建器，预置默认的错误码、HTTP状态码、级别和分类
     */
    public static class YdszTimeoutExceptionBuilder extends YdszExceptionBuilder<YdszTimeoutException, YdszTimeoutExceptionBuilder> {

        public YdszTimeoutExceptionBuilder() {
            super();
            this.code = UnifiedExceptionCode.FAIL.getCode();
            this.httpStatus = HttpStatus.GATEWAY_TIMEOUT.value();
            this.level = ExceptionLevel.ERROR;
            this.category = ExceptionCategory.TIMEOUT;
        }

        @Override
        protected YdszTimeoutException doBuild(String code, String key, Object[] params, int httpStatus,
                                               ExceptionLevel level, ExceptionCategory category,
                                               Throwable cause, String path, Object extData, String message) {
            YdszTimeoutException exception;
            if (cause != null) {
                exception = new YdszTimeoutException(code, key, params, cause);
            } else {
                exception = new YdszTimeoutException(code, key, params);
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
