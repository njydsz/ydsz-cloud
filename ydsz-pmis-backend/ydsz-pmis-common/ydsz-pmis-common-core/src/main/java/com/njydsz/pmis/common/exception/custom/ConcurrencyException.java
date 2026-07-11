package com.njydsz.pmis.common.exception.custom;

import com.njydsz.pmis.common.exception.code.UnifiedExceptionCode;
import com.njydsz.pmis.common.exception.enums.ExceptionCategory;
import com.njydsz.pmis.common.exception.enums.ExceptionLevel;

import java.io.Serial;

/**
 * 并发冲突异常（乐观锁冲突）
 *
 * @author ydsz-pmis-team
 * @since 1.4.0
 */
public class ConcurrencyException extends AbstractPmisException {

    @Serial
    private static final long serialVersionUID = 1L;

    public ConcurrencyException() {
        super();
        this.code = UnifiedExceptionCode.OPTIMISTIC_LOCK_CONFLICT.getCode();
        this.httpStatus = 409;
        this.level = ExceptionLevel.WARN;
        this.category = ExceptionCategory.BUSINESS;
    }

    public ConcurrencyException(String message) {
        super(message);
        this.code = UnifiedExceptionCode.OPTIMISTIC_LOCK_CONFLICT.getCode();
        this.httpStatus = 409;
        this.level = ExceptionLevel.WARN;
        this.category = ExceptionCategory.BUSINESS;
    }
}
