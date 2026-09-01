package com.njydsz.common.base.ratelimit;

import java.time.Duration;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 基于滑动窗口计数的本地限流器。
 *
 * <p>单节点部署时使用，精确度受限于内存计数。 分布式环境请使用 Redis 实现。
 *
 * <p><b>注意：</b>此实现适用于低并发场景。高并发下建议使用 Redis 版本。
 *
 * @author ydsz-team
 * @since 26.09.01
 */
public class InMemoryRateLimiter implements RateLimiter {

  private static final Logger LOG = LoggerFactory.getLogger(InMemoryRateLimiter.class);

  private final ConcurrentHashMap<String, WindowCounter> counterMap = new ConcurrentHashMap<>();

  @Override
  public boolean tryAcquire(String key, int limit, Duration window) {
    if (key == null || limit <= 0 || window == null || window.isNegative() || window.isZero()) {
      return false;
    }

    long windowMs = window.toMillis();
    long now = System.currentTimeMillis();

    WindowCounter counter = counterMap.computeIfAbsent(key, k -> new WindowCounter(windowMs));

    return counter.tryAcquire(limit, now, windowMs);
  }

  /** 滑动窗口计数器。 */
  private static class WindowCounter {

    private final long windowMs;
    private volatile long windowStart;
    private final AtomicLong count = new AtomicLong(0);

    WindowCounter(long windowMs) {
      this.windowMs = windowMs;
      this.windowStart = System.currentTimeMillis();
    }

    synchronized boolean tryAcquire(int limit, long now, long windowMs) {
      // 检查是否需要重置窗口
      if (now - windowStart >= windowMs) {
        windowStart = now;
        count.set(0);
      }
      long current = count.incrementAndGet();
      if (current > limit) {
        count.decrementAndGet();
        if (LOG.isDebugEnabled()) {
          LOG.debug("限流拒绝 | count={} | limit={}", current, limit);
        }
        return false;
      }
      return true;
    }
  }
}
