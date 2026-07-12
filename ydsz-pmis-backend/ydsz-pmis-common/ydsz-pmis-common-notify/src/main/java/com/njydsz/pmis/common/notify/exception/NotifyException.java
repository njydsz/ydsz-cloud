package com.njydsz.pmis.common.notify.exception;

import com.njydsz.pmis.common.exception'.custom.BusinessException;
import com.njydsz.pmis.common.exception'.enums.ExceptionCategory;
import com.njydsz.pmis.common.exception'.enums.ExceptionLevel;

/**
 * 通知发送异常
 *
 * @author ydsz-pmis-team
 * 
 * @since 1.0.0
 * @since 1.0.0
 */
public class NotifyException extends BusinessException {

    private static final long serialVersionUID = 1L;

    private static final String DEFAULT_CODE = "NOTIFY_ERROR";

    public NotifyException(String message) {
        super();
        this.httpStatus = 400;
        this.level = ExceptionLevel.ERROR;
        this.category = ExceptionCategory.BUSINESS;
        this.code = DEFAULT_CODE;
        this.message = message;
        this.params = new Object[]{};
    }

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
