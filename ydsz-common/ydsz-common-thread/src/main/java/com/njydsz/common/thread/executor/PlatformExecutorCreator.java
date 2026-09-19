package com.njydsz.common.thread.executor;

import com.njydsz.common.thread.config.ThreadPoolExecutorFactory;
import com.njydsz.common.thread.config.ThreadPoolProperties.PoolConfig;
import com.njydsz.common.thread.config.ThreadPoolProperties.PoolType;

/**
 * 平台线程池创建器实现。
 *
 * <p>基于 {@link ThreadPoolExecutorFactory#createTaskExecutor} 创建传统的 {@link
 * org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor}。
 *
 * <p>26.09.19 新增（P2-5）。
 *
 * @author ydsz-team
 * @since 26.09.19
 */
public class PlatformExecutorCreator implements ExecutorCreator {

  private final ThreadPoolExecutorFactory factory;

  public PlatformExecutorCreator(ThreadPoolExecutorFactory factory) {
    this.factory = factory;
  }

  @Override
  public Object createExecutor(String name, PoolConfig config) {
    return factory.createTaskExecutor(name, config);
  }

  @Override
  public PoolType supportedType() {
    return PoolType.PLATFORM;
  }
}
