package com.njydsz.cronjob.server.core.executor;

import java.util.Collection;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * ExecutorService 委托基类。
 *
 * <p>将 {@link ExecutorService} 接口的全部方法委托给内嵌的 {@code delegate} 实例。
 * 子类可覆写 {@link #execute(Runnable)} / {@link #submit} 等方法以添加上下文装饰逻辑。
 *
 * @author ydsz-team
 * @since 26.09.01
 */
public abstract class AbstractDelegatedExecutorService implements ExecutorService {

  private final ExecutorService delegate;

  protected AbstractDelegatedExecutorService(ExecutorService delegate) {
    this.delegate = delegate;
  }

  @Override
  public void execute(Runnable command) {
    delegate.execute(command);
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
  public boolean awaitTermination(long timeout, TimeUnit unit) throws InterruptedException {
    return delegate.awaitTermination(timeout, unit);
  }

  @Override
  public <T> Future<T> submit(Callable<T> task) {
    return delegate.submit(task);
  }

  @Override
  public <T> Future<T> submit(Runnable task, T result) {
    return delegate.submit(task, result);
  }

  @Override
  public Future<?> submit(Runnable task) {
    return delegate.submit(task);
  }

  @Override
  public <T> List<Future<T>> invokeAll(Collection<? extends Callable<T>> tasks)
      throws InterruptedException {
    return delegate.invokeAll(tasks);
  }

  @Override
  public <T> List<Future<T>> invokeAll(
      Collection<? extends Callable<T>> tasks, long timeout, TimeUnit unit)
      throws InterruptedException {
    return delegate.invokeAll(tasks, timeout, unit);
  }

  @Override
  public <T> T invokeAny(Collection<? extends Callable<T>> tasks)
      throws InterruptedException, ExecutionException {
    return delegate.invokeAny(tasks);
  }

  @Override
  public <T> T invokeAny(
      Collection<? extends Callable<T>> tasks, long timeout, TimeUnit unit)
      throws InterruptedException, ExecutionException, TimeoutException {
    return delegate.invokeAny(tasks, timeout, unit);
  }
}
