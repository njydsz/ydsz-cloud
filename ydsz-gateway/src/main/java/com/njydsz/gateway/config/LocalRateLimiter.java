package com.njydsz.gateway.config;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;

/**
 * A1: 网关本地令牌桶（L1 快速路径，避免每次请求都触发 Redis 网络 IO）。
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
 * 此举在高 QPS 场景下（>3000）可将限流延迟从毫秒级降至纳秒级（本地桶命中路径）。
 *
 * <p><b>注意：</b>本组件仅作为快速路径优化，仍依赖 Redis 分布式限流保证全局准确性。
 * 本地令牌桶的 "80% capacity" 设计确保突发流量走 Redis 精确计数，防止短时间超发。
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

  /** 本地令牌桶映射（resource → bucket）。 */
  private final ConcurrentMap<String, LocalTokenBucket> buckets = new ConcurrentHashMap<>(256);

  /** 限流配置属性（用于获取 QPS / 突发容量配置） */
  private final GatewayRateLimitProperties properties;

  public LocalRateLimiter(GatewayRateLimitProperties properties) {
    this.properties = properties;
    log.info("[LocalRateLimit] 本地令牌桶初始化（L1 快速路径，容量比例={}）", LOCAL_BUCKET_RATIO);
  }

  /**
   * A1: 尝试消耗本地令牌（L1 快速路径）。
   *
   * <p>扣除逻辑：基于时间流逝增量填充令牌，优先消耗本地配额。
   *
   * @param resource 资源标识（{@code "ip:" + clientIp} 或 {@code "user:" + userId}）
   * @param isIpDimension 是否为 IP 维度（决定 QPS 配置来源）
   * @return true=本地桶有配额（快速放行）；false=本地桶耗尽（需走 L2 Redis 精确校验）
   */
  public boolean tryAcquireLocal(String resource, boolean isIpDimension) {
    LocalTokenBucket bucket = buckets.computeIfAbsent(resource,
        k -> new LocalTokenBucket(
            effectiveQps(isIpDimension), LOCAL_BUCKET_RATIO, RESET_INTERVAL_MS));
    return bucket.tryAcquire();
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
   * 清理过期的本地令牌桶（防止内存泄漏）。
   *
   * <p>定时清理超过 5 分钟未访问的桶（Scheduled 每 5 分钟执行一次）。
   */
  @Scheduled(fixedDelay = 300_000)
  public void evictStaleBuckets() {
    int before = buckets.size();
    buckets.entrySet().removeIf(e -> e.getValue().isStale(300_000));
    int after = buckets.size();
    if (before != after) {
      log.debug("[LocalRateLimit] 清理过期令牌桶：{} → {}", before, after);
    }
  }

  /**
   * Spring 容器销毁时清理全部令牌桶。
   */
  @PreDestroy
  public void cleanup() {
    buckets.clear();
    log.info("[LocalRateLimit] 本地令牌桶已清理");
  }
}
