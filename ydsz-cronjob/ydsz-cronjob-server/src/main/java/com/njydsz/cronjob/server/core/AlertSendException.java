package com.njydsz.cronjob.server.core;

import com.njydsz.common.core.code.BaseResultCode;
import com.njydsz.common.exception.custom.SysException;

/**
 * 告警发送异常（P5 告警 + 监控）。
 *
 * <p>由 {@link AlertDispatcher} 实现类在发送失败时抛出，由 {@link AlertDispatcher} 捕获并记录到 {@code
 * ydsz_job_alert_log.error_message}。
 *
 * <p>继承 {@link SysException}，纳入 common-exception 统一异常体系， 支持统一错误码、ProblemDetail (RFC
 * 7807)、i18n、异常监控等能力。 原为 checked exception（extends Exception），现改为 unchecked（extends
 * RuntimeException）， 调用方无需声明 throws，简化代码。
 *
 * @author ydsz-team
 * @since 1.0.0
 */
public class AlertSendException extends SysException {

  private static final long serialVersionUID = 1L;

  /**
   * 构造告警发送异常。
   *
   * <p>P0-FIX: SysException 无 String 构造器，改用 {@link BaseResultCode#INTERNAL_ERROR} +
   * {@link #setMessage(String)} 组装（AbstractYdszException 提供 setMessage）。
   *
   * @param message 异常描述信息
   */
  public AlertSendException(String message) {
    super(BaseResultCode.INTERNAL_ERROR);
    setMessage(message);
  }

  /**
   * 构造带根因的告警发送异常，便于链路追踪。
   *
   * @param message 异常描述信息
   * @param cause 导致发送失败的根因（如网络异常、渠道超时）
   */
  public AlertSendException(String message, Throwable cause) {
    super(BaseResultCode.INTERNAL_ERROR, cause);
    setMessage(message);
  }
}
