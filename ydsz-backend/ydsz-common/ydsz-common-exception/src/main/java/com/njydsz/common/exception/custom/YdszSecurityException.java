package com.njydsz.common.exception.custom;

import org.springframework.http.HttpStatus;

import com.njydsz.common.exception.code.UnifiedExceptionCode;
import com.njydsz.common.exception.enums.ExceptionCategory;
import com.njydsz.common.exception.enums.ExceptionCode;
import com.njydsz.common.exception.enums.ExceptionLevel;

import lombok.ToString;

/**
 * 安全异常
 *
 * <p>用于封装安全相关的异常（如权限不足、认证失败、CSRF 等）。
 *
 * <p><b>默认值：</b>
 * <ul>
 *   <li>HTTP 状态码：403 Forbidden</li>
 *   <li>异常级别：WARN</li>
 *   <li>异常分类：SECURITY</li>
 * </ul>
 *
 * @author ydsz-team
 * @since 1.0.0
 */
@ToString(callSuper = true)
public class YdszSecurityException extends AbstractYdszException {

    private static final long serialVersionUID = 1L;

    private static final int DEFAULT_HTTP_STATUS = HttpStatus.FORBIDDEN.value();
    private static final ExceptionLevel DEFAULT_LEVEL = ExceptionLevel.WARN;
    private static final ExceptionCategory DEFAULT_CATEGORY = ExceptionCategory.SECURITY;
    private static final String DEFAULT_CODE = UnifiedExceptionCode.ACCESS_DENIED.getCode();

    public YdszSecurityException() {
        super();
        initDefaults(DEFAULT_HTTP_STATUS, DEFAULT_LEVEL, DEFAULT_CATEGORY);
    }

    public YdszSecurityException(ExceptionCode exceptionCode) {
        super();
        init(exceptionCode, new Object[]{}, DEFAULT_LEVEL, DEFAULT_CATEGORY);
    }

    public YdszSecurityException(ExceptionCode exceptionCode, Object[] params) {
        super();
        init(exceptionCode, params, DEFAULT_LEVEL, DEFAULT_CATEGORY);
    }

    public YdszSecurityException(ExceptionCode exceptionCode, String message) {
        super(message);
        initDefaults(DEFAULT_HTTP_STATUS, DEFAULT_LEVEL, DEFAULT_CATEGORY);
        initFields(exceptionCode.getCode(), exceptionCode.getKey(), new Object[]{});
        this.message = message;
        this.messageResolved = true;
    }

    public YdszSecurityException(String key) {
        super();
        init(DEFAULT_CODE, key, new Object[]{}, DEFAULT_HTTP_STATUS, DEFAULT_LEVEL, DEFAULT_CATEGORY);
    }

    public YdszSecurityException(String key, Object[] params) {
        super();
        init(DEFAULT_CODE, key, params, DEFAULT_HTTP_STATUS, DEFAULT_LEVEL, DEFAULT_CATEGORY);
    }

    public YdszSecurityException(String code, String key) {
        super();
        init(code, key, new Object[]{}, DEFAULT_HTTP_STATUS, DEFAULT_LEVEL, DEFAULT_CATEGORY);
    }

    public YdszSecurityException(String code, String key, Object[] params) {
        super();
        init(code, key, params, DEFAULT_HTTP_STATUS, DEFAULT_LEVEL, DEFAULT_CATEGORY);
    }

    public YdszSecurityException(Throwable cause) {
        super(cause);
        initDefaults(DEFAULT_HTTP_STATUS, DEFAULT_LEVEL, DEFAULT_CATEGORY);
        this.code = DEFAULT_CODE;
    }
}
