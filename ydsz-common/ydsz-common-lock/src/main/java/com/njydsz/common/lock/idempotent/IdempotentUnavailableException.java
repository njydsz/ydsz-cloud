package com.njydsz.common.lock.idempotent;

/**
 * 幂等能力不可用异常（已迁移）
 *
 * <p><b>已迁移至 {@code com.njydsz.common.safe.idempotent.exception.IdempotentUnavailableException}。</b>
 *
 * @author ydsz-team
 * @since 26.09.01
 * @deprecated 使用 {@code com.njydsz.common.safe.idempotent.exception.IdempotentUnavailableException} 替代
 */
@Deprecated
public class IdempotentUnavailableException extends RuntimeException {

  public IdempotentUnavailableException(String message) {
    super(message);
  }

  public IdempotentUnavailableException(String message, Throwable cause) {
    super(message, cause);
  }
}
