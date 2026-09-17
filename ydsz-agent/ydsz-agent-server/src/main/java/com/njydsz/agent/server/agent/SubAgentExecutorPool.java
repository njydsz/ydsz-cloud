package com.njydsz.agent.server.agent;

import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

import lombok.extern.slf4j.Slf4j;

import com.njydsz.common.thread.util.ExecutorUtils;

/**
 * 子 Agent 异步执行线程池管理器
 *
 * <p>管理用于子 Agent 并行异步执行的专用线程池。特性：
 *
 * <ul>
 *   <li>核心线程数 = {@code min(4, availableProcessors)}，避免子 Agent 过度占用 CPU
 *   <li>最大线程数 = 核心线程数 × 2，应对突发子任务峰值
 *   <li>有界队列容量 200，防止任务堆积时内存溢出
 *   <li>拒绝策略：CallerRunsPolicy — 调用方线程执行，提供背压而非直接丢弃
 *   <li>线程名前缀 "sub-agent-"，便于排查子 Agent 相关线程问题
 * </ul>
 *
 * <p>通过 {@link com.njydsz.common.thread.util.ExecutorUtils} Builder 创建，
 * 禁止业务代码直接 new ThreadPoolExecutor（统一使用 ydsz-common-thread 管理能力）。
 *
 * @author ydsz-team
 * @since 26.09.17
 */
@Slf4j
public final class SubAgentExecutorPool {

  /** 核心线程数上限 */
  private static final int MAX_CORE_THREADS = 4;

  /** 线程空闲存活时间（秒） */
  private static final long KEEP_ALIVE_TIME = 60L;

  /** 任务队列容量 */
  private static final int QUEUE_CAPACITY = 200;

  /** 计算核心线程数：min(4, availableProcessors) */
  private static final int CORE_POOL_SIZE =
      Math.min(MAX_CORE_THREADS, Runtime.getRuntime().availableProcessors());

  /** 最大线程数 = 核心线程数 × 2 */
  private static final int MAXIMUM_POOL_SIZE = CORE_POOL_SIZE * 2;

  /** 子 Agent 专用线程池 */
  private static final ThreadPoolExecutor EXECUTOR =
      ExecutorUtils.builder()
          .corePoolSize(CORE_POOL_SIZE)
          .maxPoolSize(MAXIMUM_POOL_SIZE)
          .keepAliveTime(KEEP_ALIVE_TIME, TimeUnit.SECONDS)
          .queueType(ExecutorUtils.BlockingQueueType.ARRAY)
          .queueCapacity(QUEUE_CAPACITY)
          .threadNamePrefix("sub-agent-")
          .daemon(true)
          .rejectedHandler(new ThreadPoolExecutor.CallerRunsPolicy())
          .build();

  private SubAgentExecutorPool() {
    throw new UnsupportedOperationException("工具类不可实例化");
  }

  /**
   * 获取子 Agent 异步执行的线程池。
   *
   * @return 子 Agent 专用 {@link ThreadPoolExecutor}
   */
  public static ThreadPoolExecutor getExecutor() {
    return EXECUTOR;
  }

  /**
   * 获取当前活跃线程数（用于监控）。
   *
   * @return 活跃线程数
   */
  public static int getActiveCount() {
    return EXECUTOR.getActiveCount();
  }

  /**
   * 获取当前队列大小（用于监控）。
   *
   * @return 队列中等待执行的任务数
   */
  public static int getQueueSize() {
    return EXECUTOR.getQueue().size();
  }

  /**
   * 优雅关闭线程池。
   *
   * <p>先调用 shutdown 停止接收新任务，等待已提交任务执行完毕；超时后强制 shutdownNow。
   *
   * @param timeoutSeconds 等待超时秒数
   */
  public static void shutdownGracefully(long timeoutSeconds) {
    EXECUTOR.shutdown();
    try {
      if (!EXECUTOR.awaitTermination(timeoutSeconds, TimeUnit.SECONDS)) {
        log.warn("[SubAgent-Pool] 超时未终止，强制关闭: timeout={}s", timeoutSeconds);
        EXECUTOR.shutdownNow();
      }
    } catch (InterruptedException e) {
      log.warn("[SubAgent-Pool] 关闭被中断，强制终止");
      EXECUTOR.shutdownNow();
      Thread.currentThread().interrupt();
    }
  }
}
