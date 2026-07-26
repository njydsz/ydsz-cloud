package com.njydsz.common.exception.custom;

import org.springframework.http.HttpStatus;

import com.njydsz.common.exception.code.UnifiedExceptionCode;
import com.njydsz.common.exception.core.ExceptionInfo;
import com.njydsz.common.exception.enums.ExceptionCategory;
import com.njydsz.common.exception.enums.ExceptionCode;
import com.njydsz.common.exception.enums.ExceptionLevel;

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
 * @author ydsz-team
 * @since 1.0.0
 * @see UnifiedExceptionCode
 * @see ExceptionCategory#VALIDATION
 */
@ToString(callSuper = true)
public class ValidationException extends AbstractYdszException {

    private static final long serialVersionUID = 1L;

    /**
     * 默认构造函数，初始化为 400 Bad Request / WARN / VALIDATION
     */
    public ValidationException() {
        super();
        initDefaults(HttpStatus.BAD_REQUEST.value(), ExceptionLevel.WARN, ExceptionCategory.VALIDATION);
    }

    /**
     * 使用国际化消息键构造校验异常
     *
     * @param key 国际化消息键
     */
    public ValidationException(String key) {
        super();
        init(UnifiedExceptionCode.PARAM_ERROR.getCode(), key, new Object[]{}, HttpStatus.BAD_REQUEST.value(), ExceptionLevel.WARN, ExceptionCategory.VALIDATION);
    }

    /**
     * 使用异常码枚举构造校验异常
     *
     * @param exceptionCode 异常码枚举
     */
    public ValidationException(ExceptionCode exceptionCode) {
        super();
        init(exceptionCode, new Object[]{}, ExceptionLevel.WARN, ExceptionCategory.VALIDATION);
    }

    /**
     * 使用国际化消息键和参数构造校验异常
     *
     * @param key    国际化消息键
     * @param params 消息参数
     */
    public ValidationException(String key, Object[] params) {
        super();
        init(UnifiedExceptionCode.PARAM_ERROR.getCode(), key, params, HttpStatus.BAD_REQUEST.value(), ExceptionLevel.WARN, ExceptionCategory.VALIDATION);
    }

    /**
     * 使用异常码枚举和参数构造校验异常
     *
     * @param exceptionCode 异常码枚举
     * @param params        消息参数
     */
    public ValidationException(ExceptionCode exceptionCode, Object[] params) {
        super();
        init(exceptionCode, params, ExceptionLevel.WARN, ExceptionCategory.VALIDATION);
    }

    /**
     * 使用自定义错误码和消息键构造校验异常
     *
     * @param code 错误码字符串
     * @param key  国际化消息键
     */
    public ValidationException(String code, String key) {
        super();
        init(code, key, new Object[]{}, HttpStatus.BAD_REQUEST.value(), ExceptionLevel.WARN, ExceptionCategory.VALIDATION);
    }

    /**
     * 使用自定义错误码、消息键和参数构造校验异常
     *
     * @param code   错误码字符串
     * @param key    国际化消息键
     * @param params 消息参数
     */
    public ValidationException(String code, String key, Object[] params) {
        super();
        init(code, key, params, HttpStatus.BAD_REQUEST.value(), ExceptionLevel.WARN, ExceptionCategory.VALIDATION);
    }

    /**
     * 使用原始异常构造校验异常
     *
     * @param cause 原始异常
     */
    public ValidationException(Throwable cause) {
        super(cause);
        initDefaults(HttpStatus.BAD_REQUEST.value(), ExceptionLevel.WARN, ExceptionCategory.VALIDATION);
        this.code = UnifiedExceptionCode.PARAM_ERROR.getCode();
    }

    /**
     * 使用自定义错误码和原始异常构造校验异常
     *
     * @param code  错误码字符串
     * @param cause 原始异常
     */
    public ValidationException(String code, Throwable cause) {
        super(cause);
        initDefaults(HttpStatus.BAD_REQUEST.value(), ExceptionLevel.WARN, ExceptionCategory.VALIDATION);
        this.code = code;
    }

    /**
     * 使用异常码枚举和原始异常构造校验异常
     *
     * @param exceptionCode 异常码枚举
     * @param cause         原始异常
     */
    public ValidationException(ExceptionCode exceptionCode, Throwable cause) {
        super(null, cause);
        init(exceptionCode, new Object[]{}, ExceptionLevel.WARN, ExceptionCategory.VALIDATION);
    }

    /**
     * 使用自定义错误码、消息键和原始异常构造校验异常
     *
     * @param code  错误码字符串
     * @param key   国际化消息键
     * @param cause 原始异常
     */
    public ValidationException(String code, String key, Throwable cause) {
        super(null, cause);
        init(code, key, new Object[]{}, HttpStatus.BAD_REQUEST.value(), ExceptionLevel.WARN, ExceptionCategory.VALIDATION);
    }

    /**
     * 使用自定义错误码、消息键、参数和原始异常构造校验异常
     *
     * @param code   错误码字符串
     * @param key    国际化消息键
     * @param params 消息参数
     * @param cause  原始异常
     */
    public ValidationException(String code, String key, Object[] params, Throwable cause) {
        super(null, cause);
        init(code, key, params, HttpStatus.BAD_REQUEST.value(), ExceptionLevel.WARN, ExceptionCategory.VALIDATION);
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
        protected ValidationException doBuild(String code, String subCode, String key, Object[] params, int httpStatus,
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
