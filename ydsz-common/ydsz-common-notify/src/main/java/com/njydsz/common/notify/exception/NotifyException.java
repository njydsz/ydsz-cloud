package com.njydsz.common.notify.exception;

import com.njydsz.common.exception.code.CoreExceptionCode;
import com.njydsz.common.exception.custom.BusinessException;
import com.njydsz.common.exception.enums.ExceptionCategory;
import com.njydsz.common.exception.enums.ExceptionLevel;

/**
 * 通知发送异常
 *
 * <p>错误码使用 {@link CoreExceptionCode#NOTIFY_ERROR}（B02056）， i18n 消息键 {@code notify.error}，HTTP 500。
 *
 * @author ydsz-team
 * @since 26.09.01
 */
public class NotifyException extends BusinessException {

  private static final long serialVersionUID = 1L;

  /**
   * 构造通知异常
   *
   * @param message 错误信息
   */
  public NotifyException(String message) {
    super();
    initFields(
        CoreExceptionCode.NOTIFY_ERROR.getCode(),
        CoreExceptionCode.NOTIFY_ERROR.getKey(),
        new Object[] {});
    setHttpStatus(CoreExceptionCode.NOTIFY_ERROR.getHttpStatus());
    setLevel(ExceptionLevel.ERROR);
    setCategory(ExceptionCategory.BUSINESS);
    setMessage(message);
  }

  /**
   * 构造通知异常（带原因）
   *
   * @param message 错误信息
   * @param cause 原始异常
   */
  public NotifyException(String message, Throwable cause) {
    super(CoreExceptionCode.NOTIFY_ERROR, cause);
    setHttpStatus(CoreExceptionCode.NOTIFY_ERROR.getHttpStatus());
    setLevel(ExceptionLevel.ERROR);
    setCategory(ExceptionCategory.BUSINESS);
    setMessage(message);
  }
}
