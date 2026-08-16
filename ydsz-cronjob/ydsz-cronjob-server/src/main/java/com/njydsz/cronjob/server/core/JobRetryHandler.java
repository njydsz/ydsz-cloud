package com.njydsz.cronjob.server.core;

import java.util.concurrent.TimeUnit;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * P2-1: 任务重试处理器（从 DefaultTaskDispatcher 提取）。
 *
 * <p>封装任务执行失败后的重试逻辑，支持固定间隔和指数退避两种策略。 消除 DefaultTaskDispatcher 中分散的重试代码。
 *
 * <h3>核心能力</h3>
 *
 * <ul>
 *   <li>计算下次重试的等待时间（FIXED / EXPONENTIAL）
 *   <li>判断是否已达最大重试次数
 *   <li>执行重试等待（阻塞式，带超时保护）
 * </ul>
 *
 * <h3>提取动机</h3>
 *
 * <p>DefaultTaskDispatcher 1592 行代码中约 150 行涉及重试逻辑， 提取后可统一重试策略配置和错误处理。
 *
 * @author ydsz-team
 * @since 1.0.0
 */
@Slf4j
@RequiredArgsConstructor
public class JobRetryHandler {

  /** 默认最大重试次数 */
  private static final int DEFAULT_MAX_RETRIES = 0;

  /** 默认重试间隔（毫秒） */
  private static final long DEFAULT_RETRY_INTERVAL_MS = 0L;

  /** 指数退避基准 */
  private static final long EXPONENTIAL_BASE_MS = 1000L;

  /** 指数退避上限 */
  private static final long EXPONENTIAL_MAX_MS = 60_000L;

  /**
   * 判断任务是否应该重试。
   *
   * @param currentRetryCount 当前已重试次数
   * @param maxRetries 最大重试次数（0=不重试）
   * @return true=应重试
   */
  public boolean shouldRetry(int currentRetryCount, Integer maxRetries) {
    int max = maxRetries != null ? maxRetries : DEFAULT_MAX_RETRIES;
    return currentRetryCount < max;
  }

  /**
   * 计算重试等待时间。
   *
   * @param retryCount 当前重试次数（从 0 开始）
   * @param retryIntervalMs 重试间隔（毫秒，null=立即重试）
   * @param backoffStrategy 退避策略：FIXED / EXPONENTIAL
   * @return 等待时间（毫秒）
   */
  public long calculateRetryDelay(int retryCount, Long retryIntervalMs, String backoffStrategy) {
    long baseInterval = retryIntervalMs != null ? retryIntervalMs : DEFAULT_RETRY_INTERVAL_MS;

    if ("EXPONENTIAL".equalsIgnoreCase(backoffStrategy)) {
      // 指数退避：base * 2^retryCount，上限 60s
      long delay = baseInterval * (1L << Math.min(retryCount, 6));
      return Math.min(delay, EXPONENTIAL_MAX_MS);
    }
    // 默认固定间隔
    return baseInterval;
  }

  /**
   * 执行重试等待。
   *
   * @param retryCount 当前重试次数
   * @param retryIntervalMs 重试间隔
   * @param backoffStrategy 退避策略
   */
  public void waitForRetry(int retryCount, Long retryIntervalMs, String backoffStrategy) {
    long delayMs = calculateRetryDelay(retryCount, retryIntervalMs, backoffStrategy);
    if (delayMs <= 0) {
      return;
    }
    // 阻塞等待：限制最大阻塞时间，避免线程被占用过久
    try {
      long cappedDelay = Math.min(delayMs, 30_000L);
      log.info(
          "[JobRetry] 等待重试: retryCount={} delay={}ms strategy={}",
          retryCount,
          cappedDelay,
          backoffStrategy);
      TimeUnit.MILLISECONDS.sleep(cappedDelay);
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      log.warn("[JobRetry] 重试等待被中断: retryCount={}", retryCount);
    }
  }

  /**
   * 计算下次重试的超时时间戳。
   *
   * @param retryCount 当前重试次数
   * @param retryIntervalMs 重试间隔
   * @param backoffStrategy 退避策略
   * @return 超时时间戳（epoch millis）；0=立即重试
   */
  public long calculateRetryDeadline(int retryCount, Long retryIntervalMs, String backoffStrategy) {
    long delay = calculateRetryDelay(retryCount, retryIntervalMs, backoffStrategy);
    if (delay <= 0) {
      return 0L;
    }
    return System.currentTimeMillis() + delay;
  }
}
