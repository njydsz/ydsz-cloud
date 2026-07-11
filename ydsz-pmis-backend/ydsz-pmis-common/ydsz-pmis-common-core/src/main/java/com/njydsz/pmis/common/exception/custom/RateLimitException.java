package com.njydsz.pmis.common.exception.custom;

import com.njydsz.pmis.common.exception.code.UnifiedExceptionCode;
import com.njydsz.pmis.common.exception.enums.ExceptionCategory;
import com.njydsz.pmis.common.exception.enums.ExceptionLevel;

import java.io.Serial;

/**
 * 限流异常
 *
 * @author ydsz-pmis-team
 * @since 1.4.0
 */
public class RateLimitException extends AbstractPmisException {

    @Serial
    private static final long serialVersionUID = 1L;

    public RateLimitException() {
        super();
        this.code = UnifiedExceptionCode.RATE_LIMIT.getCode();
        this.httpStatus = 429;
        this.level = ExceptionLevel.WARN;
        this.category = ExceptionCategory.SECURITY;
    }

    public RateLimitException(String message) {
        super(message);
        this.code = UnifiedExceptionCode.RATE_LIMIT.getCode();
        this.httpStatus = 429;
        this.level = ExceptionLevel.WARN;
        this.category = ExceptionCategory.SECURITY;
    }
}
