package com.njydsz.cronjob.server.core.executor;

import java.util.concurrent.ExecutorService;

import org.springframework.core.task.TaskDecorator;

/**
 * 携带租户上下文装饰器的 ExecutorService 包装器。
 *
 * <p>在执行（execute / submit）任务前，通过 {@link TaskDecorator} 装饰 {@link Runnable}，
 * 自动从父线程传播租户快照到异步线程。
 *
 * @author ydsz-team
 * @since 26.09.01
 */
public class TenantDecoratedExecutorService extends AbstractDelegatedExecutorService {

  private final TaskDecorator taskDecorator;

  /**
   * 构造器。
   *
   * @param delegate 实际执行任务的 ExecutorService
   * @param taskDecorator 租户上下文装饰器
   */
  public TenantDecoratedExecutorService(ExecutorService delegate, TaskDecorator taskDecorator) {
    super(delegate);
    this.taskDecorator = taskDecorator;
  }

  @Override
  public void execute(Runnable command) {
    super.execute(taskDecorator.decorate(command));
  }
}
