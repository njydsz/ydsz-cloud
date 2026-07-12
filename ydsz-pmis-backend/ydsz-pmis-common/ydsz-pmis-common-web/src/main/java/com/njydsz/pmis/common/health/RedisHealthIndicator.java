package com.njydsz.pmis.common.health;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.health.contributor.Health;
import org.springframework.boot.health.contributor.HealthIndicator;
import org.springframework.data.redis.connection.RedisConnection;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.stereotype.Component;

import java.util.Properties;

/**
 * Redis 健康检查指示器（P1-8 增强）
 *
 * <p>提供 /actuator/health/redis 端点的健康状态检查，包括：
 * <ul>
 *   <li>连接可用性检测（PING/PONG）</li>
 *   <li>PING 延迟测量（latency_ms）</li>
 *   <li>Redis 服务端版本信息（version）</li>
 *   <li>内存使用情况（used_memory / max_memory）</li>
 *   <li>延迟超过 100ms 时标记为 degraded</li>
 * </ul>
 *
 * @author ydsz-pmis-team
 * @since 1.1.0
 */
@Slf4j
@Component
public class RedisHealthIndicator implements HealthIndicator {

    /** Redis 连接工厂，用于获取连接执行 PING 与 INFO 命令 */
    private final RedisConnectionFactory connectionFactory;

    /**
     * 构造器注入 Redis 连接工厂
     *
     * @param connectionFactory Spring 管理的 Redis 连接工厂
     */
    public RedisHealthIndicator(RedisConnectionFactory connectionFactory) {
        this.connectionFactory = connectionFactory;
    }

    /**
     * 执行 Redis 健康检查
     *
     * <p>检查步骤：
     * <ol>
     *   <li>从连接工厂获取连接并发送 PING 命令</li>
     *   <li>测量 PING 延迟</li>
     *   <li>收集服务端版本、内存使用等元信息</li>
     *   <li>延迟超过 100ms 时附加 degraded 状态标记</li>
     * </ol>
     *
     * @return 健康状态对象
     */
    @Override
    public Health health() {
        try (RedisConnection connection = connectionFactory.getConnection()) {
            long startTime = System.currentTimeMillis();
            String pong = connection.ping();
            long latency = System.currentTimeMillis() - startTime;

            if (!"PONG".equalsIgnoreCase(pong)) {
                return Health.down()
                        .withDetail("error", "unexpected PING response: " + pong)
                        .build();
            }

            Health.Builder builder = Health.up()
                    .withDetail("latency_ms", latency);

            // 尝试收集 Redis 服务端元信息（版本、内存等）
            try {
                Properties info = connection.serverCommands().info();
                if (info != null) {
                    builder.withDetail("version", info.getProperty("redis_version", "unknown"));
                    builder.withDetail("used_memory", info.getProperty("used_memory_human", "unknown"));
                    builder.withDetail("max_memory", info.getProperty("max_memory_human", "unlimited"));
                    builder.withDetail("connected_clients", info.getProperty("connected_clients", "unknown"));
                }
            } catch (Exception e) {
                builder.withDetail("server_info", "unavailable");
            }

            // 延迟超过阈值标记降级
            if (latency > 100) {
                builder.withDetail("status", "DEGRADED");
                builder.withDetail("reason", "high latency: " + latency + "ms");
            }

            return builder.build();
        } catch (Exception e) {
            log.warn("[HealthCheck] Redis 健康检查失败: {}", e.getMessage());
            return Health.down()
                    .withDetail("error", e.getClass().getSimpleName())
                    .withDetail("reason", e.getMessage())
                    .build();
        }
    }
}
