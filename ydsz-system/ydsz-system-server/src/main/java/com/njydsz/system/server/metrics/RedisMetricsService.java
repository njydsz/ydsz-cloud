package com.njydsz.system.server.metrics;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Properties;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

/**
 * Redis 指标采集服务（CACHE-P1-001 整改 26.09.27）。
 *
 * <p>将 {@code MetricsDashboardController} 中直接持有的 {@link StringRedisTemplate} 下沉至 server 层，
 * 使 Controller 不再直接操作 Redis（遵循分层隔离原则 / YDIZ-ARCH-003）。
 */
@Slf4j
@Service
public class RedisMetricsService {

  /** 默认 Map 初始容量 */
  private static final int DEFAULT_MAP_CAPACITY = 8;

  /** 小型 Map 初始容量 */
  private static final int SMALL_MAP_CAPACITY = 4;

  /** Redis 操作模板（可选：未装配时跳过 Redis 指标采集） */
  private final ObjectProvider<StringRedisTemplate> stringRedisTemplateProvider;

  /**
   * 构造 Redis 指标采集服务。
   *
   * @param stringRedisTemplateProvider Redis 模板提供者（可选）
   */
  public RedisMetricsService(ObjectProvider<StringRedisTemplate> stringRedisTemplateProvider) {
    this.stringRedisTemplateProvider = stringRedisTemplateProvider;
  }

  /**
   * 采集 Redis 统计指标。
   *
   * <p>读取 INFO Stats 部分的以下字段：
   * <ul>
   *   <li>{@code total_commands_processed} — 启动以来处理命令总数
   *   <li>{@code keyspace_hits} — 键命中次数
   *   <li>{@code keyspace_misses} — 键未命中次数
   * </ul>
   * 并计算命中率 = hits / (hits + misses)。
   *
   * @return Redis 指标 Map；Redis 未装配时返回空 Map
   */
  public Map<String, Object> collectRedisMetrics() {
    StringRedisTemplate redisTemplate = stringRedisTemplateProvider.getIfAvailable();
    if (redisTemplate == null) {
      return Collections.emptyMap();
    }

    try {
      Properties info = redisTemplate.getConnectionFactory().getConnection().info("stats");
      if (info == null) {
        return Collections.emptyMap();
      }

      long totalCommands = parseLongOrDefault(info.getProperty("total_commands_processed"), 0L);
      long keyspaceHits = parseLongOrDefault(info.getProperty("keyspace_hits"), 0L);
      long keyspaceMisses = parseLongOrDefault(info.getProperty("keyspace_misses"), 0L);
      long totalLookups = keyspaceHits + keyspaceMisses;
      double hitRate = totalLookups > 0
          ? Math.round((double) keyspaceHits / totalLookups * 10000.0) / 100.0
          : 0.0;

      Map<String, Object> redisMetrics = new LinkedHashMap<>(DEFAULT_MAP_CAPACITY);
      redisMetrics.put("totalCommands", totalCommands);
      redisMetrics.put("keyspaceHits", keyspaceHits);
      redisMetrics.put("keyspaceMisses", keyspaceMisses);
      redisMetrics.put("hitRate", hitRate);
      redisMetrics.put("available", true);
      return redisMetrics;
    } catch (Exception ex) {
      // Redis 指标采集异常不阻塞整体返回
      log.debug("[RedisMetrics] Redis 指标采集异常: {}", ex.getMessage());
      Map<String, Object> fallback = new LinkedHashMap<>(SMALL_MAP_CAPACITY);
      fallback.put("available", false);
      fallback.put("reason", ex.getMessage());
      return fallback;
    }
  }

  /**
   * 解析长整型字符串，解析失败返回默认值。
   *
   * @param value        字符串值
   * @param defaultValue 默认值
   * @return 解析结果或默认值
   */
  private static long parseLongOrDefault(String value, long defaultValue) {
    if (value == null || value.isEmpty()) {
      return defaultValue;
    }
    try {
      return Long.parseLong(value.trim());
    } catch (NumberFormatException e) {
      return defaultValue;
    }
  }
}
