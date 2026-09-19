package com.njydsz.common.notify.exception;

import com.njydsz.common.exception.code.CoreExceptionCode;
import com.njydsz.common.exception.custom.BusinessException;
import com.njydsz.common.exception.enums.ExceptionCategory;
import com.njydsz.common.exception.enums.ExceptionLevel;

/**
 * 通知发送异常
 *
 * <p><b>P0-2 错误码体系建设</b>：支持通过 {@link NotifyExceptionCode} 设置细粒度错误码， 使调用方可编程区分失败原因（配置错误、渠道不可用、限流、熔断、内容非法等），
 * 据此决定重试、降级、告警或忽略策略。
 *
 * <p>默认错误码使用 {@link CoreExceptionCode#NOTIFY_ERROR}（B02056）， i18n 消息键 {@code notify.error}，HTTP 500。
 *
 * @author ydsz-team
 * @since 26.09.01
 */
public class NotifyException extends BusinessException {

  private static final long serialVersionUID = 1L;

  /** 通知模块细粒度错误码（P0-2 新增） */
  private NotifyExceptionCode notifyErrorCode;

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

  /**
   * 构造通知异常（带细粒度错误码，P0-2 新增）。
   *
   * <p>适用于需要调用方根据错误码做差异化处理的场景。
   *
   * @param errorCode 通知模块细粒度错误码
   * @param message 错误信息
   */
  public NotifyException(NotifyExceptionCode errorCode, String message) {
    super();
    this.notifyErrorCode = errorCode;
    initFields(errorCode.getCode(), CoreExceptionCode.NOTIFY_ERROR.getKey(), new Object[] {});
    setHttpStatus(CoreExceptionCode.NOTIFY_ERROR.getHttpStatus());
    setLevel(ExceptionLevel.ERROR);
    setCategory(ExceptionCategory.BUSINESS);
    setMessage(message);
  }

  /**
   * 构造通知异常（带细粒度错误码和原始异常，P0-2 新增）。
   *
   * @param errorCode 通知模块细粒度错误码
   * @param message 错误信息
   * @param cause 原始异常
   */
  public NotifyException(NotifyExceptionCode errorCode, String message, Throwable cause) {
    super(CoreExceptionCode.NOTIFY_ERROR, cause);
    this.notifyErrorCode = errorCode;
    setHttpStatus(CoreExceptionCode.NOTIFY_ERROR.getHttpStatus());
    setLevel(ExceptionLevel.ERROR);
    setCategory(ExceptionCategory.BUSINESS);
    setMessage(message);
  }

  /**
   * 获取通知模块细粒度错误码。
   *
   * @return 细粒度错误码，可能为 {@code null}（未设置具体错误码时）
   */
  public NotifyExceptionCode getNotifyErrorCode() {
    return notifyErrorCode;
  }

  /**
   * 判断当前异常是否包含细粒度错误码。
   *
   * @return {@code true} 表示包含细粒度错误码
   */
  public boolean hasNotifyErrorCode() {
    return notifyErrorCode != null;
  }
}
