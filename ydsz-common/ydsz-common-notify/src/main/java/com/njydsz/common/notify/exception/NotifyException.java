package com.njydsz.common.notify.exception;

import com.njydsz.common.exception.custom.BusinessException;
import com.njydsz.common.exception.enums.ExceptionCategory;
import com.njydsz.common.exception.enums.ExceptionLevel;

/**
 * 通知发送异常
 *
 * @author ydsz-team
 * @since 1.0.0
 */
public class NotifyException extends BusinessException {

    private static final long serialVersionUID = 1L;

    private static final String DEFAULT_CODE = "NOTIFY_ERROR";

    /**
     * 构造通知异常
     *
     * @param message 错误信息
     */
    public NotifyException(String message) {
        super();
        this.httpStatus = 400;
        this.level = ExceptionLevel.ERROR;
        this.category = ExceptionCategory.BUSINESS;
        this.code = DEFAULT_CODE;
        setMessage(message);
        this.params = new Object[]{};
    }

    /**
     * 构造通知异常（带原因）
     *
     * @param message 错误信息
     * @param cause   原始异常
     */
    public NotifyException(String message, Throwable cause) {
        super(cause);
        this.httpStatus = 400;
        this.level = ExceptionLevel.ERROR;
        this.category = ExceptionCategory.BUSINESS;
        this.code = DEFAULT_CODE;
        setMessage(message);
        this.params = new Object[]{};
    }
}
