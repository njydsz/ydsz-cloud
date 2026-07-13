package com.njydsz.pmis.common.redis.health;

import java.util.Properties;

import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.health.contributor.Health;
import org.springframework.boot.health.contributor.HealthIndicator;
import org.springframework.data.redis.connection.RedisConnection;
import org.springframework.data.redis.connection.RedisConnectionFactory;

import lombok.extern.slf4j.Slf4j;

/**
 * Redis 健康检查指示器
 *
 * <p>提供 /actuator/health/redis 端点的健康状态检查，包括：
 * <ul>
 *   <li>连接可用性检测</li>
 *   <li>PING/PONG 延迟测量</li>
 *   <li>Redis 版本信息</li>
 *   <li>内存使用情况</li>
 * </ul>
 *
 * @author Marvin Lee
 * @email limw1888@126.com
 * @version 3.5.0
 * @since 1.0.0
 */
@Slf4j
@ConditionalOnClass(name = "org.springframework.boot.health.contributor.HealthIndicator")
public class RedisHealthIndicator implements HealthIndicator {

    /**
     * Redis 连接工厂
     */
    private final RedisConnectionFactory connectionFactory;

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
     * <p>执行步骤：</p>
     * <ol>
     *   <li>从连接工厂获取连接并发送 PING 命令</li>
     *   <li>解析 PONG 响应与延迟</li>
     *   <li>收集服务端版本、内存使用等元信息</li>
     *   <li>延迟超过 100ms 时标记为 degraded</li>
     * </ol>
     *
     * @return 健康状态 UP/DOWN，附带延迟、版本、内存等明细
     */
    @Override
    public Health health() {
        try {
            RedisConnection connection = connectionFactory.getConnection();
            try {
                long startTime = System.currentTimeMillis();
                String pong = connection.ping();
                long latency = System.currentTimeMillis() - startTime;

                if (!"PONG".equalsIgnoreCase(pong)) {
                    return Health.down()
                            .withDetail("reason", "Unexpected PING response: " + pong)
                            .build();
                }

                Health.Builder builder = Health.up()
                        .withDetail("pong", pong)
                        .withDetail("latency_ms", latency);

                try {
                    Properties info = connection.serverCommands().info();
                    if (info != null) {
                        builder.withDetail("used_memory", info.getProperty("used_memory_human", "unknown"));
                        builder.withDetail("max_memory", info.getProperty("max_memory_human", "unlimited"));
                        builder.withDetail("version", info.getProperty("redis_version", "unknown"));
                    }
                } catch (Exception e) {
                    builder.withDetail("server_info", "unavailable");
                }

                if (latency > 100) {
                    builder.withDetail("status", "degraded");
                }

                return builder.build();
            } finally {
                connection.close();
            }
        } catch (Exception e) {
            log.error("【Redis】健康检查失败 | error={}", e.getMessage());
            return Health.down()
                    .withDetail("error", e.getClass().getSimpleName())
                    .withDetail("reason", e.getMessage())
                    .build();
        }
    }
}
