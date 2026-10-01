package com.njydsz.common.audit.core;

import com.njydsz.common.exception.code.CoreExceptionCode;
import com.njydsz.common.exception.custom.SysException;

/**
 * 审计日志写入异常（EXP-P1-001 整改 26.09.27 — 纳入 SysException 体系）。
 *
 * <p>当 {@link AuditWriter} 写入失败时抛出此异常，上层 Recorder 可捕获并执行降级策略（如磁盘兜底写入、重试等）。
 *
 * @author ydsz-team
 * @since 26.10.01
 */
public class AuditWriteException extends SysException {

  private static final long serialVersionUID = 1L;

  /**
   * 构造审计写入异常
   *
   * @param message 异常消息
   */
  public AuditWriteException(String message) {
    super();
    this.code = CoreExceptionCode.INTERNAL_ERROR.getCode();
    setMessage(message);
  }

  /**
   * 构造审计写入异常
   *
   * @param message 异常消息
   * @param cause 原始异常
   */
  public AuditWriteException(String message, Throwable cause) {
    super();
    this.code = CoreExceptionCode.INTERNAL_ERROR.getCode();
    setMessage(message);
    initCause(cause);
  }

  /**
   * 构造审计写入异常
   *
   * @param cause 原始异常
   */
  public AuditWriteException(Throwable cause) {
    super();
    this.code = CoreExceptionCode.INTERNAL_ERROR.getCode();
    initCause(cause);
  }
}
