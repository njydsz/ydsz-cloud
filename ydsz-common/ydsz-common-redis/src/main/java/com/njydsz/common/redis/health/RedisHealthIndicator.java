package com.njydsz.common.redis.health;

import java.util.Properties;
import java.util.concurrent.atomic.AtomicReference;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.health.contributor.Health;
import org.springframework.boot.health.contributor.HealthIndicator;
import org.springframework.data.redis.connection.RedisConnection;
import org.springframework.data.redis.connection.RedisConnectionFactory;

/**
 * Redis 健康检查指示器
 *
 * <p>提供 /actuator/health/redis 端点的健康状态检查，包括：
 *
 * <ul>
 *   <li>连接可用性检测（PING/PONG）
 *   <li>PING 延迟测量
 *   <li>Redis 版本信息（server section）
 *   <li>内存使用情况（memory section）
 *   <li>连接数、Key 数量等元信息（clients + stats section）
 * </ul>
 *
 * <p><b>性能优化：</b>
 *
 * <ul>
 *   <li>INFO 调用限制为指定 section（server/memory/clients/stats），避免全量 INFO 返回过大内容
 *   <li>健康结果缓存 30 秒，避免高频监控端点反复触发 INFO 查询
 * </ul>
 *
 * @author ydsz-team
 * @since 1.0.0
 */
@Slf4j
@ConditionalOnClass(name = "org.springframework.boot.health.contributor.HealthIndicator")
public class RedisHealthIndicator implements HealthIndicator {

  /** 健康检查信息缓存时长（毫秒），避免高频轮询反复触发 INFO */
  private static final long INFO_CACHE_TTL_MS = 30_000L;

  /** 延迟降级阈值（毫秒） */
  private static final long LATENCY_DEGRADED_THRESHOLD_MS = 100L;

  /** Redis 连接工厂 */
  private final RedisConnectionFactory connectionFactory;

  /** 缓存的健康信息（带过期时间戳） */
  private final AtomicReference<CachedHealth> cachedHealth = new AtomicReference<>();

  /** 缓存的健康信息封装 */
  private static class CachedHealth {
    final Health health;
    final long expireAtMs;

    CachedHealth(Health health, long expireAtMs) {
      this.health = health;
      this.expireAtMs = expireAtMs;
    }

    boolean isExpired() {
      return System.currentTimeMillis() > expireAtMs;
    }
  }

  /**
   * 构造健康检查指示器
   *
   * @param connectionFactory Redis 连接工厂（由 Spring 注入）
   */
  public RedisHealthIndicator(RedisConnectionFactory connectionFactory) {
    this.connectionFactory = connectionFactory;
  }

  /**
   * 执行健康检查
   *
   * <p>执行步骤：
   *
   * <ol>
   *   <li>检查缓存：若 30 秒内有有效缓存则直接返回缓存结果
   *   <li>从连接工厂获取连接并发送 PING 命令
   *   <li>解析 PONG 响应与延迟
   *   <li>收集指定 section 的服务端元信息（限制 INFO 范围，避免全量返回）
   *   <li>延迟超过 100ms 时标记为 degraded
   * </ol>
   *
   * @return 健康状态 UP/DOWN，附带延迟、版本、内存等明细
   */
  @Override
  public Health health() {
    // 缓存命中：在 TTL 内直接返回缓存的健康状态（PING 仍然每次都测以获取实时延迟）
    CachedHealth cached = cachedHealth.get();
    if (cached != null && !cached.isExpired()) {
      // 对于缓存命中，仅重新测量 PING 延迟
      return refreshPingOnly(cached.health);
    }

    try {
      RedisConnection connection = connectionFactory.getConnection();
      try {
        long startTime = System.currentTimeMillis();
        String pong = connection.ping();
        long latency = System.currentTimeMillis() - startTime;

        if (!"PONG".equalsIgnoreCase(pong)) {
          return Health.down().withDetail("reason", "Unexpected PING response: " + pong).build();
        }

        Health.Builder builder =
            Health.up().withDetail("pong", pong).withDetail("latency_ms", latency);

        // 仅获取指定 section 的 INFO，避免全量返回过大内容
        fetchLimitedSections(connection, builder);

        if (latency > LATENCY_DEGRADED_THRESHOLD_MS) {
          builder.withDetail("status", "degraded");
        }

        Health health = builder.build();
        // 缓存结果（下次在 TTL 内可直接复用）
        cachedHealth.set(new CachedHealth(health, System.currentTimeMillis() + INFO_CACHE_TTL_MS));
        return health;
      } finally {
        connection.close();
      }
    } catch (Exception e) {
      log.error("【Redis】健康检查失败", e);
      return Health.down()
          .withDetail("error", e.getClass().getSimpleName())
          .withDetail("reason", e.getMessage())
          .build();
    }
  }

  /**
   * 仅刷新 PING 延迟（缓存命中时使用，避免重复查询 INFO）
   *
   * @param cached 缓存的健康结果（用于保留 INFO 信息）
   * @return 更新了实时延迟的健康结果
   */
  private Health refreshPingOnly(Health cached) {
    try {
      RedisConnection connection = connectionFactory.getConnection();
      try {
        long startTime = System.currentTimeMillis();
        connection.ping();
        long latency = System.currentTimeMillis() - startTime;

        // 保留缓存中的 INFO 信息，仅替换延迟
        Health.Builder builder = Health.status(cached.getStatus());
        cached
            .getDetails()
            .forEach(
                (key, value) -> {
                  if ("latency_ms".equals(key)) {
                    builder.withDetail(key, latency);
                  } else {
                    builder.withDetail(key, value);
                  }
                });
        if (latency > LATENCY_DEGRADED_THRESHOLD_MS) {
          builder.withDetail("status", "degraded");
        }
        return builder.build();
      } finally {
        connection.close();
      }
    } catch (Exception e) {
      // PING 失败时降级返回缓存结果
      log.warn("【Redis】健康检查 PING 刷新失败，返回缓存结果", e);
      return cached;
    }
  }

  /**
   * 获取限定 section 的 Redis INFO 信息
   *
   * <p>仅查询 server、memory、clients、stats 四个关键 section， 避免无参数 INFO 命令返回全部监控数据（生产环境可能非常大）。
   *
   * @param connection Redis 连接
   * @param builder Health 构造器
   */
  private void fetchLimitedSections(RedisConnection connection, Health.Builder builder) {
    try {
      // server section: 版本、运行模式等
      Properties serverInfo = connection.serverCommands().info("server");
      if (serverInfo != null) {
        builder.withDetail("version", serverInfo.getProperty("redis_version", "unknown"));
        builder.withDetail("redis_mode", serverInfo.getProperty("redis_mode", "unknown"));
        builder.withDetail("tcp_port", serverInfo.getProperty("tcp_port", "unknown"));
      }

      // memory section: 内存使用
      Properties memoryInfo = connection.serverCommands().info("memory");
      if (memoryInfo != null) {
        builder.withDetail(
            "used_memory_human", memoryInfo.getProperty("used_memory_human", "unknown"));
        builder.withDetail(
            "max_memory_policy", memoryInfo.getProperty("maxmemory_policy", "unknown"));
        builder.withDetail(
            "mem_fragmentation_ratio",
            memoryInfo.getProperty("mem_fragmentation_ratio", "unknown"));
      }

      // clients section: 连接数
      Properties clientsInfo = connection.serverCommands().info("clients");
      if (clientsInfo != null) {
        builder.withDetail(
            "connected_clients", clientsInfo.getProperty("connected_clients", "unknown"));
      }

      // stats section: 全局统计
      Properties statsInfo = connection.serverCommands().info("stats");
      if (statsInfo != null) {
        builder.withDetail(
            "total_commands_processed",
            statsInfo.getProperty("total_commands_processed", "unknown"));
        builder.withDetail("keyspace_hits", statsInfo.getProperty("keyspace_hits", "unknown"));
        builder.withDetail("keyspace_misses", statsInfo.getProperty("keyspace_misses", "unknown"));
      }

      // dbSize 单独获取（不属于 INFO section）
      try {
        Long dbSize = connection.serverCommands().dbSize();
        builder.withDetail("db_size", dbSize != null ? dbSize : -1);
      } catch (Exception ignored) {
        builder.withDetail("db_size", "unavailable");
      }
    } catch (Exception e) {
      builder.withDetail("server_info", "unavailable");
      log.debug("【Redis】健康检查 INFO 获取失败", e);
    }
  }
}
