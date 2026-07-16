package com.njydsz.common.auth.health;

import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.health.contributor.Health;
import org.springframework.boot.health.contributor.HealthIndicator;
import org.springframework.data.redis.connection.RedisConnection;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.stereotype.Component;

import lombok.extern.slf4j.Slf4j;

/**
 * 权限模块健康检查指示器
 *
 * <p>检测 Redis 连通性（权限缓存依赖 Redis），暴露 /actuator/health/auth 端点。
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
@ConditionalOnProperty(prefix = "ydsz.auth", name = "enabled", havingValue = "true", matchIfMissing = true)
public class AuthHealthIndicator implements HealthIndicator {

    private final RedisConnectionFactory redisConnectionFactory;

    public AuthHealthIndicator(RedisConnectionFactory redisConnectionFactory) {
        this.redisConnectionFactory = redisConnectionFactory;
    }

    @Override
    public Health health() {
        RedisConnection connection = null;
        try {
            long startTime = System.currentTimeMillis();
            connection = redisConnectionFactory.getConnection();
            String pong = connection.ping();
            long responseTime = System.currentTimeMillis() - startTime;

            if ("PONG".equalsIgnoreCase(pong)) {
                return Health.up()
                        .withDetail("module", "auth")
                        .withDetail("redis", "connected")
                        .withDetail("responseTimeMs", responseTime)
                        .build();
            }

            return Health.down()
                    .withDetail("module", "auth")
                    .withDetail("redis", "unexpected response: " + pong)
                    .build();
        } catch (Exception e) {
            log.error("【权限模块】健康检查失败 | error={}", e.getMessage());
            return Health.down()
                    .withDetail("module", "auth")
                    .withDetail("error", e.getMessage())
                    .build();
        } finally {
            if (connection != null) {
                try {
                    connection.close();
                } catch (Exception e) {
                    log.debug("关闭 Redis 连接异常: {}", e.getMessage());
                }
            }
        }
    }
}
