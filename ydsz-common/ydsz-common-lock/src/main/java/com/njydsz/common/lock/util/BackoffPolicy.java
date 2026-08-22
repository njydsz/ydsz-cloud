package com.njydsz.common.lock.util;

import java.util.concurrent.ThreadLocalRandom;

/**
 * 指数退避策略工具类
 *
 * <p>提供全抖动（Full Jitter）指数退避算法，来自 AWS 架构博客推荐， 可在高并发场景下有效分散同步请求，避免"惊群效应"。
 *
 * <pre>
 * sleep = randomBetween(0, min(maxBackoff, base * 2^attempt))
 * </pre>
 *
 * <p>使用示例：
 *
 * <pre>{@code
 * BackoffPolicy policy = new BackoffPolicy();
 * long backoff = policy.getMinBackoff();
 * while (!acquired && System.currentTimeMillis() < deadline) {
 *     long jitterSleep = policy.calculateSleepMillis(backoff);
 *     Thread.sleep(jitterSleep);
 *     backoff = policy.nextBackoff(backoff);
 * }
 * }</pre>
 *
 * @author ydsz-team
 * @since 1.0.0
 */
public final class BackoffPolicy {

  /** 默认最小退避等待时间（毫秒） */
  public static final long DEFAULT_MIN_BACKOFF_MILLIS = 10L;

  /** 默认最大退避等待时间（毫秒） */
  public static final long DEFAULT_MAX_BACKOFF_MILLIS = 200L;

  /** 最小退避等待时间（毫秒） */
  private final long minBackoffMillis;

  /** 最大退避等待时间（毫秒） */
  private final long maxBackoffMillis;

  /** 默认构造器 */
  public BackoffPolicy() {
    this(DEFAULT_MIN_BACKOFF_MILLIS, DEFAULT_MAX_BACKOFF_MILLIS);
  }

  /**
   * 自定义构造器
   *
   * @param minBackoffMillis 最小退避等待时间（毫秒）
   * @param maxBackoffMillis 最大退避等待时间（毫秒）
   */
  public BackoffPolicy(long minBackoffMillis, long maxBackoffMillis) {
    this.minBackoffMillis = minBackoffMillis;
    this.maxBackoffMillis = maxBackoffMillis;
  }

  /**
   * 获取最小退避时间
   *
   * @return 最小退避时间（毫秒）
   */
  public long getMinBackoff() {
    return minBackoffMillis;
  }

  /**
   * 获取最大退避时间
   *
   * @return 最大退避时间（毫秒）
   */
  public long getMaxBackoff() {
    return maxBackoffMillis;
  }

  /**
   * 计算本次退避睡眠时间（全抖动算法）
   *
   * <p>在 [0, backoffMillis] 范围内随机选取等待时间，避免多个线程同时唤醒造成的惊群效应。
   *
   * @param backoffMillis 当前退避时间（毫秒）
   * @return 本次应睡眠的时间（毫秒）
   */
  public long calculateSleepMillis(long backoffMillis) {
    long cappedBackoff = Math.min(backoffMillis, maxBackoffMillis);
    return ThreadLocalRandom.current().nextLong(cappedBackoff + 1);
  }

  /**
   * 计算本次退避睡眠时间（带截止时间限制）
   *
   * @param deadline 截止时间戳（毫秒）
   * @param backoffMillis 当前退避时间（毫秒）
   * @return 本次应睡眠的时间（毫秒），若已超时返回 0
   */
  public long calculateSleepMillis(long deadline, long backoffMillis) {
    long remaining = deadline - System.currentTimeMillis();
    if (remaining <= 0) {
      return 0L;
    }
    long cappedBackoff = Math.min(backoffMillis, maxBackoffMillis);
    long sleepMillis = Math.min(remaining, cappedBackoff);
    if (sleepMillis <= 0) {
      return 0L;
    }
    return ThreadLocalRandom.current().nextLong(sleepMillis + 1);
  }

  /**
   * 计算下一次退避时间
   *
   * @param currentBackoff 当前退避时间（毫秒）
   * @return 下一次退避时间（毫秒）
   */
  public long nextBackoff(long currentBackoff) {
    return Math.min(currentBackoff * 2, maxBackoffMillis);
  }

  /**
   * 执行退避睡眠
   *
   * @param backoffMillis 当前退避时间（毫秒）
    * @return 下一次退避时间（毫秒）
   * @throws InterruptedException 线程被中断
   */
  public long sleepAndNextBackoff(long backoffMillis) throws InterruptedException {
    long sleepMillis = calculateSleepMillis(backoffMillis);
    if (sleepMillis > 0) {
      Thread.sleep(sleepMillis);
    }
    return nextBackoff(backoffMillis);
  }

  /**
   * 执行退避睡眠（带截止时间限制）
   *
   * @param deadline 截止时间戳（毫秒）
   * @param backoffMillis 当前退避时间（毫秒）
   * @return 下一次退避时间（毫秒），若已超时返回当前退避时间
   */
  public long sleepAndNextBackoff(long deadline, long backoffMillis) {
    long sleepMillis = calculateSleepMillis(deadline, backoffMillis);
    if (sleepMillis > 0) {
      try {
        Thread.sleep(sleepMillis);
      } catch (InterruptedException e) {
        Thread.currentThread().interrupt();
      }
    }
    return nextBackoff(backoffMillis);
  }
}
