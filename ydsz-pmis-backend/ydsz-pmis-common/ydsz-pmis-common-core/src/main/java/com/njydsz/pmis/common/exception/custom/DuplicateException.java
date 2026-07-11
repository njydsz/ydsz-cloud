package com.njydsz.pmis.common.exception.custom;

import com.njydsz.pmis.common.exception.code.ExceptionCode;
import com.njydsz.pmis.common.exception.code.UnifiedExceptionCode;

/**
 * 重复操作异常 —— 幂等拦截、唯一约束冲突等场景。
 * <p>
 * 对标 remi-comm DuplicateException，默认 409 Conflict。
 * </p>
 *
 * @author njydsz
 * @since 1.0.0
 */
public class DuplicateException extends AbstractPmisException {

    private static final long serialVersionUID = 1L;

    public DuplicateException(String message) {
        super(UnifiedExceptionCode.DUPLICATE_RESOURCE, message);
    }

    public DuplicateException(ExceptionCode code, String message) {
        super(code, message);
    }

    public DuplicateException(ExceptionCode code, String message, Object... args) {
        super(code, message, args);
    }

    public static DuplicateException of(String resource, String identifier) {
        return new DuplicateException(
                UnifiedExceptionCode.DUPLICATE_RESOURCE,
                "Duplicate resource: " + resource + " with identifier '" + identifier + "' already exists."
        );
    }
}
