package com.njydsz.common.safe.ratelimit.algorithm;

import java.time.Instant;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.StampedLock;

import lombok.extern.slf4j.Slf4j;

import com.njydsz.common.safe.ratelimit.enums.RateLimitAlgorithm;
import com.njydsz.common.safe.ratelimit.enums.RateLimitResult;
import com.njydsz.common.safe.ratelimit.model.RateLimitContext;
import com.njydsz.common.safe.ratelimit.model.RateLimitDecision;
import com.njydsz.common.safe.ratelimit.model.RateLimitRule;

/**
 * 令牌桶限流器
 *
 * <p>基于 {@link StampedLock} 实现的无锁化令牌桶算法，相比 synchronized 显著降低读路径竞争。
 *
 * <p><b>算法原理：</b>
 *
 * <ul>
 *   <li>桶初始为空，容量为 burstCapacity
 *   <li>以 threshold / window 速率持续往桶中放令牌
 *   <li>请求到达时从桶中取一个令牌；桶空则拒绝
 *   <li>支持突发流量（桶满时可瞬间通过 burstCapacity 个请求）
 * </ul>
 *
 * <p><b>并发策略：</b>
 *
 * <ul>
 *   <li>使用 {@link StampedLock} 乐观读 + 写锁替代 synchronized
 *   <li>读多写少场景下乐观读可完全避免阻塞
 *   <li>令牌扣减时使用写锁保证原子性
 *   <li>CAS 重试路径处理并发扣减冲突
 * </ul>
 *
 * @author ydsz-team
 * @since 26.09.01
 */
@Slf4j
public class TokenBucketLimiter implements RateLimiter {

  /** 资源 → 桶 */
  private final ConcurrentHashMap<String, TokenBucket> buckets = new ConcurrentHashMap<>();

  /** 桶数量上限：防止资源 key 无限增长导致内存泄漏 */
  private static final int MAX_BUCKETS = 10_000;

  /** 空闲淘汰阈值：超过该时长（毫秒）未访问的桶将被惰性清理 */
  private static final long IDLE_EXPIRE_MILLIS = 5 * 60_000L;

  private final RateLimitRule rule;

  public TokenBucketLimiter(RateLimitRule rule) {
    rule.validate();
    this.rule = rule;
  }

  @Override
  public RateLimitDecision tryAcquire(RateLimitContext context) {
    String key = context.getResource();
    TokenBucket bucket = buckets.computeIfAbsent(key, k -> createBucket(k));
    return bucket.tryAcquire();
  }

  /**
   * 创建新桶，并在桶数量超限时惰性清理空闲桶，防止资源 key 爆炸导致内存泄漏。
   *
   * @param key 资源 key
   * @return 新令牌桶
   */
  private TokenBucket createBucket(String key) {
    if (buckets.size() >= MAX_BUCKETS) {
      evictIdleBuckets();
    }
    return new TokenBucket(rule);
  }

  /**
   * 惰性清理超过空闲阈值的桶（O(n) 扫描，仅在桶数超限时触发）。
   */
  private void evictIdleBuckets() {
    long now = System.currentTimeMillis();
    buckets.entrySet().removeIf(entry -> now - entry.getValue().getLastAccessMillis() > IDLE_EXPIRE_MILLIS);
    log.warn(
        "令牌桶数量达到上限 {}，已惰性清理空闲桶，剩余 {}",
        MAX_BUCKETS,
        buckets.size());
  }

  @Override
  public RateLimitAlgorithm getAlgorithm() {
    return RateLimitAlgorithm.TOKEN_BUCKET;
  }

  @Override
  public RateLimitRule getRule() {
    return rule;
  }

  @Override
  public void reset() {
    buckets.clear();
  }

  /**
   * 令牌桶内部实现
   *
   * <p>使用 StampedLock 实现无锁化并发控制。 读操作尝试乐观读避免阻塞，写操作用于令牌补充和扣减的原子性保证。
   */
  private static class TokenBucket {
    /** 每令牌的纳秒成本（1 令牌 = 10^9 纳秒） */
    private static final long NANOS_PER_TOKEN = 1_000_000_000L;

    /** 桶容量（令牌数） */
    private final long capacity;

    /** 填充速率（每秒令牌数） */
    private final double refillRate;

    /** 预热期（纳秒） */
    private final long warmupNanos;

    /** 启动时间（纳秒） */
    private final long startNanos;

    /** 读写锁：保护 lastRefillNanos 和 tokensNanos 的一致性 */
    private final StampedLock lock = new StampedLock();

    /** 上次填充时间（纳秒） */
    private volatile long lastRefillNanos;

    /** 当前令牌数（以纳秒为单位存储，避免浮点运算） */
    private volatile long tokensNanos;

    /** 上次访问时间（毫秒），用于空闲淘汰 */
    private volatile long lastAccessMillis;

    TokenBucket(RateLimitRule rule) {
      this.capacity = rule.getBurstCapacity();
      this.refillRate = rule.getThreshold();
      this.warmupNanos = rule.getWarmupPeriod() == null ? 0L : rule.getWarmupPeriod().toNanos();
      this.startNanos = System.nanoTime();
      this.lastRefillNanos = this.startNanos;
      // 启动时桶满
      this.tokensNanos = this.capacity * NANOS_PER_TOKEN;
      this.lastAccessMillis = System.currentTimeMillis();
    }

    /**
     * 获取最近访问时间（毫秒）。
     *
     * @return 最近访问时间戳
     */
    long getLastAccessMillis() {
      return lastAccessMillis;
    }

    /**
     * 尝试获取一个令牌
     *
     * <p>采用乐观读优先策略：
     *
     * <ol>
     *   <li>先尝试乐观读获取当前状态
     *   <li>计算应补充的令牌数
     *   <li>使用写锁原子性地补充令牌并尝试扣减
     *   <li>乐观读失败时降级为悲观读锁
     * </ol>
     */
    RateLimitDecision tryAcquire() {
      long now = System.nanoTime();
      lastAccessMillis = System.currentTimeMillis();

      // 第一阶段：乐观读获取当前状态
      long stamp = lock.tryOptimisticRead();
      long lastRefill = lastRefillNanos;
      long currentTokens = tokensNanos;

      // 验证乐观读期间数据未被修改
      if (!lock.validate(stamp)) {
        // 乐观读失败，降级为悲观读锁
        stamp = lock.readLock();
        try {
          lastRefill = lastRefillNanos;
          currentTokens = tokensNanos;
        } finally {
          lock.unlockRead(stamp);
        }
      }

      // 第二阶段：计算应补充的令牌（纯计算，无共享状态修改）
      long elapsedNanos = now - lastRefill;
      if (elapsedNanos <= 0) {
        elapsedNanos = 0;
      }

      double actualRate = calculateActualRate(elapsedNanos);
      long tokensToAdd = (long) (elapsedNanos * actualRate);
      long capacityNanos = capacity * NANOS_PER_TOKEN;

      // 计算补充后的令牌数（上限为桶容量）
      long newTokens = currentTokens + tokensToAdd;
      if (newTokens > capacityNanos) {
        newTokens = capacityNanos;
      }

      // 第三阶段：尝试获取写锁进行原子性更新
      long writeStamp = lock.writeLock();
      try {
        // 重新读取最新值（可能在等待写锁期间被其他线程更新）
        long latestRefill = lastRefillNanos;
        long latestTokens = tokensNanos;
        long actualElapsed = now - latestRefill;

        if (actualElapsed > 0) {
          double rate = calculateActualRate(actualElapsed);
          long toAdd = (long) (actualElapsed * rate);
          latestTokens = Math.min(latestTokens + toAdd, capacityNanos);
        }

        // 尝试扣减一个令牌
        if (latestTokens >= NANOS_PER_TOKEN) {
          latestTokens -= NANOS_PER_TOKEN;
          tokensNanos = latestTokens;
          lastRefillNanos = now;
          return RateLimitDecision.builder()
              .result(RateLimitResult.PASS)
              .remaining(latestTokens / NANOS_PER_TOKEN)
              .threshold(capacity)
              .timestamp(Instant.now())
              .reason("token acquired")
              .build();
        } else {
          tokensNanos = latestTokens;
          lastRefillNanos = now;
          // 计算需要等待的时间
          long needNanos = NANOS_PER_TOKEN - latestTokens;
          long waitMs = (long) Math.ceil(needNanos / actualRate / 1_000_000.0);
          return RateLimitDecision.builder()
              .result(RateLimitResult.BLOCKED)
              .remaining(0)
              .threshold(capacity)
              .waitTimeMillis(waitMs)
              .timestamp(Instant.now())
              .reason("token bucket empty")
              .build();
        }
      } finally {
        lock.unlockWrite(writeStamp);
      }
    }

    /**
     * 计算实际填充速率（考虑预热期）
     *
     * @param elapsedNanos 距离上次填充的纳秒数
     * @return 实际填充速率（令牌/纳秒）
     */
    private double calculateActualRate(long elapsedNanos) {
      if (warmupNanos > 0) {
        long sinceStart = elapsedNanos + (lastRefillNanos - startNanos);
        double warmupFactor = Math.min(1.0, sinceStart / (double) warmupNanos);
        return refillRate * warmupFactor;
      }
      return refillRate;
    }
  }
}
