package com.njydsz.common.safe.idempotent.exception;

import com.njydsz.common.exception.custom.InfraException;

/**
 * 幂等能力不可用异常（26.09.30 迁移至 InfraException 统一体系）。
 *
 * <p>当幂等检查依赖的基础设施（如 Redis）不可用、且已配置为 fail-closed （{@code
 * ydsz.safe.idempotent.fail-open=false}）时抛出，拒绝请求以保证强幂等语义。
 *
 * <p>继承 {@link InfraException} 后具备：统一错误码（HTTP 500 / SYSTEM 分类）、i18n 消息解析、
 * 异常指标统计、异常事件发布等能力。
 *
 * @author ydsz-team
 * @since 26.10.01
 */
public class IdempotentUnavailableException extends InfraException {

  private static final long serialVersionUID = 1L;

  /**
   * 以错误消息构造异常
   *
   * @param message 错误消息，需包含上下文信息
   */
  public IdempotentUnavailableException(String message) {
    super(message);
  }

  /**
   * 以错误消息与根因构造异常
   *
   * @param message 错误消息，需包含上下文信息
   * @param cause 根因异常
   */
  public IdempotentUnavailableException(String message, Throwable cause) {
    super(message, cause);
  }
}
