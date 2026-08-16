package com.njydsz.common.seata.api;

import java.time.LocalDateTime;

/**
 * TCC 事务日志记录
 *
 * <p>持久化 TCC 分支事务状态，用于解决空回滚、悬挂、幂等三大经典问题。
 *
 * <p>对应数据库表 {@code tcc_transaction_log}（见 DDL）。
 *
 * @author ydsz-team
 * @since 1.0.0
 */
public class TccTransactionLog {

  /** 全局事务 ID */
  private final String xid;

  /** 分支事务 ID */
  private final String branchId;

  /** 事务名称 */
  private final String transactionName;

  /** TCC Action Bean 名称（用于跨实例恢复时查找 Action） */
  private String actionBeanName;

  /** 分支状态 */
  private TccBranchStatus status;

  /** 业务上下文快照（JSON，用于 Confirm/Cancel 恢复） */
  private String contextSnapshot;

  /** Try 开始时间 */
  private LocalDateTime tryStartedAt;

  /** Try 完成时间 */
  private LocalDateTime tryCompletedAt;

  /** Confirm/Cancel 完成时间 */
  private LocalDateTime finishedAt;

  /** 重试次数 */
  private int retryCount;

  /** 最近一次错误信息 */
  private String lastError;

  /**
   * 构造 TCC 事务日志记录
   *
   * @param xid 全局事务 ID
   * @param branchId 分支事务 ID
   * @param transactionName 事务名称
   */
  public TccTransactionLog(String xid, String branchId, String transactionName) {
    this.xid = xid;
    this.branchId = branchId;
    this.transactionName = transactionName;
    this.status = TccBranchStatus.INIT;
    this.retryCount = 0;
  }

  /**
   * 获取全局事务 ID
   *
   * @return 全局事务 ID
   */
  public String getXid() {
    return xid;
  }

  /**
   * 获取分支事务 ID
   *
   * @return 分支事务 ID
   */
  public String getBranchId() {
    return branchId;
  }

  /**
   * 获取事务名称
   *
   * @return 事务名称
   */
  public String getTransactionName() {
    return transactionName;
  }

  /**
   * 获取分支状态
   *
   * @return 分支状态
   */
  public TccBranchStatus getStatus() {
    return status;
  }

  /**
   * 设置分支状态
   *
   * @param status 新状态
   */
  public void setStatus(TccBranchStatus status) {
    this.status = status;
  }

  /**
   * 获取上下文快照（JSON 格式）
   *
   * @return 上下文快照字符串
   */
  public String getContextSnapshot() {
    return contextSnapshot;
  }

  /**
   * 设置上下文快照
   *
   * @param contextSnapshot JSON 格式的上下文快照
   */
  public void setContextSnapshot(String contextSnapshot) {
    this.contextSnapshot = contextSnapshot;
  }

  /**
   * 获取 Try 开始时间
   *
   * @return Try 开始时间
   */
  public LocalDateTime getTryStartedAt() {
    return tryStartedAt;
  }

  /**
   * 设置 Try 开始时间
   *
   * @param tryStartedAt Try 开始时间
   */
  public void setTryStartedAt(LocalDateTime tryStartedAt) {
    this.tryStartedAt = tryStartedAt;
  }

  /**
   * 获取 Try 完成时间
   *
   * @return Try 完成时间
   */
  public LocalDateTime getTryCompletedAt() {
    return tryCompletedAt;
  }

  /**
   * 设置 Try 完成时间
   *
   * @param tryCompletedAt Try 完成时间
   */
  public void setTryCompletedAt(LocalDateTime tryCompletedAt) {
    this.tryCompletedAt = tryCompletedAt;
  }

  /**
   * 获取 Confirm/Cancel 完成时间
   *
   * @return 完成时间
   */
  public LocalDateTime getFinishedAt() {
    return finishedAt;
  }

  /**
   * 设置 Confirm/Cancel 完成时间
   *
   * @param finishedAt 完成时间
   */
  public void setFinishedAt(LocalDateTime finishedAt) {
    this.finishedAt = finishedAt;
  }

  /**
   * 获取重试次数
   *
   * @return 重试次数
   */
  public int getRetryCount() {
    return retryCount;
  }

  /** 重试次数加一 */
  public void incrementRetryCount() {
    this.retryCount++;
  }

  /**
   * 获取最近一次错误信息
   *
   * @return 错误信息
   */
  public String getLastError() {
    return lastError;
  }

  /**
   * 设置最近一次错误信息
   *
   * @param lastError 错误信息
   */
  public void setLastError(String lastError) {
    this.lastError = lastError;
  }

  /**
   * 获取 TCC Action Bean 名称
   *
   * @return Bean 名称，未设置时返回 null
   */
  public String getActionBeanName() {
    return actionBeanName;
  }

  /**
   * 设置 TCC Action Bean 名称
   *
   * @param actionBeanName Spring Bean 名称
   */
  public void setActionBeanName(String actionBeanName) {
    this.actionBeanName = actionBeanName;
  }
}
