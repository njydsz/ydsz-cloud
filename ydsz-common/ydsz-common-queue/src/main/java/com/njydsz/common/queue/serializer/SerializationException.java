package com.njydsz.common.queue.serializer;

import com.njydsz.common.exception.code.CoreExceptionCode;
import com.njydsz.common.exception.custom.SysException;

/**
 * 序列化异常（EXP-P1-001 整改 26.09.27 — 纳入 SysException 体系）。
 *
 * <p>当消息序列化或反序列化失败时抛出此异常。
 *
 * @author ydsz-team
 * @since 26.10.01
 */
public class SerializationException extends SysException {

  private static final long serialVersionUID = 1L;

  public SerializationException(String message) {
    super();
    this.code = CoreExceptionCode.INTERNAL_ERROR.getCode();
    setMessage(message);
  }

  public SerializationException(String message, Throwable cause) {
    super();
    this.code = CoreExceptionCode.INTERNAL_ERROR.getCode();
    setMessage(message);
    initCause(cause);
  }

  public SerializationException(Throwable cause) {
    super();
    this.code = CoreExceptionCode.INTERNAL_ERROR.getCode();
    initCause(cause);
  }
}
