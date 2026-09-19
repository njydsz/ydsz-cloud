package com.njydsz.common.thread.executor;

import com.njydsz.common.thread.config.ThreadPoolExecutorFactory;
import com.njydsz.common.thread.config.ThreadPoolProperties.PoolConfig;
import com.njydsz.common.thread.config.ThreadPoolProperties.PoolType;

/**
 * 虚拟线程池创建器实现。
 *
 * <p>基于 {@link ThreadPoolExecutorFactory#createVirtualExecutor} 创建 JDK 21 的虚拟线程执行器
 * （{@code newThreadPerTaskExecutor}），每任务一线程。
 *
 * <p>26.09.19 新增（P2-5）。
 *
 * @author ydsz-team
 * @since 26.09.19
 */
public class VirtualExecutorCreator implements ExecutorCreator {

  private final ThreadPoolExecutorFactory factory;

  public VirtualExecutorCreator(ThreadPoolExecutorFactory factory) {
    this.factory = factory;
  }

  @Override
  public Object createExecutor(String name, PoolConfig config) {
    return factory.createVirtualExecutor(name, config);
  }

  @Override
  public PoolType supportedType() {
    return PoolType.VIRTUAL;
  }
}
