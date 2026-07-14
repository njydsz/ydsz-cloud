package com.njydsz.pmis.common.exception.custom;

import org.springframework.http.HttpStatus;

import com.njydsz.pmis.common.exception.code.UnifiedExceptionCode;
import com.njydsz.pmis.common.exception.core.ExceptionInfo;
import com.njydsz.pmis.common.exception.enums.ExceptionCategory;
import com.njydsz.pmis.common.exception.enums.ExceptionCode;
import com.njydsz.pmis.common.exception.enums.ExceptionLevel;

import lombok.ToString;

/**
 * 校验异常类
 *
 * <p>用于封装参数校验失败类异常，如参数为空、格式错误、范围越界等。
 * 默认 HTTP 状态码为 400，异常分类为 VALIDATION。
 *
 * <p><b>使用示例：</b>
 * <pre>{@code
 * throw new ValidationException(UnifiedExceptionCode.PARAM_ERROR);
 * throw new ValidationException("param.error", new Object[]{"userName"});
 * throw ValidationException.of(UnifiedExceptionCode.PARAM_ERROR).cause(cause).build();
 * }</pre>
 *
 * @author ydsz-pmis-team
 * @since 1.0.0
 * 
 * @since 3.0.0
 * @see UnifiedExceptionCode
 * @see ExceptionCategory#VALIDATION
 */
@ToString(callSuper = true)
public class ValidationException extends AbstractYdszException {

    private static final long serialVersionUID = 1L;

    public ValidationException() {
        super();
        this.httpStatus = HttpStatus.BAD_REQUEST.value();
        this.level = ExceptionLevel.WARN;
        this.category = ExceptionCategory.VALIDATION;
    }

    public ValidationException(String key) {
        super();
        this.httpStatus = HttpStatus.BAD_REQUEST.value();
        this.level = ExceptionLevel.WARN;
        this.category = ExceptionCategory.VALIDATION;
        this.code = UnifiedExceptionCode.PARAM_ERROR.getCode();
        this.key = key;
        this.params = normalizeParams(new Object[]{});
        this.message = null;
        this.messageKey = key;
        this.messageParams = this.params;
    }

    public ValidationException(ExceptionCode exceptionCode) {
        super();
        this.httpStatus = exceptionCode.getHttpStatus();
        this.level = ExceptionLevel.WARN;
        this.category = ExceptionCategory.VALIDATION;
        this.code = exceptionCode.getCode();
        this.key = exceptionCode.getKey();
        this.params = normalizeParams(new Object[]{});
        this.message = null;
        this.messageKey = exceptionCode.getKey();
        this.messageParams = this.params;
    }

    public ValidationException(String key, Object[] params) {
        super();
        this.httpStatus = HttpStatus.BAD_REQUEST.value();
        this.level = ExceptionLevel.WARN;
        this.category = ExceptionCategory.VALIDATION;
        this.code = UnifiedExceptionCode.PARAM_ERROR.getCode();
        this.key = key;
        this.params = normalizeParams(params);
        this.message = null;
        this.messageKey = key;
        this.messageParams = this.params;
    }

    public ValidationException(ExceptionCode exceptionCode, Object[] params) {
        super();
        this.httpStatus = exceptionCode.getHttpStatus();
        this.level = ExceptionLevel.WARN;
        this.category = ExceptionCategory.VALIDATION;
        this.code = exceptionCode.getCode();
        this.key = exceptionCode.getKey();
        this.params = normalizeParams(params);
        this.message = null;
        this.messageKey = exceptionCode.getKey();
        this.messageParams = this.params;
    }

    public ValidationException(String code, String key) {
        super();
        this.httpStatus = HttpStatus.BAD_REQUEST.value();
        this.level = ExceptionLevel.WARN;
        this.category = ExceptionCategory.VALIDATION;
        this.code = code;
        this.key = key;
        this.params = normalizeParams(new Object[]{});
        this.message = null;
        this.messageKey = key;
        this.messageParams = this.params;
    }

    public ValidationException(String code, String key, Object[] params) {
        super();
        this.httpStatus = HttpStatus.BAD_REQUEST.value();
        this.level = ExceptionLevel.WARN;
        this.category = ExceptionCategory.VALIDATION;
        this.code = code;
        this.key = key;
        this.params = normalizeParams(params);
        this.message = null;
        this.messageKey = key;
        this.messageParams = this.params;
    }

    public ValidationException(Throwable cause) {
        super(cause);
        this.httpStatus = HttpStatus.BAD_REQUEST.value();
        this.level = ExceptionLevel.WARN;
        this.category = ExceptionCategory.VALIDATION;
        this.code = UnifiedExceptionCode.PARAM_ERROR.getCode();
    }

    public ValidationException(String code, Throwable cause) {
        super(cause);
        this.httpStatus = HttpStatus.BAD_REQUEST.value();
        this.level = ExceptionLevel.WARN;
        this.category = ExceptionCategory.VALIDATION;
        this.code = code;
    }

    public ValidationException(ExceptionCode exceptionCode, Throwable cause) {
        super(null, cause);
        this.httpStatus = exceptionCode.getHttpStatus();
        this.level = ExceptionLevel.WARN;
        this.category = ExceptionCategory.VALIDATION;
        this.code = exceptionCode.getCode();
        this.key = exceptionCode.getKey();
        this.params = normalizeParams(new Object[]{});
        this.message = null;
        this.messageKey = exceptionCode.getKey();
        this.messageParams = this.params;
    }

    public ValidationException(String code, String key, Throwable cause) {
        super(null, cause);
        this.httpStatus = HttpStatus.BAD_REQUEST.value();
        this.level = ExceptionLevel.WARN;
        this.category = ExceptionCategory.VALIDATION;
        this.code = code;
        this.key = key;
        this.params = normalizeParams(new Object[]{});
        this.message = null;
        this.messageKey = key;
        this.messageParams = this.params;
    }

    public ValidationException(String code, String key, Object[] params, Throwable cause) {
        super(null, cause);
        this.httpStatus = HttpStatus.BAD_REQUEST.value();
        this.level = ExceptionLevel.WARN;
        this.category = ExceptionCategory.VALIDATION;
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

    public static ValidationExceptionBuilder builder() {
        return new ValidationExceptionBuilder();
    }

    public static ValidationException of(String key) {
        return new ValidationException(key);
    }

    /**
     * 根据异常码创建校验异常
     *
     * @param exceptionCode 异常码枚举
     * @return 校验异常实例
     */
    public static ValidationException of(ExceptionCode exceptionCode) {
        return new ValidationException(exceptionCode);
    }

    public static ValidationException of(String code, String key) {
        return new ValidationException(code, key);
    }

    public static class ValidationExceptionBuilder extends YdszExceptionBuilder<ValidationException, ValidationExceptionBuilder> {

        @Override
        protected ValidationExceptionBuilder self() {
            return this;
        }

        public ValidationExceptionBuilder() {
            super();
            this.code = UnifiedExceptionCode.PARAM_ERROR.getCode();
            this.httpStatus = HttpStatus.BAD_REQUEST.value();
            this.level = ExceptionLevel.WARN;
            this.category = ExceptionCategory.VALIDATION;
        }

        @Override
        protected ValidationException doBuild(String code, String key, Object[] params, int httpStatus,
                                              ExceptionLevel level, ExceptionCategory category,
                                              Throwable cause, String path, Object extData, String message) {
            ValidationException exception;
            if (cause != null) {
                exception = new ValidationException(code, key, params, cause);
            } else {
                exception = new ValidationException(code, key, params);
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
