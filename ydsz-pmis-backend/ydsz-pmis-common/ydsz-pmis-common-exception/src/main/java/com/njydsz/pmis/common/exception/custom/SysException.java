package com.njydsz.pmis.common.exception.custom;

import com.njydsz.pmis.common.exception.code.ExceptionCode;
import com.njydsz.pmis.common.exception.code.UnifiedExceptionCode;

/**
 * 系统内部异常 —— 不应暴露给前端的内部错误。
 * <p>
 * 对标 remi-comm SysException，默认 500 Internal Server Error。
 * 生产环境对外消息会被替换为 "系统繁忙，请稍后重试"。
 * </p>
 *
 * @author njydsz
 * @since 1.0.0
 */
public class SysException extends AbstractPmisException {

    private static final long serialVersionUID = 1L;

    public SysException(String message) {
        super(UnifiedExceptionCode.INTERNAL_ERROR, message);
    }

    public SysException(String message, Throwable cause) {
        super(UnifiedExceptionCode.INTERNAL_ERROR, message);
        initCause(cause);
    }

    public SysException(ExceptionCode code, String message) {
        super(code, message);
    }

    public SysException(ExceptionCode code, String message, Object... args) {
        super(code, message, args);
    }

    public static SysException of(String message) {
        return new SysException(message);
    }

    public static SysException of(String message, Throwable cause) {
        SysException ex = new SysException(message);
        ex.initCause(cause);
        return ex;
    }
}
