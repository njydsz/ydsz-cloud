package com.njydsz.common.audit.core;

/**
 * 审计日志写入异常
 *
 * <p>当 {@link AuditWriter} 写入失败时抛出此异常，上层 Recorder 可捕获并执行降级策略 （如磁盘兜底写入、重试等）。
 *
 * @author ydsz-team
 * @since 1.1.0
 */
public class AuditWriteException extends RuntimeException {

  private static final long serialVersionUID = 1L;

  /**
   * 构造审计写入异常
   *
   * @param message 异常消息
   */
  public AuditWriteException(String message) {
    super(message);
  }

  /**
   * 构造审计写入异常
   *
   * @param message 异常消息
   * @param cause 原始异常
   */
  public AuditWriteException(String message, Throwable cause) {
    super(message, cause);
  }

  /**
   * 构造审计写入异常
   *
   * @param cause 原始异常
   */
  public AuditWriteException(Throwable cause) {
    super(cause);
  }
}
