package com.njydsz.cronjob.server.core.config;

import java.util.concurrent.RejectedExecutionHandler;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.atomic.AtomicLong;

/**
 * P2-E1: 拒绝任务计数处理器。
 *
 * <p>包装原始 {@link RejectedExecutionHandler}，在任务被拒绝时递增计数，
 * 供 {@link CronjobThreadPoolRegistry#getMetrics()} 与 {@code /actuator/threadpools} 端点观测
 * 线程池拒绝压力（原实现中 {@code rejectedExecutionCount} 硬编码为 0，无法观测）。
 *
 * <p>委托链：本处理器只做计数，实际拒绝策略（AbortPolicy / CallerRunsPolicy 等）由 delegate 决定，行为不变。
 *
 * @author ydsz-team
 * @since 1.2.0
 */
public class CountingRejectedExecutionHandler implements RejectedExecutionHandler {

  private final RejectedExecutionHandler delegate;

  private final AtomicLong rejectedCount;

  /**
   * 构造计数拒绝处理器。
   *
   * @param delegate 原始拒绝策略（如 {@code ThreadPoolExecutor.CallerRunsPolicy}）
   * @param rejectedCount 共享计数（由 Registry 持有，跨注册/注销保持可见）
   */
  public CountingRejectedExecutionHandler(
      RejectedExecutionHandler delegate, AtomicLong rejectedCount) {
    this.delegate = delegate;
    this.rejectedCount = rejectedCount;
  }

  @Override
  public void rejectedExecution(Runnable r, ThreadPoolExecutor executor) {
    rejectedCount.incrementAndGet();
    delegate.rejectedExecution(r, executor);
  }

  /** 当前累计拒绝次数。 */
  public long getRejectedCount() {
    return rejectedCount.get();
  }
}
