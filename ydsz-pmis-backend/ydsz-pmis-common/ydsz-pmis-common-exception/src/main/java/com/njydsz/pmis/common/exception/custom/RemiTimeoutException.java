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
 * throw new RemiTimeoutException(UnifiedExceptionCode.EXTERNAL_SERVICE_TIMEOUT);
 * throw new RemiTimeoutException("external.service.timeout", new Object[]{serviceName});
 * throw RemiTimeoutException.of(UnifiedExceptionCode.EXTERNAL_SERVICE_TIMEOUT).build();
 * }</pre>
 *
 * @author Marvin Lee
 * @email limw1888@126.com
 * @version 3.5.0
 * @since 3.0.0
 * @see com.njydsz.pmis.common.exception.code.UnifiedExceptionCode
 * @see ExceptionCategory#TIMEOUT
 */
@ToString(callSuper = true)
public class RemiTimeoutException extends AbstractRemiException {

    private static final long serialVersionUID = 1L;

    public RemiTimeoutException() {
        super();
        this.httpStatus = HttpStatus.GATEWAY_TIMEOUT.value();
        this.level = ExceptionLevel.ERROR;
        this.category = ExceptionCategory.TIMEOUT;
    }

    public RemiTimeoutException(String key) {
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

    public RemiTimeoutException(ExceptionCode exceptionCode) {
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

    public RemiTimeoutException(String key, Object[] params) {
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

    public RemiTimeoutException(ExceptionCode exceptionCode, Object[] params) {
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

    public RemiTimeoutException(String code, String key) {
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

    public RemiTimeoutException(String code, String key, Object[] params) {
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

    public RemiTimeoutException(Throwable cause) {
        super(cause);
        this.httpStatus = HttpStatus.GATEWAY_TIMEOUT.value();
        this.level = ExceptionLevel.ERROR;
        this.category = ExceptionCategory.TIMEOUT;
        this.code = UnifiedExceptionCode.FAIL.getCode();
    }

    public RemiTimeoutException(String code, Throwable cause) {
        super(cause);
        this.httpStatus = HttpStatus.GATEWAY_TIMEOUT.value();
        this.level = ExceptionLevel.ERROR;
        this.category = ExceptionCategory.TIMEOUT;
        this.code = code;
    }

    public RemiTimeoutException(ExceptionCode exceptionCode, Throwable cause) {
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

    public RemiTimeoutException(String code, String key, Throwable cause) {
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

    public RemiTimeoutException(String code, String key, Object[] params, Throwable cause) {
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

    public static RemiTimeoutExceptionBuilder builder() {
        return new RemiTimeoutExceptionBuilder();
    }

    public static RemiTimeoutException of(String key) {
        return new RemiTimeoutException(key);
    }

    public static RemiTimeoutException of(ExceptionCode exceptionCode) {
        return new RemiTimeoutException(exceptionCode);
    }

    public static RemiTimeoutException of(String code, String key) {
        return new RemiTimeoutException(code, key);
    }

    /**
     * 超时异常构建器，预置默认的错误码、HTTP状态码、级别和分类
     */
    public static class RemiTimeoutExceptionBuilder extends RemiExceptionBuilder<RemiTimeoutException, RemiTimeoutExceptionBuilder> {

        public RemiTimeoutExceptionBuilder() {
            super();
            this.code = UnifiedExceptionCode.FAIL.getCode();
            this.httpStatus = HttpStatus.GATEWAY_TIMEOUT.value();
            this.level = ExceptionLevel.ERROR;
            this.category = ExceptionCategory.TIMEOUT;
        }

        @Override
        protected RemiTimeoutException doBuild(String code, String key, Object[] params, int httpStatus,
                                               ExceptionLevel level, ExceptionCategory category,
                                               Throwable cause, String path, Object extData, String message) {
            RemiTimeoutException exception;
            if (cause != null) {
                exception = new RemiTimeoutException(code, key, params, cause);
            } else {
                exception = new RemiTimeoutException(code, key, params);
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
