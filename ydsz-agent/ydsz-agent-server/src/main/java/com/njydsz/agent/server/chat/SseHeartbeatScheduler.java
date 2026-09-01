package com.njydsz.agent.server.chat;

import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import com.njydsz.common.thread.util.ExecutorUtils;

/**
 * SSE 心跳统一调度器。
 *
 * <p>为所有 {@link SseExecutor} 实例提供共享的心跳调度线程池，避免每个 SSE 连接独立创建线程池导致的资源浪费。
 * 使用虚拟线程作为工作线程，平台线程仅用于调度。
 *
 * <p><b>设计说明：</b>
 *
 * <ul>
 *   <li>虚拟线程由 JDK 21+ 提供，适合 I/O 密集型的 SSE 心跳发送
 *   <li>调度线程池大小固定为 2，足以覆盖常规 SSE 连接的心跳调度需求
 *   <li>{@link SseExecutor#cleanup} 中仅取消任务，不 shutdown 共享调度器
 * </ul>
 *
 * <p><b>线程安全：</b>本类在 Spring 容器中单例存在，线程安全。
 *
 * @author ydsz-team
 * @since 1.0.0
 */
@Slf4j
@Component
public class SseHeartbeatScheduler {

  /** 调度线程池大小：2 个足够覆盖常规 SSE 连接的心跳调度需求 */
  private static final int SCHEDULER_POOL_SIZE = 2;

  /** 共享调度器实例 */
  private static volatile ScheduledExecutorService sharedScheduler;

  /** 初始化标记 */
  private final AtomicBoolean initialized = new AtomicBoolean(false);

  /** 引用计数：追踪当前使用的 SseExecutor 实例数量 */
  private final AtomicInteger referenceCount = new AtomicInteger(0);

  /**
   * 获取共享的心跳调度器。
   *
   * <p>采用懒加载方式，首次访问时初始化线程池。线程池在应用生命周期内共享，不会因单个 SSE 连接的关闭而销毁。
   *
   * @return 共享的 ScheduledExecutorService
   */
  public static ScheduledExecutorService getScheduler() {
    if (sharedScheduler == null) {
      synchronized (SseHeartbeatScheduler.class) {
        if (sharedScheduler == null) {
          sharedScheduler = createScheduler();
        }
      }
    }
    return sharedScheduler;
  }

  /**
   * 创建调度线程池。
   *
   * <p>使用虚拟线程工厂，确保心跳发送不会阻塞平台线程。
   *
   * @return 配置好的 ScheduledExecutorService
   */
  private static ScheduledExecutorService createScheduler() {
    log.info("[SseHeartbeatScheduler] 创建 SSE 心跳调度线程池，大小={}", SCHEDULER_POOL_SIZE);
    return ExecutorUtils.newScheduledThreadPool(SCHEDULER_POOL_SIZE, "agent-sse-heartbeat-");
  }

  /**
   * 增加引用计数。
   *
   * @return 增加后的引用计数
   */
  public int incrementReference() {
    int count = referenceCount.incrementAndGet();
    log.debug("[SseHeartbeatScheduler] 增加引用，当前引用数={}", count);
    return count;
  }

  /**
   * 减少引用计数。
   *
   * @return 减少后的引用计数
   */
  public int decrementReference() {
    int count = referenceCount.decrementAndGet();
    log.debug("[SseHeartbeatScheduler] 减少引用，当前引用数={}", count);
    return count;
  }

  /**
   * 应用关闭时销毁线程池。
   *
   * <p>由 Spring 容器在 Bean 销毁阶段自动回调。
   */
  @PreDestroy
  public void destroy() {
    if (sharedScheduler != null && !sharedScheduler.isShutdown()) {
      log.info("[SseHeartbeatScheduler] 销毁 SSE 心跳调度线程池");
      sharedScheduler.shutdownNow();
    }
  }
}
