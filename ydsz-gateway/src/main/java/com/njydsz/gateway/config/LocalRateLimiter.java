package com.njydsz.gateway.config;

import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;

import com.njydsz.common.cache.YdszCache;
import com.njydsz.common.cache.api.Cache;
import com.njydsz.common.cache.builder.CacheType;

/**
 * A1: 网关本地令牌桶（L1 快路径，避免每次请求都触发 Redis 网络 IO）。
 *
 * <p><b>设计定位：</b><br>
 * 原 {@code RateLimitFilter} 使用 {@code boundedElastic} 调度器包装同步 Redis 调用，存在：
 *
 * <ul>
 *   <li>boundedElastic 50 线程上限 + 1000 排队容量：5000+ QPS 场景下排队延迟</li>
 *   <li>每个请求至少一次 Redis 网络 IO</li>
 * </ul>
 *
 * <p><b>两级限流策略：</b>
 *
 * <ol>
 *   <li><b>L1（本地令牌桶）：</b>每个 IP / 用户独立维护原子令牌桶，纳秒级判定，命中即放行，无 IO</li>
 *   <li><b>L2（Redis 分布式）：</b>本地桶剩余令牌低于阈值时，降级到 RedisClusterRateLimiter 做全局计数校正</li>
 * </ol>
 *
 * <p>本地桶容量 = QPS 配置的 80%，重置频率 50ms。当本地桶耗尽（突发流量）再走 Redis 精确计数。
 *
 * <p><b>缓存实现：</b><br>
 * 使用 {@link YdszCache}（ydsz-common-cache）管理令牌桶映射，通过 {@code expireAfterAccess} 实现
 * Stale 条目的自动淘汰（替代手写 {@code @Scheduled}），符合云顶编码规范禁止使用 Caffeine / ConcurrentHashMap
 * 直接管理缓存的要求。
 *
 * <p><b>注意：</b>本组件仅作为快速路径优化，仍依赖 Redis 分布式限流保证全局准确性。
 *
 * @since 26.09.23
 * @author ydsz-team
 */
@Slf4j
public class LocalRateLimiter {

  /** 本地桶容量百分比：80%（剩余 20% 通过 Redis 校正） */
  private static final double LOCAL_BUCKET_RATIO = 0.8;

  /** 令牌重置间隔（毫秒）——每 50ms 重置一次，避免频率过高 */
  private static final long RESET_INTERVAL_MS = 50L;

  /** 令牌桶最大数量（每个 IP / 用户独立一个桶，过期后自动淘汰）。 */
  private static final long MAX_BUCKETS = 50_000L;

  /** Stale 条目过期时间（5 分钟未访问 → 自动淘汰）。 */
  private static final long EXPIRE_AFTER_ACCESS_MINUTES = 5L;

  /**
   * A1: 本地令牌桶缓存（使用 ydzs-common-cache 管理，符合编码规范；禁用 Caffeine 直接依赖）。
   *
   * <ul>
   *   <li>type=STRIPED：高并发写入场景首选（令牌桶是典型的写多读少场景）</li>
   *   <li>maximumSize：限制最大内存占用</li>
   *   <li>expireAfterAccess：替代手写 @Scheduled 淘汰逻辑</li>
   * </ul>
   */
  private final Cache<String, LocalTokenBucket> buckets;

  /** 限流配置属性（用于获取 QPS / 突发容量配置） */
  private final GatewayRateLimitProperties properties;

  /** 累计令牌放行计数（可观测）。 */
  private final AtomicLong totalAllowCount = new AtomicLong();

  /** 累计令牌拒绝计数（触发 L2 Redis 校验）。 */
  private final AtomicLong totalRejectCount = new AtomicLong();

  public LocalRateLimiter(GatewayRateLimitProperties properties) {
    this.properties = properties;
    this.buckets = YdszCache.<String, LocalTokenBucket>newBuilder()
        .type(CacheType.STRIPED)
        .name("gateway:local-ratelimit")
        .maximumSize(MAX_BUCKETS)
        .expireAfterAccess(EXPIRE_AFTER_ACCESS_MINUTES, TimeUnit.MINUTES)
        .recordStats()
        .build();
    log.info("[LocalRateLimit] 本地令牌桶初始化（L1 快路径，容量比例={}, maxBuckets={}, expireAfterAccess={}m）",
        LOCAL_BUCKET_RATIO, MAX_BUCKETS, EXPIRE_AFTER_ACCESS_MINUTES);
  }

  /**
   * A1: 尝试消耗本地令牌（L1 快路径）。
   *
   * <p>扣除逻辑：基于时间流逝增量填充令牌，优先消耗本地配额。
   *
   * @param resource 资源标识（{@code "ip:" + clientIp} 或 {@code "user:" + userId}）
   * @param isIpDimension 是否为 IP 维度（决定 QPS 配置来源）
   * @return true=本地桶有配额（快速放行）；false=本地桶耗尽（需走 L2 Redis 精确校验）
   */
  public boolean tryAcquireLocal(String resource, boolean isIpDimension) {
    LocalTokenBucket bucket = buckets.getIfPresent(resource);
    if (bucket == null) {
      LocalTokenBucket created = new LocalTokenBucket(
          effectiveQps(isIpDimension), LOCAL_BUCKET_RATIO, RESET_INTERVAL_MS);
      buckets.put(resource, created);
      // put 后重新 get 以应对并发创建场景（后者覆盖前者不影响正确性）
      bucket = buckets.getIfPresent(resource);
      if (bucket == null) {
        bucket = created;
      }
    }
    boolean allowed = bucket.tryAcquire();
    if (allowed) {
      totalAllowCount.incrementAndGet();
    } else {
      totalRejectCount.incrementAndGet();
    }
    return allowed;
  }

  /**
   * 获取维度的有效 QPS 配置。
   *
   * @param isIpDimension 是否 IP 维度
   * @return QPS 配置值
   */
  private int effectiveQps(boolean isIpDimension) {
    if (isIpDimension) {
      return properties.getPerIp().getDefaultQps();
    }
    return properties.getPerUser().getDefaultQps();
  }

  /**
   * 获取累计放行计数（L1 快路径命中）。
   *
   * @return 放行数
   */
  public long getTotalAllowCount() {
    return totalAllowCount.get();
  }

  /**
   * 获取累计拒绝计数（触发 L2 Redis 校验的次数）。
   *
   * @return 拒绝数
   */
  public long getTotalRejectCount() {
    return totalRejectCount.get();
  }

  /**
   * Spring 容器销毁时清理令牌桶缓存。
   */
  @PreDestroy
  public void cleanup() {
    buckets.invalidateAll();
    log.info("[LocalRateLimit] 本地令牌桶已清理（totalAllow={}, totalReject={}）",
        totalAllowCount.get(), totalRejectCount.get());
  }
}
