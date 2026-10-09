package com.njydsz.agent.server.agent;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;

import lombok.extern.slf4j.Slf4j;

import com.njydsz.common.thread.util.ExecutorUtils;

/**
 * 子 Agent 异步执行线程池管理器
 *
 * <p>管理用于子 Agent 并行异步执行的专用线程池。特性：
 *
 * <ul>
 *   <li>基于 JDK 21 虚拟线程，IO 密集型（LLM HTTP 调用）场景下每任务一线程，零平台线程占用</li>
 *   <li>并发度限制 = {@code min(4, availableProcessors)} × 2，通过 {@link Semaphore} 实现背压</li>
 *   <li>线程名前缀 "sub-agent-"，便于排查子 Agent 相关线程问题</li>
 *   <li>守护线程池，JVM 退出时自动回收</li>
 * </ul>
 *
 * <p>P1 迁移：原实现使用 {@code ExecutorUtils.builder()} 创建平台线程池
 * （{@code ThreadPoolExecutor}），改为 {@link ExecutorUtils#newVirtualThreadExecutor(String)} 虚拟线程池。
 * IO 密集型子 Agent 并行场景下，虚拟线程在等待 LLM HTTP 响应时自动卸载，
 * 不占用平台线程资源，可支撑更高并发度。
 *
 * <p>通过 {@link com.njydsz.common.thread.util.ExecutorUtils} 创建，
 * 禁止业务代码直接 {@code new ThreadPoolExecutor}（统一使用 ydzs-common-thread 管理能力）。
 *
 * @author ydsz-team
 * @since 26.09.17
 */
@Slf4j
public final class SubAgentExecutorPool {

  /** 并发度上限因子：核心并发 = min(4, availableProcessors) */
  private static final int MAX_CORE_CONCURRENCY = 4;

  /** 并发度 = 核心并发 × 2（应对突发子任务峰值） */
  private static final int MAX_CONCURRENCY =
      Math.min(MAX_CORE_CONCURRENCY, Runtime.getRuntime().availableProcessors()) * 2;

  /** 并发度信号量（正数=可用许可，0=满负荷） */
  private static final Semaphore CONCURRENCY_SEMAPHORE = new Semaphore(MAX_CONCURRENCY);

  /** 线程空闲存活时间（秒） */
  private static final long KEEP_ALIVE_TIME = 60L;

  /** 子 Agent 专用虚拟线程池 */
  private static final ExecutorService EXECUTOR =
      ExecutorUtils.newVirtualThreadExecutor("sub-agent-");

  private SubAgentExecutorPool() {
    throw new UnsupportedOperationException("agent.error.util_class_instantiation");
  }

  /**
   * 获取子 Agent 异步执行的线程池（虚拟线程池，带并发度限制）。
   *
   * <p>返回的 ExecutorService 使用虚拟线程，每个 submit 的任务在独立虚拟线程中执行。
   * 并发度由 {@link #CONCURRENCY_SEMAPHORE} 控制，超出限制时任务在虚拟线程内等待许可。
   *
   * @return 子 Agent 专用 {@link ExecutorService}
   */
  public static ExecutorService getExecutor() {
    return EXECUTOR;
  }

  /**
   * 获取并发度信号量，供调用方在提交任务前获取许可（防溢出）。
   *
   * @return 当前并发度 {@link Semaphore}
   */
  public static Semaphore getConcurrencySemaphore() {
    return CONCURRENCY_SEMAPHORE;
  }

  /**
   * 获取当前活跃任务数（用于监控）。
   *
   * @return 活跃任务数（已获取许可数）
   */
  public static int getActiveCount() {
    return MAX_CONCURRENCY - CONCURRENCY_SEMAPHORE.availablePermits();
  }

  /**
   * 获取当前排队等待许可的任务数（用于监控）。
   *
   * @return 排队等待的任务数
   */
  public static int getQueueSize() {
    return CONCURRENCY_SEMAPHORE.getQueueLength();
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
