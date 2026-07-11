package com.njydsz.pmis.common.exception.custom;

import com.njydsz.pmis.common.exception.code.ExceptionCode;
import com.njydsz.pmis.common.exception.code.UnifiedExceptionCode;

/**
 * 参数校验异常 —— Bean Validation、业务规则校验失败等。
 * <p>
 * 对标 remi-comm ValidationException，默认 400 Bad Request。
 * </p>
 *
 * @author njydsz
 * @since 1.0.0
 */
public class ValidationException extends AbstractPmisException {

    private static final long serialVersionUID = 1L;

    public ValidationException(String message) {
        super(UnifiedExceptionCode.PARAM_ERROR, message);
    }

    public ValidationException(ExceptionCode code, String message) {
        super(code, message);
    }

    public ValidationException(ExceptionCode code, String message, Object... args) {
        super(code, message, args);
    }

    public static ValidationException of(String field, String reason) {
        return new ValidationException(
                UnifiedExceptionCode.PARAM_ERROR,
                "Validation failed for field '" + field + "': " + reason
        );
    }
}
