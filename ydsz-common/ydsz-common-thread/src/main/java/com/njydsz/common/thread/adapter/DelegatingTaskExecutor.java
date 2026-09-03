package com.njydsz.common.thread.adapter;

import java.util.concurrent.Callable;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

import org.springframework.core.task.TaskRejectedException;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

/**
 * 将 {@link com.njydsz.common.thread.factory.InternalExecutorFactory} 创建的受管
 * {@link ExecutorService} 适配为 Spring {@link ThreadPoolTaskExecutor}。
 *
 * <p>解决 {@code InternalExecutorFactory} 返回原生 {@link ExecutorService} 而业务服务层
 * 依赖 Spring {@link ThreadPoolTaskExecutor} 的适配问题。
 *
 * <p>所有任务提交（submit/execute）均委托给底层 {@link ExecutorService}，
 * {@code InternalExecutorFactory} 负责线程生命周期（队列策略、拒绝策略、线程命名、监控）；
 * 本类仅处理 Spring 生命周期桥接（{@code afterPropertiesSet} / {@code destroy}）与异常翻译。
 *
 * <p>使用示例：
 *
 * <pre>{@code
 * ExecutorService internal =
 *     InternalExecutorFactory.createThreadPool("search", 4, 8, 60, 256, false);
 * ThreadPoolTaskExecutor taskExecutor = DelegatingTaskExecutor.wrap(internal);
 * }</pre>
 *
 * @author ydsz-team
 * @since 26.09.01
 */
public final class DelegatingTaskExecutor extends ThreadPoolTaskExecutor {

  private static final long serialVersionUID = 1L;

  /** 底层受管线程池（由 {@link com.njydsz.common.thread.factory.InternalExecutorFactory} 统一管理） */
  private final ExecutorService delegate;

  private DelegatingTaskExecutor(ExecutorService delegate) {
    this.delegate = delegate;
    // 同步 ThreadPoolTaskExecutor 的参数显示，便于 Spring Boot Admin / Actuator 展示
    if (delegate instanceof ThreadPoolExecutor tpe) {
      super.setCorePoolSize(tpe.getCorePoolSize());
      super.setMaxPoolSize(tpe.getMaximumPoolSize());
      super.setQueueCapacity(tpe.getQueue().remainingCapacity() + tpe.getQueue().size());
      super.setKeepAliveSeconds((int) tpe.getKeepAliveTime(TimeUnit.SECONDS));
    }
    super.setThreadNamePrefix("ydsz-delegate-");
    super.setWaitForTasksToCompleteOnShutdown(true);
    super.setAwaitTerminationSeconds(5);
  }

  /**
   * 将受管 {@link ExecutorService} 包装为 Spring {@link ThreadPoolTaskExecutor}。
   *
   * @param delegate 底层线程池（由 InternalExecutorFactory 创建）
   * @return 适配后的 ThreadPoolTaskExecutor；永不为 {@code null}
   */
  public static ThreadPoolTaskExecutor wrap(ExecutorService delegate) {
    if (delegate == null) {
      throw new IllegalArgumentException("Delegate ExecutorService must not be null");
    }
    return new DelegatingTaskExecutor(delegate);
  }

  @Override
  public void execute(Runnable task) {
    try {
      delegate.execute(task);
    } catch (RejectedExecutionException ex) {
      throw new TaskRejectedException("Delegated executor rejected task: " + task, ex);
    }
  }

  @Override
  public Future<?> submit(Runnable task) {
    try {
      return delegate.submit(task);
    } catch (RejectedExecutionException ex) {
      throw new TaskRejectedException("Delegated executor rejected task: " + task, ex);
    }
  }

  @Override
  public <T> Future<T> submit(Callable<T> task) {
    try {
      return delegate.submit(task);
    } catch (RejectedExecutionException ex) {
      throw new TaskRejectedException("Delegated executor rejected task: " + task, ex);
    }
  }

  /**
   * 提交 Runnable 任务并以 {@link CompletableFuture} 返回异步结果。
   *
   * @param task 待执行的任务
   * @return 任务的异步结果 Future
   * @throws TaskRejectedException 当任务被拒绝时
   */
  public CompletableFuture<Void> submitCompletable(Runnable task) {
    CompletableFuture<Void> future = new CompletableFuture<>();
    try {
      delegate.execute(
          () -> {
            try {
              task.run();
              future.complete(null);
            } catch (Throwable ex) {
              future.completeExceptionally(ex);
            }
          });
    } catch (RejectedExecutionException ex) {
      throw new TaskRejectedException("Delegated executor rejected task: " + task, ex);
    }
    return future;
  }

  /**
   * 提交 Callable 任务并以 {@link CompletableFuture} 返回异步结果。
   *
   * @param task 待执行的可返回结果任务
   * @param <T> 返回值类型
   * @return 任务的异步结果 Future
   * @throws TaskRejectedException 当任务被拒绝时
   */
  public <T> CompletableFuture<T> submitCompletable(Callable<T> task) {
    CompletableFuture<T> future = new CompletableFuture<>();
    try {
      delegate.submit(
          () -> {
            try {
              T result = task.call();
              future.complete(result);
            } catch (Throwable ex) {
              future.completeExceptionally(ex);
            }
          });
    } catch (RejectedExecutionException ex) {
      throw new TaskRejectedException("Delegated executor rejected task: " + task, ex);
    }
    return future;
  }

  public void shutdown() {
    delegate.shutdown();
  }

  public boolean isShutdown() {
    return delegate.isShutdown();
  }

  public boolean isTerminated() {
    return delegate.isTerminated();
  }

  /**
   * 返回底层受管 {@link ExecutorService}。
   *
   * @return 底层受管 {@link ExecutorService}，便于需要直接调用 {@link ExecutorService} 接口的场景
   */
  public ExecutorService getDelegate() {
    return delegate;
  }
}
