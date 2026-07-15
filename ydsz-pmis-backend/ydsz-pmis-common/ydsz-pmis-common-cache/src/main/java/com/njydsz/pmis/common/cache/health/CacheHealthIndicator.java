package com.njydsz.pmis.common.cache.health;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Map.Entry;
import java.util.OptionalLong;
import java.util.concurrent.ConcurrentHashMap;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.njydsz.pmis.common.cache.api.Cache;
import com.njydsz.pmis.common.cache.stats.CacheStats;

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
 */
public class CacheHealthIndicator {

  private static final Logger log = LoggerFactory.getLogger(CacheHealthIndicator.class);

  /** 健康状态枚举 */
  public enum Status {
    UP,
    WARN,
    DOWN
  }

  /** 告警阈值：命中率低于此值时状态为 WARN */
  private static final double HIT_RATE_WARN_THRESHOLD = 0.3;

  /** 告警阈值：容量使用率高于此值时状态为 WARN */
  private static final double CAPACITY_WARN_THRESHOLD = 0.9;

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

    // 命中率检查
    long totalAccess = stats.getHitCount() + stats.getMissCount();
    if (totalAccess > 100 && hitRate < HIT_RATE_WARN_THRESHOLD) {
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
          if (usage > CAPACITY_WARN_THRESHOLD) {
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

    public Status getStatus() {
      return status;
    }

    public Map<String, Object> getDetails() {
      return details;
    }

    public boolean isUp() {
      return status == Status.UP;
    }
  }
}
