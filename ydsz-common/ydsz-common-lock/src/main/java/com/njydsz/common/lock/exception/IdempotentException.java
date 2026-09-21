package com.njydsz.common.lock.exception;

import com.njydsz.common.exception.code.CoreExceptionCode;
import com.njydsz.common.exception.custom.BusinessException;
import com.njydsz.common.exception.enums.ExceptionCategory;
import com.njydsz.common.exception.enums.ExceptionLevel;

/**
 * 接口幂等性异常（已迁移）
 *
 * <p><b>已迁移至 {@code com.njydsz.common.safe.idempotent.exception.IdempotentException}。</b>
 * 本类保留原实现作为向后兼容，错误码 HTTP 409 Conflict 保持不变。
 *
 * @author ydsz-team
 * @since 26.09.01
 * @deprecated 使用 {@code com.njydsz.common.safe.idempotent.exception.IdempotentException} 替代
 */
@Deprecated
public class IdempotentException extends BusinessException {

  private static final long serialVersionUID = 1L;

  public IdempotentException(String message) {
    super();
    initFields(
        CoreExceptionCode.IDEMPOTENT_REJECT.getCode(),
        CoreExceptionCode.IDEMPOTENT_REJECT.getKey(),
        new Object[] {});
    setHttpStatus(CoreExceptionCode.IDEMPOTENT_REJECT.getHttpStatus());
    setLevel(ExceptionLevel.WARN);
    setCategory(ExceptionCategory.BUSINESS);
    setMessage(message);
  }

  public IdempotentException(String message, String idempotentKey) {
    super();
    initFields(
        CoreExceptionCode.IDEMPOTENT_REJECT.getCode(),
        CoreExceptionCode.IDEMPOTENT_REJECT.getKey(),
        new Object[] {idempotentKey});
    setHttpStatus(CoreExceptionCode.IDEMPOTENT_REJECT.getHttpStatus());
    setLevel(ExceptionLevel.WARN);
    setCategory(ExceptionCategory.BUSINESS);
    setMessage(message);
  }
}
