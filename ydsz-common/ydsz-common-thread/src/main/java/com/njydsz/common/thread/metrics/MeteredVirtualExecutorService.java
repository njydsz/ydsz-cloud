package com.njydsz.common.thread.metrics;

import java.util.Collection;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.LongAdder;

import org.springframework.lang.NonNull;

/**
 * 带指标追踪的虚拟线程执行器服务包装器。
 *
 * <p>包装 JDK 21 的虚拟线程执行器（{@link java.util.concurrent.Executors#newThreadPerTaskExecutor}），
 * 在任务提交、完成时自动同步更新关联的 {@link VirtualThreadMetrics} 计数器。
 *
 * <p>使用装饰器模式透明包装原始 {@link ExecutorService}，对调用方无侵入。
 *
 * <p>v1.4.0 变更：移除 rejected 相关逻辑（JDK 21 的虚拟线程执行器从不拒绝任务， 拒绝计数器和对应指标为不可达代码）。
 *
 * <p>v1.3.1 新增：修复虚拟线程池指标计数器空转问题。
 *
 * @author ydsz-team
 * @since 1.0.0
 * @see VirtualThreadMetrics
 */
public class MeteredVirtualExecutorService implements ExecutorService {

  private final ExecutorService delegate;
  private final VirtualThreadMetrics metrics;

  /** 已提交任务计数器（使用 LongAdder 优化高并发写入性能）。 */
  private final LongAdder submittedCount = new LongAdder();

  /** 已完成任务计数器。 */
  private final LongAdder completedCount = new LongAdder();

  /**
   * 构造带指标追踪的虚拟线程执行器服务。
   *
   * @param delegate 原始虚拟线程执行器（不可为 null）
   * @param metrics 关联的指标绑定器（不可为 null）
   */
  public MeteredVirtualExecutorService(ExecutorService delegate, VirtualThreadMetrics metrics) {
    this.delegate = delegate;
    this.metrics = metrics;
  }

  @Override
  public void execute(@NonNull Runnable command) {
    submittedCount.increment();
    metrics.incrementSubmitted();
    delegate.execute(wrapTask(command));
  }

  @Override
  public <T> Future<T> submit(@NonNull Callable<T> task) {
    submittedCount.increment();
    metrics.incrementSubmitted();
    return delegate.submit(wrapCallable(task));
  }

  @Override
  public <T> Future<T> submit(@NonNull Runnable task, T result) {
    submittedCount.increment();
    metrics.incrementSubmitted();
    return delegate.submit(wrapTask(task), result);
  }

  @Override
  public Future<?> submit(@NonNull Runnable task) {
    submittedCount.increment();
    metrics.incrementSubmitted();
    return delegate.submit(wrapTask(task));
  }

  @Override
  public <T> List<Future<T>> invokeAll(@NonNull Collection<? extends Callable<T>> tasks)
      throws InterruptedException {
    submittedCount.add(tasks.size());
    metrics.getSubmittedCount();
    return delegate.invokeAll(tasks);
  }

  @Override
  public <T> List<Future<T>> invokeAll(
      @NonNull Collection<? extends Callable<T>> tasks, long timeout, @NonNull TimeUnit unit)
      throws InterruptedException {
    submittedCount.add(tasks.size());
    return delegate.invokeAll(tasks, timeout, unit);
  }

  @Override
  public <T> T invokeAny(@NonNull Collection<? extends Callable<T>> tasks)
      throws InterruptedException, ExecutionException {
    submittedCount.add(tasks.size());
    return delegate.invokeAny(tasks);
  }

  @Override
  public <T> T invokeAny(
      @NonNull Collection<? extends Callable<T>> tasks, long timeout, @NonNull TimeUnit unit)
      throws InterruptedException, ExecutionException, TimeoutException {
    submittedCount.add(tasks.size());
    return delegate.invokeAny(tasks, timeout, unit);
  }

  @Override
  public void shutdown() {
    delegate.shutdown();
  }

  @Override
  public List<Runnable> shutdownNow() {
    return delegate.shutdownNow();
  }

  @Override
  public boolean isShutdown() {
    return delegate.isShutdown();
  }

  @Override
  public boolean isTerminated() {
    return delegate.isTerminated();
  }

  @Override
  public boolean awaitTermination(long timeout, @NonNull TimeUnit unit)
      throws InterruptedException {
    return delegate.awaitTermination(timeout, unit);
  }

  /**
   * 获取累计提交任务数。
   *
   * @return 提交总数
   */
  public long getSubmittedCount() {
    return submittedCount.sum();
  }

  /**
   * 获取累计完成任务数。
   *
   * @return 完成总数
   */
  public long getCompletedCount() {
    return completedCount.sum();
  }

  // ====================== private helpers ======================

  /** 包装 Runnable，在任务执行完成后计数。 */
  private Runnable wrapTask(Runnable task) {
    return () -> {
      try {
        task.run();
      } finally {
        completedCount.increment();
        metrics.incrementCompleted();
      }
    };
  }

  /** 包装 Callable，在任务执行完成后计数。 */
  private <T> Callable<T> wrapCallable(Callable<T> callable) {
    return () -> {
      try {
        return callable.call();
      } finally {
        completedCount.increment();
        metrics.incrementCompleted();
      }
    };
  }
}
