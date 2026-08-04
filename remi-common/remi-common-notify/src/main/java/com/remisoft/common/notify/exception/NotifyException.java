package com.remisoft.common.notify.exception;

import com.remisoft.common.exception.custom.BusinessException;
import com.remisoft.common.exception.enums.ExceptionCategory;
import com.remisoft.common.exception.enums.ExceptionLevel;

/**
 * 通知发送异常
 *
 * @author remi-team
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
        this.message = message;
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
        this.message = message;
        this.params = new Object[]{};
    }
}
