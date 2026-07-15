package com.njydsz.pmis.common.safe.health;

import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.health.contributor.Health;
import org.springframework.boot.health.contributor.HealthIndicator;
import org.springframework.data.redis.connection.RedisConnection;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.stereotype.Component;

import lombok.extern.slf4j.Slf4j;

/**
 * 安全模块健康检查指示器
 *
 * <p>检测 Redis 连通性（限流依赖 Redis），暴露 /actuator/health/safe 端点。
 *
 * <p><b>检测逻辑：</b>
 * <ul>
 *   <li>验证 RedisConnectionFactory 连接状态</li>
 *   <li>执行 PING 命令验证连接可达性</li>
 *   <li>返回连接耗时作为性能指标</li>
 * </ul>
 *
 * @since 1.0.0
 * 
 */
@Slf4j
@Component
@ConditionalOnClass(HealthIndicator.class)
@ConditionalOnProperty(prefix = "ydsz.safe", name = "enabled", havingValue = "true", matchIfMissing = true)
public class SafeHealthIndicator implements HealthIndicator {

    private final RedisConnectionFactory redisConnectionFactory;

    public SafeHealthIndicator(RedisConnectionFactory redisConnectionFactory) {
        this.redisConnectionFactory = redisConnectionFactory;
    }

    @Override
    public Health health() {
        try {
            long startTime = System.currentTimeMillis();
            RedisConnection connection = redisConnectionFactory.getConnection();
            try {
                String pong = connection.ping();
                long responseTime = System.currentTimeMillis() - startTime;

                if ("PONG".equalsIgnoreCase(pong)) {
                    return Health.up()
                            .withDetail("module", "safe")
                            .withDetail("redis", "connected")
                            .withDetail("responseTimeMs", responseTime)
                            .build();
                }

                return Health.down()
                        .withDetail("module", "safe")
                        .withDetail("redis", "unexpected response: " + pong)
                        .build();
            } finally {
                connection.close();
            }
        } catch (Exception e) {
            log.error("【安全模块】健康检查失败 | error={}", e.getMessage());
            return Health.down()
                    .withDetail("module", "safe")
                    .withDetail("error", e.getMessage())
                    .build();
        }
    }
}
