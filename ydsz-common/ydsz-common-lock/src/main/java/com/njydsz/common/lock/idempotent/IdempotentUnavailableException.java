package com.njydsz.common.lock.idempotent;

import com.njydsz.common.safe.idempotent.exception.IdempotentUnavailableException;

/**
 * 幂等能力不可用异常（已迁移）
 *
 * <p><b>已迁移至 {@link com.njydsz.common.safe.idempotent.exception.IdempotentUnavailableException}。</b>
 *
 * @author ydsz-team
 * @since 26.09.01
 * @deprecated 使用 {@link com.njydsz.common.safe.idempotent.exception.IdempotentUnavailableException} 替代
 */
@Deprecated
@SuppressWarnings("all")
public class IdempotentUnavailableException extends com.njydsz.common.safe.idempotent.exception.IdempotentUnavailableException {

  public IdempotentUnavailableException(String message) {
    super(message);
  }

  public IdempotentUnavailableException(String message, Throwable cause) {
    super(message, cause);
  }
}
