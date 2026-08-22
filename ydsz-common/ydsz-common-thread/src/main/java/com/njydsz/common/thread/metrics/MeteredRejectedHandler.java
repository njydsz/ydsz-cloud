package com.njydsz.common.thread.metrics;

import java.util.concurrent.RejectedExecutionHandler;
import java.util.concurrent.ThreadPoolExecutor;

import lombok.extern.slf4j.Slf4j;

/**
 * 带指标追踪的拒绝策略装饰器。
 *
 * <p>包装用户选择的实际拒绝策略，在拒绝触发时自动回调 {@link ThreadPoolMetrics#incrementRejected()}，确保每次拒绝事件都被 Micrometer
 * 记录。
 *
 * <p>使用方式：
 *
 * <pre>{@code
 * RejectedExecutionHandler userHandler = new ThreadPoolExecutor.AbortPolicy();
 * MeteredRejectedHandler metered = new MeteredRejectedHandler(userHandler, metrics);
 * executor.setRejectedExecutionHandler(metered);
 * }</pre>
 *
 * <p>1.0.0 新增：由 {@code ThreadPoolExecutorFactory} 自动装配，业务方无需手动包装。
 *
 * @author ydsz-team
 * @since 1.0.0
 * @see ThreadPoolMetrics
 */
@Slf4j
public class MeteredRejectedHandler implements RejectedExecutionHandler {

  private final RejectedExecutionHandler delegate;
  private final ThreadPoolMetrics metrics;

  public MeteredRejectedHandler(RejectedExecutionHandler delegate, ThreadPoolMetrics metrics) {
    this.delegate = delegate;
    this.metrics = metrics;
  }

  /**
   * 记录拒绝计数后委托给实际的拒绝策略。
   *
   * <p>计数异常不会影响原始拒绝策略的执行。
   *
   * @param r 被拒绝的任务
   * @param executor 执行器
   */
  @Override
  public void rejectedExecution(Runnable r, ThreadPoolExecutor executor) {
    try {
      metrics.incrementRejected();
    } catch (Exception e) {
      // 计数异常不影响原始拒绝策略
      log.debug("[MeteredRejectedHandler] 拒绝计数异常: {}", e.getMessage());
    }
    delegate.rejectedExecution(r, executor);
  }

  /**
   * 获取被包装的原始拒绝策略。
   *
   * @return 原始 {@link RejectedExecutionHandler}
   */
  public RejectedExecutionHandler getDelegate() {
    return delegate;
  }
}
