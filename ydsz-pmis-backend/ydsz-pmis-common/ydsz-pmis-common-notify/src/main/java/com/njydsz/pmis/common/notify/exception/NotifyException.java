package com.njydsz.pmis.common.notify.exception;

import com.njydsz.pmis.common.exception.code.ExceptionCode;
import com.njydsz.pmis.common.exception.code.UnifiedExceptionCode;

/**
 * 通知发送异�?
 *
 * @author ydsz-pmis-team
 * @since 1.0.0
 */
public class NotifyException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    private final ExceptionCode exceptionCode;

    public NotifyException(String message) {
        super(message);
        this.exceptionCode = UnifiedExceptionCode.BAD_REQUEST;
    }

    public NotifyException(String message, Throwable cause) {
        super(message, cause);
        this.exceptionCode = UnifiedExceptionCode.BAD_REQUEST;
    }

    public NotifyException(ExceptionCode code, String message) {
        super(message);
        this.exceptionCode = code;
    }

    public NotifyException(ExceptionCode code, String message, Throwable cause) {
        super(message, cause);
        this.exceptionCode = code;
    }

    public ExceptionCode getExceptionCode() {
        return exceptionCode;
    }
}