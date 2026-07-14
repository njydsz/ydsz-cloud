package com.njydsz.pmis.common.exception.custom;

import org.springframework.http.HttpStatus;

import com.njydsz.pmis.common.exception.code.UnifiedExceptionCode;
import com.njydsz.pmis.common.exception.core.ExceptionInfo;
import com.njydsz.pmis.common.exception.enums.ExceptionCategory;
import com.njydsz.pmis.common.exception.enums.ExceptionCode;
import com.njydsz.pmis.common.exception.enums.ExceptionLevel;

import lombok.ToString;

/**
 * 安全异常类
 *
 * <p>用于封装安全相关异常，如越权访问、SQL注入、XSS攻击、Token伪造等。
 * 默认 HTTP 状态码为 403，异常分类为 SECURITY。
 *
 * <p><b>使用示例：</b>
 * <pre>{@code
 * throw new YdszSecurityException(UnifiedExceptionCode.FORBIDDEN);
 * throw new YdszSecurityException("forbidden", new Object[]{resourceId});
 * throw YdszSecurityException.of(UnifiedExceptionCode.ACCESS_DENIED).build();
 * }</pre>
 *
 * @author Marvin Lee
 * @email limw1888@126.com
 * @version 3.5.0
 * @since 3.0.0
 * @see UnifiedExceptionCode
 * @see ExceptionCategory#SECURITY
 */
@ToString(callSuper = true)
public class YdszSecurityException extends AbstractYdszException {

    private static final long serialVersionUID = 1L;

    /** 默认构造函数，初始化为 403 Forbidden / WARN / SECURITY */
    public YdszSecurityException() {
        super();
        this.httpStatus = HttpStatus.FORBIDDEN.value();
        this.level = ExceptionLevel.WARN;
        this.category = ExceptionCategory.SECURITY;
    }

    public YdszSecurityException(String key) {
        super();
        this.httpStatus = HttpStatus.FORBIDDEN.value();
        this.level = ExceptionLevel.WARN;
        this.category = ExceptionCategory.SECURITY;
        this.code = UnifiedExceptionCode.FORBIDDEN.getCode();
        this.key = key;
        this.params = normalizeParams(new Object[]{});
        this.message = null;
        this.messageKey = key;
        this.messageParams = this.params;
    }

    public YdszSecurityException(ExceptionCode exceptionCode) {
        super();
        this.httpStatus = exceptionCode.getHttpStatus();
        this.level = ExceptionLevel.WARN;
        this.category = ExceptionCategory.SECURITY;
        this.code = exceptionCode.getCode();
        this.key = exceptionCode.getKey();
        this.params = normalizeParams(new Object[]{});
        this.message = null;
        this.messageKey = exceptionCode.getKey();
        this.messageParams = this.params;
    }

    public YdszSecurityException(String key, Object[] params) {
        super();
        this.httpStatus = HttpStatus.FORBIDDEN.value();
        this.level = ExceptionLevel.WARN;
        this.category = ExceptionCategory.SECURITY;
        this.code = UnifiedExceptionCode.FORBIDDEN.getCode();
        this.key = key;
        this.params = normalizeParams(params);
        this.message = null;
        this.messageKey = key;
        this.messageParams = this.params;
    }

    public YdszSecurityException(ExceptionCode exceptionCode, Object[] params) {
        super();
        this.httpStatus = exceptionCode.getHttpStatus();
        this.level = ExceptionLevel.WARN;
        this.category = ExceptionCategory.SECURITY;
        this.code = exceptionCode.getCode();
        this.key = exceptionCode.getKey();
        this.params = normalizeParams(params);
        this.message = null;
        this.messageKey = exceptionCode.getKey();
        this.messageParams = this.params;
    }

    public YdszSecurityException(String code, String key) {
        super();
        this.httpStatus = HttpStatus.FORBIDDEN.value();
        this.level = ExceptionLevel.WARN;
        this.category = ExceptionCategory.SECURITY;
        this.code = code;
        this.key = key;
        this.params = normalizeParams(new Object[]{});
        this.message = null;
        this.messageKey = key;
        this.messageParams = this.params;
    }

    public YdszSecurityException(String code, String key, Object[] params) {
        super();
        this.httpStatus = HttpStatus.FORBIDDEN.value();
        this.level = ExceptionLevel.WARN;
        this.category = ExceptionCategory.SECURITY;
        this.code = code;
        this.key = key;
        this.params = normalizeParams(params);
        this.message = null;
        this.messageKey = key;
        this.messageParams = this.params;
    }

    public YdszSecurityException(Throwable cause) {
        super(cause);
        this.httpStatus = HttpStatus.FORBIDDEN.value();
        this.level = ExceptionLevel.WARN;
        this.category = ExceptionCategory.SECURITY;
        this.code = UnifiedExceptionCode.FORBIDDEN.getCode();
    }

    public YdszSecurityException(String code, Throwable cause) {
        super(cause);
        this.httpStatus = HttpStatus.FORBIDDEN.value();
        this.level = ExceptionLevel.WARN;
        this.category = ExceptionCategory.SECURITY;
        this.code = code;
    }

    public YdszSecurityException(ExceptionCode exceptionCode, Throwable cause) {
        super(null, cause);
        this.httpStatus = exceptionCode.getHttpStatus();
        this.level = ExceptionLevel.WARN;
        this.category = ExceptionCategory.SECURITY;
        this.code = exceptionCode.getCode();
        this.key = exceptionCode.getKey();
        this.params = normalizeParams(new Object[]{});
        this.message = null;
        this.messageKey = exceptionCode.getKey();
        this.messageParams = this.params;
    }

    public YdszSecurityException(String code, String key, Throwable cause) {
        super(null, cause);
        this.httpStatus = HttpStatus.FORBIDDEN.value();
        this.level = ExceptionLevel.WARN;
        this.category = ExceptionCategory.SECURITY;
        this.code = code;
        this.key = key;
        this.params = normalizeParams(new Object[]{});
        this.message = null;
        this.messageKey = key;
        this.messageParams = this.params;
    }

    public YdszSecurityException(String code, String key, Object[] params, Throwable cause) {
        super(null, cause);
        this.httpStatus = HttpStatus.FORBIDDEN.value();
        this.level = ExceptionLevel.WARN;
        this.category = ExceptionCategory.SECURITY;
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

    public static YdszSecurityExceptionBuilder builder() {
        return new YdszSecurityExceptionBuilder();
    }

    public static YdszSecurityException of(String key) {
        return new YdszSecurityException(key);
    }

    public static YdszSecurityException of(ExceptionCode exceptionCode) {
        return new YdszSecurityException(exceptionCode);
    }

    public static YdszSecurityException of(String code, String key) {
        return new YdszSecurityException(code, key);
    }

    /**
     * 安全异常构建器，预置默认的错误码、HTTP状态码、级别和分类
     */
    public static class YdszSecurityExceptionBuilder extends YdszExceptionBuilder<YdszSecurityException, YdszSecurityExceptionBuilder> {

        @Override
        protected YdszSecurityExceptionBuilder self() {
            return this;
        }

        public YdszSecurityExceptionBuilder() {
            super();
            this.code = UnifiedExceptionCode.FORBIDDEN.getCode();
            this.httpStatus = HttpStatus.FORBIDDEN.value();
            this.level = ExceptionLevel.WARN;
            this.category = ExceptionCategory.SECURITY;
        }

        @Override
        protected YdszSecurityException doBuild(String code, String key, Object[] params, int httpStatus,
                                                ExceptionLevel level, ExceptionCategory category,
                                                Throwable cause, String path, Object extData, String message) {
            YdszSecurityException exception;
            if (cause != null) {
                exception = new YdszSecurityException(code, key, params, cause);
            } else {
                exception = new YdszSecurityException(code, key, params);
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
