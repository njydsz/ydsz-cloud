package com.njydsz.common.lock.idempotent;

/**
 * 幂等能力不可用异常
 *
 * <p>当幂等检查依赖的基础设施（如 Redis）不可用、且已配置为 fail-closed （{@code
 * ydsz.lock.idempotent.fail-open=false}）时抛出，拒绝请求以保证强幂等语义。
 *
 * @author ydsz-team
 * @since 1.0.0
 */
public class IdempotentUnavailableException extends RuntimeException {

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
