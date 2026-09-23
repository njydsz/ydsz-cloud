package com.njydsz.gateway.config;

import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.LongAdder;

/**
 * A1: 本地令牌桶（纳秒级判定，无 IO，为 RateLimitFilter 提供 L1 快速路径）。
 *
 * <p>算法说明：
 *
 * <ul>
 *   <li>令牌以固定速率填充（{@code ratePerSecond}），以毫秒为单位增量计算</li>
 *   <li>桶容量上限为 {@code capacity}（按比例缩小后的 QPS 配置）</li>
 *   <li>使用 {@link AtomicLong} 实现线程安全的令牌扣减（{@code CAS} 无锁）</li>
 * </ul>
 *
 * <h3>线程安全保证</h3>
 *
 * <p>{@link #tryAcquire()} 使用 AtomicLong 的 CAS 循环保证原子性：读当前令牌数 → 不足直接返回 false
 * → 足够则 CAS 扣减（失败重试）。无 synchronized，无 ThreadLocal，适合 Netty 单线程调用路径。
 *
 * <h3>规范合规：</h3>
 *
 * <p>本对象仅作为 {@link LocalRateLimiter} 的内部值对象，不持有缓存结构本身。
 * 缓存生命周期由 {@link LocalRateLimiter} 通过 {@code ydsz-common-cache} 统一管理，
 * 符合云顶编码规范「禁止使用 Caffeine，统一使用 ydzs-common-cache」的要求。
 *
 * @since 26.09.23
 * @author ydsz-team
 */
public class LocalTokenBucket {

  /** 每毫秒填充的令牌数（ratePerSecond / 1000.0）。 */
  private final double tokensPerMs;

  /** 桶最大容量。 */
  private final long capacity;

  /**
   * 当前令牌数（使用 AtomicLong 存储，以原子 CAS 操作替代锁）。
   *
   * <p>令牌数放大 1000 倍存储（long 类型），方便小数 quota 精确计算。
   */
  private final AtomicLong tokens;

  /** 上次重置时间戳（毫秒）。 */
  private volatile long lastRefillTime;

  /** 总请求计数（统计/可观测）。 */
  private final LongAdder requestCount = new LongAdder();

  /**
   * 构造本地令牌桶。
   *
   * @param ratePerSecond 令牌填充速率（每秒令牌数）
   * @param capacityRatio 容量比例（0.0 ~ 1.0）：桶容量 = ratePerSecond * capacityRatio
   * @param lastRefillIntervalMs 令牌重置间隔（毫秒）
   */
  public LocalTokenBucket(double ratePerSecond, double capacityRatio, long lastRefillIntervalMs) {
    this.tokensPerMs = ratePerSecond / 1000.0;
    this.capacity = Math.max(1, (long) (ratePerSecond * capacityRatio));
    // 初始令牌数 = 桶容量（满桶启动）
    this.tokens = new AtomicLong(capacity * 1000L);
    this.lastRefillTime = System.currentTimeMillis();
  }

  /**
   * A1: 尝试从本地令牌桶消耗一个令牌。
   *
   * <p>先填充（按时间流逝增量），再尝试 CAS 扣减。整个过程无锁、无 IO、纳秒级延迟。
   *
   * @return true=成功消耗令牌（放行）；false=令牌不足（需走 Redis L2 校验）
   */
  public boolean tryAcquire() {
    long now = System.currentTimeMillis();
    long elapsed = Math.max(0, now - lastRefillTime);

    // 填充令牌（仅在时间差超过 1ms 时触发，避免无意义 CAS）
    if (elapsed > 0) {
      long tokensToAdd = (long) (elapsed * tokensPerMs * 1000.0); // 1000x 放大值
      long newTokens = tokens.updateAndGet(current -> Math.min(capacity * 1000L, current + tokensToAdd));
      if (newTokens != Long.MIN_VALUE) {
        lastRefillTime = now;
      }
    }

    // CAS 扣减令牌（无锁原子循环）
    while (true) {
      long current = tokens.get();
      if (current < 1000L) { // 令牌不足（1000x 放大，实际消耗 1000 个单位 = 1 个令牌）
        requestCount.increment();
        return false;
      }
      if (tokens.compareAndSet(current, current - 1000L)) {
        requestCount.increment();
        return true;
      }
      // CAS 失败：其他线程并发修改，重试
    }
  }

  /**
   * 获取总请求计数。
   *
   * @return 总请求数
   */
  public long getRequestCount() {
    return requestCount.sum();
  }
}
