package com.njydsz.common.seata.api;

import java.time.LocalDateTime;

/**
 * SAGA 状态机日志
 *
 * <p>持久化 SAGA 事务的执行状态，支持崩溃恢复和异步驱动。
 *
 * <p><b>P1-4 修复</b>：原 SAGA 编排器是纯内存同步编排，服务崩溃后状态丢失， 已完成的正向步骤无法回滚。本日志表用于持久化每个步骤的执行状态， 支持启动时恢复未完成 SAGA
 * 实例。
 *
 * <p>状态流转：
 *
 * <pre>
 *   PENDING → EXECUTING → SUCCEEDED → COMPLETING → COMPLETED
 *                      ↘ FAILED → COMPENSATING → COMPENSATED
 *                                           ↘ COMPENSATION_FAILED
 * </pre>
 *
 * @author ydsz-team
 * @since 1.0.0
 */
public class SagaStateMachineLog {

  /** 全局事务 ID */
  private final String xid;

  /** 事务名称 */
  private final String transactionName;

  /** 当前步骤索引 */
  private int currentStepIndex;

  /** 当前步骤名称 */
  private String currentStepName;

  /** 整体状态 */
  private SagaState state;

  /** 步骤快照 JSON（记录所有步骤定义） */
  private String stepsSnapshot;

  /** 上下文数据 JSON */
  private String contextSnapshot;

  /** 创建时间 */
  private LocalDateTime createdAt;

  /** 更新时间 */
  private LocalDateTime updatedAt;

  /** 错误信息 */
  private String lastError;

  /** 重试次数 */
  private int retryCount;

  public SagaStateMachineLog(String xid, String transactionName) {
    this.xid = xid;
    this.transactionName = transactionName;
    this.state = SagaState.PENDING;
    this.currentStepIndex = 0;
    this.createdAt = LocalDateTime.now();
    this.updatedAt = LocalDateTime.now();
    this.retryCount = 0;
  }

  public String getXid() {
    return xid;
  }

  public String getTransactionName() {
    return transactionName;
  }

  public int getCurrentStepIndex() {
    return currentStepIndex;
  }

  public void setCurrentStepIndex(int currentStepIndex) {
    this.currentStepIndex = currentStepIndex;
  }

  public String getCurrentStepName() {
    return currentStepName;
  }

  public void setCurrentStepName(String currentStepName) {
    this.currentStepName = currentStepName;
  }

  public SagaState getState() {
    return state;
  }

  public void setState(SagaState state) {
    this.state = state;
    this.updatedAt = LocalDateTime.now();
  }

  public String getStepsSnapshot() {
    return stepsSnapshot;
  }

  public void setStepsSnapshot(String stepsSnapshot) {
    this.stepsSnapshot = stepsSnapshot;
  }

  public String getContextSnapshot() {
    return contextSnapshot;
  }

  public void setContextSnapshot(String contextSnapshot) {
    this.contextSnapshot = contextSnapshot;
  }

  public LocalDateTime getCreatedAt() {
    return createdAt;
  }

  public LocalDateTime getUpdatedAt() {
    return updatedAt;
  }

  public String getLastError() {
    return lastError;
  }

  public void setLastError(String lastError) {
    this.lastError = lastError;
  }

  public int getRetryCount() {
    return retryCount;
  }

  public void incrementRetryCount() {
    this.retryCount++;
  }

  /** SAGA 状态枚举 */
  public enum SagaState {
    /** 初始/待执行 */
    PENDING,
    /** 正在执行某步骤 */
    EXECUTING,
    /** 所有步骤执行成功 */
    SUCCEEDED,
    /** 正在执行最终确认 */
    COMPLETING,
    /** 执行完成 */
    COMPLETED,
    /** 某步骤执行失败 */
    FAILED,
    /** 正在执行补偿 */
    COMPENSATING,
    /** 补偿完成 */
    COMPENSATED,
    /** 补偿失败（需要人工介入） */
    COMPENSATION_FAILED;

    public boolean isFinal() {
      return this == COMPLETED || this == COMPENSATED || this == COMPENSATION_FAILED;
    }

    public boolean isCompensating() {
      return this == COMPENSATING || this == COMPENSATED || this == COMPENSATION_FAILED;
    }
  }
}
