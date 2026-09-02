package com.njydsz.common.safe.idempotent;

import java.time.Duration;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 基于 ConcurrentHashMap 的本地幂等键存储。
 *
 * <p>单节点部署时作为 Redis 不可用时的降级实现。
 *
 * <p>采用懒清理策略：每次调用 {@link #tryAcquire} 时基于计数器触发清理， 无需独立调度线程，简化资源管理。
 *
 * <p><b>注意：</b>此实现仅适用于单节点部署，分布式环境请使用 Redis 实现。
 *
 * @author ydsz-team
 * @since 26.09.01
 */
public class InMemoryIdempotentStore implements IdempotentStore {

  private static final Logger LOG = LoggerFactory.getLogger(InMemoryIdempotentStore.class);

  /**
   * 触发一次过期键清理的调用间隔。
   *
   * <p>每 N 次 tryAcquire 调用触发一次全量扫描清理过期键。
   */
  private static final int CLEANUP_INTERVAL = 1000;

  private final ConcurrentHashMap<String, Long> expireMap = new ConcurrentHashMap<>();

  private final AtomicLong callCounter = new AtomicLong(0);

  @Override
  public boolean tryAcquire(String key, Duration expire) {
    if (key == null || expire == null || expire.isNegative() || expire.isZero()) {
      return false;
    }
    long expireAt = System.currentTimeMillis() + expire.toMillis();
    Long previous = expireMap.putIfAbsent(key, expireAt);
    if (previous == null) {
      tryCleanup();
      return true;
    }
    // 检查已存在的键是否已过期
    if (previous < System.currentTimeMillis()) {
      // 已过期，尝试替换
      if (expireMap.replace(key, previous, expireAt)) {
        tryCleanup();
        return true;
      }
    }
    return false;
  }

  @Override
  public void release(String key) {
    if (key != null) {
      expireMap.remove(key);
    }
  }

  /**
   * 获取当前存储的键数量（用于监控）。
   *
   * @return 键数量
   */
  public int size() {
    return expireMap.size();
  }

  /** 尝试触发清理：每 {@value #CLEANUP_INTERVAL} 次调用执行一次过期键回收。 */
  private void tryCleanup() {
    if (callCounter.incrementAndGet() % CLEANUP_INTERVAL == 0) {
      cleanup();
    }
  }

  /** 清理所有过期的幂等键。 */
  private void cleanup() {
    long now = System.currentTimeMillis();
    expireMap.entrySet().removeIf(entry -> entry.getValue() < now);
    if (LOG.isDebugEnabled()) {
      LOG.debug("幂等键清理完成，剩余 {} 个键", expireMap.size());
    }
  }
}
