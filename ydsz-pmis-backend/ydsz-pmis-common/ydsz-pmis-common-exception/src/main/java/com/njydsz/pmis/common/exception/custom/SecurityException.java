package com.njydsz.pmis.common.exception.custom;

import com.njydsz.pmis.common.exception.code.UnifiedExceptionCode;
import com.njydsz.pmis.common.exception.enums.ExceptionCategory;
import com.njydsz.pmis.common.exception.enums.ExceptionLevel;

import java.io.Serial;

/**
 * 安全异常（越权/注入/认证失败）
 *
 * @author ydsz-pmis-team
 * @since 1.4.0
 */
public class SecurityException extends AbstractPmisException {

    @Serial
    private static final long serialVersionUID = 1L;

    public SecurityException() {
        super();
        this.code = UnifiedExceptionCode.SECURITY_ACCESS_DENIED.getCode();
        this.httpStatus = 403;
        this.level = ExceptionLevel.WARN;
        this.category = ExceptionCategory.SECURITY;
    }

    public SecurityException(String message) {
        super(message);
        this.code = UnifiedExceptionCode.SECURITY_ACCESS_DENIED.getCode();
        this.httpStatus = 403;
        this.level = ExceptionLevel.WARN;
        this.category = ExceptionCategory.SECURITY;
    }

    public SecurityException(String message, Throwable cause) {
        super(message, cause);
        this.code = UnifiedExceptionCode.SECURITY_ACCESS_DENIED.getCode();
        this.httpStatus = 403;
        this.level = ExceptionLevel.WARN;
        this.category = ExceptionCategory.SECURITY;
    }
}
