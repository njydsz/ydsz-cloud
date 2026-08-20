package com.njydsz.common.seata.api;

/**
 * 分布式事务异常
 *
 * <p>封装 TCC / SAGA 事务执行过程中的各类错误，包括 Confirm/Cancel 失败、
 * 签名失败、编排失败等场景。
 *
 * @author ydsz-team
 * @since 1.0.0
 */
public class TransactionException extends RuntimeException {

  private static final long serialVersionUID = 1L;

  /** 事务名称 */
  private final String transactionName;

  /** 全局事务 XID */
  private final String xid;

  /**
   * 构造分布式事务异常
   *
   * @param message 错误消息
   */
  public TransactionException(String message) {
    super(message);
    this.transactionName = null;
    this.xid = null;
  }

  /**
   * 构造分布式事务异常（带原始异常）
   *
   * @param message 错误消息
   * @param cause 原始异常
   */
  public TransactionException(String message, Throwable cause) {
    super(message, cause);
    this.transactionName = null;
    this.xid = null;
  }

  /**
   * 构造分布式事务异常（带上下文）
   *
   * @param message 错误消息
   * @param transactionName 事务名称
   * @param xid 全局事务 ID
   * @param cause 原始异常
   */
  public TransactionException(String message, String transactionName, String xid, Throwable cause) {
    super(
        String.format(
            "%s | transaction=%s | xid=%s | cause=%s",
            message, transactionName, xid, cause != null ? cause.getMessage() : "null"),
        cause);
    this.transactionName = transactionName;
    this.xid = xid;
  }

  public String getTransactionName() {
    return transactionName;
  }

  public String getXid() {
    return xid;
  }
}
