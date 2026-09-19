package com.njydsz.common.thread.metrics;

import java.time.LocalDateTime;

/**
 * 慢任务执行记录（不可变）。
 *
 * <p>由 {@link TimedTaskDecorator} 在任务执行耗时超过慢任务阈值时采样记录，保留最近 N 条供故障定位。
 *
 * <p>26.09.19 新增（P2-8）：记录最近 N 条慢任务详情，辅助运维定位拖慢线程池的任务来源。
 *
 * @author ydsz-team
 * @since 26.09.19
 */
public final class SlowTaskRecord {

  /** 线程池名称 */
  private final String poolName;
  /** 任务执行耗时（毫秒） */
  private final long executionMs;
  /** 队列等待时长（毫秒） */
  private final long queueWaitMs;
  /** 任务委托类名（用于定位任务来源） */
  private final String taskClassName;
  /** 触发时间 */
  private final LocalDateTime triggeredAt;

  /**
   * 构造慢任务记录。
   *
   * @param poolName 线程池名称
   * @param executionMs 执行耗时
   * @param queueWaitMs 队列等待时长
   * @param taskClassName 任务委托类名
   * @param triggeredAt 触发时间
   */
  public SlowTaskRecord(
      String poolName,
      long executionMs,
      long queueWaitMs,
      String taskClassName,
      LocalDateTime triggeredAt) {
    this.poolName = poolName;
    this.executionMs = executionMs;
    this.queueWaitMs = queueWaitMs;
    this.taskClassName = taskClassName;
    this.triggeredAt = triggeredAt;
  }

  public String getPoolName() {
    return poolName;
  }

  public long getExecutionMs() {
    return executionMs;
  }

  public long getQueueWaitMs() {
    return queueWaitMs;
  }

  public String getTaskClassName() {
    return taskClassName;
  }

  public LocalDateTime getTriggeredAt() {
    return triggeredAt;
  }

  @Override
  public String toString() {
    return "SlowTaskRecord{pool=" + poolName
        + ", executionMs=" + executionMs
        + ", queueWaitMs=" + queueWaitMs
        + ", taskClassName='" + taskClassName + "'"
        + ", triggeredAt=" + triggeredAt + "}";
  }
}
