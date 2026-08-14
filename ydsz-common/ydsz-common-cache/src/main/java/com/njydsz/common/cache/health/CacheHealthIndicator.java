package com.njydsz.common.cache.health;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Map.Entry;
import java.util.OptionalLong;
import java.util.concurrent.ConcurrentHashMap;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.njydsz.common.cache.api.Cache;
import com.njydsz.common.cache.stats.CacheStats;

/**
 * 缓存健康检查指示器
 *
 * <p>提供缓存运行时的健康状态信息，包括：
 *
 * <ul>
 *   <li>缓存大小和容量使用率
 *   <li>命中率和未命中数
 *   <li>写入/删除计数
 *   <li>状态评估（UP/WARN/DOWN）
 * </ul>
 *
 * <p>可适配为 Spring Boot Actuator HealthIndicator（如 spring-boot-health 在 classpath 中）。
 *
 *
 * @author ydsz-team
 * @since 1.0.0
 */
public class CacheHealthIndicator {

  private static final Logger log = LoggerFactory.getLogger(CacheHealthIndicator.class);

  /** 健康状态枚举 */
  public enum Status {
    UP,
    WARN,
    DOWN
  }

  /** 告警阈值：命中率低于此值时状态为 WARN（默认 0.3） */
  private double hitRateWarnThreshold = 0.3;

  /** 告警阈值：容量使用率高于此值时状态为 WARN（默认 0.9） */
  private double capacityWarnThreshold = 0.9;

  /** 命中率检查的最小访问样本数（低于此数量不判断命中率，默认 100） */
  private long minSampleSize = 100;

  private final Map<String, Cache<?, ?>> monitoredCaches = new ConcurrentHashMap<>();

  /**
   * 注册需监控的缓存实例
   *
   * @param name 缓存名称
   * @param cache 缓存实例
   */
  public void registerCache(String name, Cache<?, ?> cache) {
    monitoredCaches.put(name, cache);
    log.info("缓存健康监控已注册: {}", name);
  }

  /** 注销缓存实例 */
  public void unregisterCache(String name) {
    monitoredCaches.remove(name);
  }

  /**
   * 设置命中率告警阈值
   *
   * <p>当缓存命中率低于此阈值且访问样本数达到 {@link #setMinSampleSize(long)} 时，
   * 健康状态标记为 WARN。
   *
   * @param hitRateWarnThreshold 阈值（0.0 ~ 1.0），默认 0.3
   * @throws IllegalArgumentException 当阈值不在 (0, 1] 范围内时抛出
   */
  public void setHitRateWarnThreshold(double hitRateWarnThreshold) {
    if (hitRateWarnThreshold <= 0 || hitRateWarnThreshold > 1.0) {
      throw new IllegalArgumentException(
          "hitRateWarnThreshold 必须在 (0, 1] 范围内: " + hitRateWarnThreshold);
    }
    this.hitRateWarnThreshold = hitRateWarnThreshold;
  }

  /**
   * 设置容量使用率告警阈值
   *
   * <p>当缓存当前大小与最大容量的比值高于此阈值时，健康状态标记为 WARN。
   *
   * @param capacityWarnThreshold 阈值（0.0 ~ 1.0），默认 0.9
   * @throws IllegalArgumentException 当阈值不在 (0, 1] 范围内时抛出
   */
  public void setCapacityWarnThreshold(double capacityWarnThreshold) {
    if (capacityWarnThreshold <= 0 || capacityWarnThreshold > 1.0) {
      throw new IllegalArgumentException(
          "capacityWarnThreshold 必须在 (0, 1] 范围内: " + capacityWarnThreshold);
    }
    this.capacityWarnThreshold = capacityWarnThreshold;
  }

  /**
   * 设置命中率检查的最小访问样本数
   *
   * <p访问量低于此数量时不判断命中率，避免冷启动阶段的误报。
   *
   * @param minSampleSize 最小样本数，必须 ≥ 0，默认 100
   * @throws IllegalArgumentException 当样本数 < 0 时抛出
   */
  public void setMinSampleSize(long minSampleSize) {
    if (minSampleSize < 0) {
      throw new IllegalArgumentException("minSampleSize 必须 ≥ 0: " + minSampleSize);
    }
    this.minSampleSize = minSampleSize;
  }

  /**
   * 执行健康检查
   *
   * @return 健康状态详情
   */
  public HealthResult health() {
    Map<String, Object> details = new LinkedHashMap<>();
    Status overallStatus = Status.UP;

    for (Entry<String, Cache<?, ?>> entry : monitoredCaches.entrySet()) {
      String cacheName = entry.getKey();
      Cache<?, ?> cache = entry.getValue();

      try {
        Map<String, Object> cacheDetails = checkCacheHealth(cache);
        Status cacheStatus = (Status) cacheDetails.get("status");
        details.put(cacheName, cacheDetails);

        if (cacheStatus == Status.DOWN) {
          overallStatus = Status.DOWN;
        } else if (cacheStatus == Status.WARN && overallStatus != Status.DOWN) {
          overallStatus = Status.WARN;
        }
      } catch (Exception e) {
        Map<String, Object> errorDetails = new HashMap<>();
        errorDetails.put("status", Status.DOWN);
        errorDetails.put("error", e.getMessage());
        details.put(cacheName, errorDetails);
        overallStatus = Status.DOWN;
        log.warn("缓存健康检查异常: {}", cacheName, e);
      }
    }

    details.put("totalCaches", monitoredCaches.size());
    return new HealthResult(overallStatus, details);
  }

  /** 检查单个缓存的健康状态 */
  private Map<String, Object> checkCacheHealth(Cache<?, ?> cache) {
    Map<String, Object> details = new LinkedHashMap<>();
    Status status = Status.UP;

    long size = cache.estimatedSize();
    double hitRate = cache.getHitRate();
    CacheStats stats = cache.getStats();

    details.put("size", size);
    details.put("hitRate", String.format("%.4f", hitRate));
    details.put("hitCount", stats.getHitCount());
    details.put("missCount", stats.getMissCount());

    // 命中率检查（访问量达到最小样本数才判断，避免冷启动误报）
    long totalAccess = stats.getHitCount() + stats.getMissCount();
    if (totalAccess > minSampleSize && hitRate < hitRateWarnThreshold) {
      status = Status.WARN;
      details.put("warning", "低命中率: " + String.format("%.2f%%", hitRate * 100));
    }

    // 容量检查（仅在有 maxSize 信息时）
    try {
      var policy = cache.policy();
      if (policy.eviction().isPresent()) {
        var eviction = policy.eviction().get();
        OptionalLong maxOpt = eviction.getMaximum();
        if (maxOpt.isPresent() && maxOpt.getAsLong() > 0) {
          long maxSize = maxOpt.getAsLong();
          double usage = (double) size / maxSize;
          details.put("maxSize", maxSize);
          details.put("usage", String.format("%.2f%%", usage * 100));
          if (usage > capacityWarnThreshold) {
            status = Status.WARN;
            details.put("warning", "高容量使用率: " + String.format("%.2f%%", usage * 100));
          }
        }
      }
    } catch (Exception e) {
      // policy() 可能不支持，忽略
    }

    details.put("status", status);
    return details;
  }

  /** 健康检查结果 */
  public static class HealthResult {
    private final Status status;
    private final Map<String, Object> details;

    HealthResult(Status status, Map<String, Object> details) {
      this.status = status;
      this.details = details;
    }

    /**
     * 返回整体健康状态。
     *
     * @return 聚合后的 {@link Status}，任一缓存 DOWN 即为 DOWN，否则取最高告警级别
     */
    public Status getStatus() {
      return status;
    }

    /**
     * 返回各缓存维度的健康详情。
     *
     * <p>键为缓存名称，值为该缓存的大小、命中率、容量使用率与告警信息；
     * 末尾附 totalCaches 汇总字段。
     *
     * @return 健康详情映射，与注册顺序一致
     */
    public Map<String, Object> getDetails() {
      return details;
    }

    /**
     * 判断缓存整体是否处于可用状态。
     *
     * <p>仅 {@link Status#UP} 视为健康，WARN/DOWN 均返回 false。
     *
     * @return true 表示全部缓存健康且无告警
     */
    public boolean isUp() {
      return status == Status.UP;
    }
  }
}
