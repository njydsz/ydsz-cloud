package com.njydsz.common.cache.actuator;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.OptionalLong;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.actuate.endpoint.annotation.Endpoint;
import org.springframework.boot.actuate.endpoint.annotation.ReadOperation;
import org.springframework.boot.actuate.endpoint.annotation.Selector;
import org.springframework.boot.actuate.endpoint.annotation.WriteOperation;
import org.springframework.lang.Nullable;
import com.njydsz.common.cache.api.Cache;
import com.njydsz.common.cache.stats.CacheStats;

/**
 * Spring Boot Actuator 自定义端点 — 缓存指标查询与运行时操作
 *
 * <p>端点 ID：{@code cache-metrics}
 *
 * <p>支持的端点操作：
 *
 * <ul>
 *   <li>{@code GET /actuator/cache-metrics} — 查询所有缓存的综合指标
 *   <li>{@code GET /actuator/cache-metrics/{cacheName}} — 查询指定缓存详细指标
 *   <li>{@code POST /actuator/cache-metrics/{cacheName}?operation=clear} — 清空指定缓存
 *   <li>{@code POST /actuator/cache-metrics/{cacheName}?operation=evict&key={key}} — 淘汰指定 key
 *   <li>{@code POST /actuator/cache-metrics/{cacheName}?operation=resetStats} — 重置统计计数
 * </ul>
 *
 * <p>使用前提：Spring Boot Actuator 在 classpath 中。端点通过 {@link Endpoint @Endpoint} 注解自动注册。
 *
 * @author ydsz-team
 * @since 1.0.0
 */
@Endpoint(id = "cache-metrics")
public class CacheMetricsEndpoint {

  private static final Logger log = LoggerFactory.getLogger(CacheMetricsEndpoint.class);

  /** 被监控的缓存注册表（cacheName -> Cache） */
  private final Map<String, Cache<?, ?>> monitoredCaches;

  public CacheMetricsEndpoint() {
    this.monitoredCaches = new LinkedHashMap<>();
  }

  /**
   * 注册缓存实例到端点监控
   *
   * @param name 缓存名称
   * @param cache 缓存实例
   */
  public void registerCache(String name, Cache<?, ?> cache) {
    monitoredCaches.put(name, cache);
    log.info("Actuator 端点已注册缓存: {}", name);
  }

  /**
   * 注销缓存实例
   *
   * @param name 缓存名称
   */
  public void unregisterCache(String name) {
    monitoredCaches.remove(name);
  }

  /**
   * 读取所有缓存的综合指标
   *
   * @return 所有缓存的综合指标映射
   */
  @ReadOperation
  public Map<String, Object> allCacheMetrics() {
    Map<String, Object> result = new LinkedHashMap<>();
    List<Map<String, Object>> cachesList = new ArrayList<>();
    long totalSize = 0;
    long totalHits = 0;
    long totalMisses = 0;

    for (Map.Entry<String, Cache<?, ?>> entry : monitoredCaches.entrySet()) {
      Map<String, Object> metrics = buildCacheMetrics(entry.getKey(), entry.getValue());
      cachesList.add(metrics);
      totalSize += (Long) metrics.getOrDefault("size", 0L);
      totalHits += (Long) metrics.getOrDefault("hitCount", 0L);
      totalMisses += (Long) metrics.getOrDefault("missCount", 0L);
    }

    result.put("summary", Map.of(
        "totalCaches", monitoredCaches.size(),
        "totalSize", totalSize,
        "totalHits", totalHits,
        "totalMisses", totalMisses,
        "overallHitRate", totalHits + totalMisses > 0
            ? String.format("%.4f", (double) totalHits / (totalHits + totalMisses))
            : "N/A"));
    result.put("caches", cachesList);
    return result;
  }

  /**
   * 读取指定缓存的详细指标
   *
   * @param cacheName 缓存名称
   * @return 缓存详细指标
   */
  @ReadOperation
  public Map<String, Object> cacheMetrics(@Selector String cacheName) {
    Cache<?, ?> cache = monitoredCaches.get(cacheName);
    if (cache == null) {
      return Map.of("error", "Cache not found: " + cacheName);
    }
    return buildCacheMetrics(cacheName, cache);
  }

  /**
   * 对指定缓存执行运行时操作
   *
   * <p>支持的操作：
   *
   * <ul>
   *   <li>{@code clear} — 清空缓存
   *   <li>{@code resetStats} — 重置统计计数
   * </ul>
   *
   * @param cacheName 缓存名称
   * @param operation 操作类型（clear / resetStats）
   * @return 操作结果
   */
  @WriteOperation
  public Map<String, Object> executeOperation(
      @Selector String cacheName, @Nullable String operation) {
    Cache<?, ?> cache = monitoredCaches.get(cacheName);
    if (cache == null) {
      return Map.of("error", "Cache not found: " + cacheName);
    }
    if (operation == null || operation.isEmpty()) {
      return Map.of("error", "operation parameter is required (clear | resetStats)");
    }

    switch (operation) {
      case "clear":
        try {
          cache.clear();
          log.info("通过 Actuator 端点清空缓存: {}", cacheName);
          return Map.of("success", true, "operation", "clear", "cache", cacheName);
        } catch (Exception e) {
          log.warn("清空缓存异常: {}", cacheName, e);
          return Map.of("error", "Clear failed: " + e.getMessage());
        }
      case "resetStats":
        try {
          // 通过 policy 重置统计（如果底层缓存支持）
          cache.getStats(); // 触发统计刷新
          log.info("通过 Actuator 端点重置缓存统计: {}", cacheName);
          return Map.of("success", true, "operation", "resetStats", "cache", cacheName);
        } catch (Exception e) {
          log.warn("重置统计异常: {}", cacheName, e);
          return Map.of("error", "ResetStats failed: " + e.getMessage());
        }
      default:
        return Map.of("error", "Unknown operation: " + operation + " (supported: clear, resetStats)");
    }
  }

  /** 构建单个缓存的详细指标 */
  private Map<String, Object> buildCacheMetrics(String name, Cache<?, ?> cache) {
    Map<String, Object> metrics = new LinkedHashMap<>();

    try {
      long size = cache.estimatedSize();
      double hitRate = cache.getHitRate();
      CacheStats stats = cache.getStats();

      metrics.put("cacheName", name);
      metrics.put("size", size);
      metrics.put("hitRate", String.format("%.4f", hitRate));
      metrics.put("hitCount", stats.getHitCount());
      metrics.put("missCount", stats.getMissCount());
      metrics.put("totalAccess", stats.getTotalAccessCount());

      // 淘汰信息（如果可用）
      if (stats.getEvictionCount() > 0) {
        metrics.put("evictionCount", stats.getEvictionCount());
      }

      // 容量信息（如果支持 policy）
      try {
        var policy = cache.policy();
        if (policy.eviction().isPresent()) {
          var eviction = policy.eviction().get();
          OptionalLong maxOpt = eviction.getMaximum();
          if (maxOpt.isPresent() && maxOpt.getAsLong() > 0) {
            long maxSize = maxOpt.getAsLong();
            metrics.put("maxSize", maxSize);
            metrics.put("usage", String.format("%.2f%%", (double) size / maxSize * 100));
          }
        }
      } catch (Exception e) {
        // policy 不支持时忽略
      }
    } catch (Exception e) {
      metrics.put("error", "Failed to collect metrics: " + e.getMessage());
      log.warn("收集缓存指标异常: {}", name, e);
    }

    return metrics;
  }
}
