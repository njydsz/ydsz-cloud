package com.njydsz.common.thread.executor;

import java.util.concurrent.Executor;

import com.njydsz.common.thread.config.ThreadPoolProperties;
import com.njydsz.common.thread.config.ThreadPoolProperties.PoolConfig;

/**
 * 线程池创建器统一接口。
 *
 * <p>抽象平台线程池与虚拟线程池的创建逻辑，便于后续扩展新线程池类型（如结构化并发 ScopedValue 池、GraalVM 轻量线程池等）时
 * 无需修改 {@link com.njydsz.common.thread.config.ThreadPoolRegistrar} 的 if-else 分支。
 *
 * <p>返回 {@link Executor} 而非 {@link java.util.concurrent.ExecutorService}，
 * 因为平台线程池由 {@link org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor} 表示，
 * 其实现了 {@link Executor} 但未直接实现 {@link java.util.concurrent.ExecutorService}。
 *
 * <p>实现类：
 *
 * <ul>
 *   <li>{@link PlatformExecutorCreator} — 平台线程池（ThreadPoolTaskExecutor）创建
 *   <li>{@link VirtualExecutorCreator} — 虚拟线程池（JDK 21+ newThreadPerTaskExecutor）创建
 * </ul>
 *
 * <p>26.09.19 新增（P2-5）：统一平台/虚拟线程池创建策略。
 *
 * @author ydsz-team
 * @since 26.09.19
 */
public interface ExecutorCreator {

  /**
   * 创建线程池执行器。
   *
   * @param name 线程池名称
   * @param config 线程池配置
   * @return 创建的 Executor 实例（可能是 {@link org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor}
   *     子类或虚拟线程 ExecutorService）
   */
  Executor createExecutor(String name, PoolConfig config);

  /**
   * 返回该创建器支持的线程池类型。
   *
   * @return 线程池类型枚举
   */
  ThreadPoolProperties.PoolType supportedType();
}
