package com.njydsz.pmis.common.exception.custom;

import org.springframework.http.HttpStatus;

import com.njydsz.pmis.common.exception.code.UnifiedExceptionCode;
import com.njydsz.pmis.common.exception.core.ExceptionInfo;
import com.njydsz.pmis.common.exception.enums.ExceptionCategory;
import com.njydsz.pmis.common.exception.enums.ExceptionCode;
import com.njydsz.pmis.common.exception.enums.ExceptionLevel;

import lombok.ToString;

/**
 * 外部服务异常类
 *
 * <p>用于封装调用外部第三方服务失败的异常场景，如支付服务异常、短信网关异常、
 * 第三方API调用失败等。默认 HTTP 状态码为 502，异常分类为 EXTERNAL。
 *
 * <p><b>使用示例：</b>
 * <pre>{@code
 * throw new ExternalException(UnifiedExceptionCode.OTHER_EXTERNAL_ERROR);
 * throw new ExternalException("external.service.error");
 * throw new ExternalException(UnifiedExceptionCode.EXTERNAL_SERVICE_REJECTED, cause);
 * throw ExternalException.of(UnifiedExceptionCode.OTHER_EXTERNAL_ERROR).cause(cause).build();
 * }</pre>
 *
 * @author Marvin Lee
 * @email limw1888@126.com
 * @version 3.5.0
 * @since 3.0.0
 * @see UnifiedExceptionCode
 * @see ExceptionCategory#EXTERNAL
 */
@ToString(callSuper = true)
public class ExternalException extends AbstractYdszException {

    private static final long serialVersionUID = 1L;

    public ExternalException() {
        super();
        this.httpStatus = HttpStatus.BAD_GATEWAY.value();
        this.level = ExceptionLevel.ERROR;
        this.category = ExceptionCategory.EXTERNAL;
    }

    public ExternalException(String key) {
        super();
        this.httpStatus = HttpStatus.BAD_GATEWAY.value();
        this.level = ExceptionLevel.ERROR;
        this.category = ExceptionCategory.EXTERNAL;
        this.code = UnifiedExceptionCode.FAIL.getCode();
        this.key = key;
        this.params = normalizeParams(new Object[]{});
        this.message = null;
        this.messageKey = key;
        this.messageParams = this.params;
    }

    public ExternalException(ExceptionCode exceptionCode) {
        super();
        this.httpStatus = exceptionCode.getHttpStatus();
        this.level = ExceptionLevel.ERROR;
        this.category = ExceptionCategory.EXTERNAL;
        this.code = exceptionCode.getCode();
        this.key = exceptionCode.getKey();
        this.params = normalizeParams(new Object[]{});
        this.message = null;
        this.messageKey = exceptionCode.getKey();
        this.messageParams = this.params;
    }

    public ExternalException(String key, Object[] params) {
        super();
        this.httpStatus = HttpStatus.BAD_GATEWAY.value();
        this.level = ExceptionLevel.ERROR;
        this.category = ExceptionCategory.EXTERNAL;
        this.code = UnifiedExceptionCode.FAIL.getCode();
        this.key = key;
        this.params = normalizeParams(params);
        this.message = null;
        this.messageKey = key;
        this.messageParams = this.params;
    }

    public ExternalException(ExceptionCode exceptionCode, Object[] params) {
        super();
        this.httpStatus = exceptionCode.getHttpStatus();
        this.level = ExceptionLevel.ERROR;
        this.category = ExceptionCategory.EXTERNAL;
        this.code = exceptionCode.getCode();
        this.key = exceptionCode.getKey();
        this.params = normalizeParams(params);
        this.message = null;
        this.messageKey = exceptionCode.getKey();
        this.messageParams = this.params;
    }

    public ExternalException(String code, String key) {
        super();
        this.httpStatus = HttpStatus.BAD_GATEWAY.value();
        this.level = ExceptionLevel.ERROR;
        this.category = ExceptionCategory.EXTERNAL;
        this.code = code;
        this.key = key;
        this.params = normalizeParams(new Object[]{});
        this.message = null;
        this.messageKey = key;
        this.messageParams = this.params;
    }

    public ExternalException(String code, String key, Object[] params) {
        super();
        this.httpStatus = HttpStatus.BAD_GATEWAY.value();
        this.level = ExceptionLevel.ERROR;
        this.category = ExceptionCategory.EXTERNAL;
        this.code = code;
        this.key = key;
        this.params = normalizeParams(params);
        this.message = null;
        this.messageKey = key;
        this.messageParams = this.params;
    }

    public ExternalException(Throwable cause) {
        super(cause);
        this.httpStatus = HttpStatus.BAD_GATEWAY.value();
        this.level = ExceptionLevel.ERROR;
        this.category = ExceptionCategory.EXTERNAL;
        this.code = UnifiedExceptionCode.FAIL.getCode();
    }

    public ExternalException(String code, Throwable cause) {
        super(cause);
        this.httpStatus = HttpStatus.BAD_GATEWAY.value();
        this.level = ExceptionLevel.ERROR;
        this.category = ExceptionCategory.EXTERNAL;
        this.code = code;
    }

    public ExternalException(ExceptionCode exceptionCode, Throwable cause) {
        super(null, cause);
        this.httpStatus = exceptionCode.getHttpStatus();
        this.level = ExceptionLevel.ERROR;
        this.category = ExceptionCategory.EXTERNAL;
        this.code = exceptionCode.getCode();
        this.key = exceptionCode.getKey();
        this.params = normalizeParams(new Object[]{});
        this.message = null;
        this.messageKey = exceptionCode.getKey();
        this.messageParams = this.params;
    }

    public ExternalException(String code, String key, Throwable cause) {
        super(null, cause);
        this.httpStatus = HttpStatus.BAD_GATEWAY.value();
        this.level = ExceptionLevel.ERROR;
        this.category = ExceptionCategory.EXTERNAL;
        this.code = code;
        this.key = key;
        this.params = normalizeParams(new Object[]{});
        this.message = null;
        this.messageKey = key;
        this.messageParams = this.params;
    }

    public ExternalException(String code, String key, Object[] params, Throwable cause) {
        super(null, cause);
        this.httpStatus = HttpStatus.BAD_GATEWAY.value();
        this.level = ExceptionLevel.ERROR;
        this.category = ExceptionCategory.EXTERNAL;
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

    public static ExternalExceptionBuilder builder() {
        return new ExternalExceptionBuilder();
    }

    public static ExternalException of(String key) {
        return new ExternalException(key);
    }

    public static ExternalException of(ExceptionCode exceptionCode) {
        return new ExternalException(exceptionCode);
    }

    public static ExternalException of(String code, String key) {
        return new ExternalException(code, key);
    }

    /**
     * 外部服务异常构建器，预置默认的错误码、HTTP状态码、级别和分类
     */
    public static class ExternalExceptionBuilder extends YdszExceptionBuilder<ExternalException, ExternalExceptionBuilder> {

        @Override
        protected ExternalExceptionBuilder self() {
            return this;
        }

        public ExternalExceptionBuilder() {
            super();
            this.code = UnifiedExceptionCode.FAIL.getCode();
            this.httpStatus = HttpStatus.BAD_GATEWAY.value();
            this.level = ExceptionLevel.ERROR;
            this.category = ExceptionCategory.EXTERNAL;
        }

        @Override
        protected ExternalException doBuild(String code, String key, Object[] params, int httpStatus,
                                            ExceptionLevel level, ExceptionCategory category,
                                            Throwable cause, String path, Object extData, String message) {
            ExternalException exception;
            if (cause != null) {
                exception = new ExternalException(code, key, params, cause);
            } else {
                exception = new ExternalException(code, key, params);
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
