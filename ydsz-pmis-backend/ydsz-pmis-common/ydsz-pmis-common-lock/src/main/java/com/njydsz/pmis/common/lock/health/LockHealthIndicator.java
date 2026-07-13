package com.njydsz.pmis.common.lock.health;

import jakarta.annotation.Resource;

import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.health.contributor.Health;
import org.springframework.boot.health.contributor.HealthIndicator;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnection;
import org.springframework.data.redis.connection.RedisConnectionFactory;

import lombok.extern.slf4j.Slf4j;

/**
 * 分布式锁健康检查
 *
 * <p>检测 Redis 连接状态，暴露 /actuator/health/lock 端点。
 * 分布式锁基于 Redis 实现，因此健康检查直接验证 Redis 连接可用性。
 *
 * <p><b>检测逻辑：</b>
 * <ul>
 *   <li>验证 RedisConnectionFactory 连接状态</li>
 *   <li>执行 PING 命令验证连接可达性</li>
 *   <li>返回连接耗时作为性能指标</li>
 * </ul>
 *
 * @author Marvin Lee
 * @email limw1888@126.com
 * @version 3.5.0
 * @since 1.0.0
 */
@Slf4j
@Configuration
@ConditionalOnClass(RedisConnectionFactory.class)
@ConditionalOnProperty(prefix = "ydsz.lock", name = "enabled", havingValue = "true", matchIfMissing = false)
public class LockHealthIndicator implements HealthIndicator {

    @Resource
    private RedisConnectionFactory redisConnectionFactory;

    @Override
    public Health health() {
        try {
            long startTime = System.currentTimeMillis();

            RedisConnection connection = null;
            try {
                connection = redisConnectionFactory.getConnection();
                String pong = connection.ping();
                long responseTime = System.currentTimeMillis() - startTime;

                if ("PONG".equals(pong)) {
                    return Health.up()
                            .withDetail("lockType", "redis")
                            .withDetail("responseTimeMs", responseTime)
                            .build();
                }

                return Health.down()
                        .withDetail("lockType", "redis")
                        .withDetail("reason", "unexpected response: " + pong)
                        .build();
            } finally {
                if (connection != null) {
                    try {
                        connection.close();
                    } catch (Exception e) {
                        log.debug("关闭 Redis 连接异常", e);
                    }
                }
            }
        } catch (Exception e) {
            log.error("分布式锁健康检查失败", e);
            return Health.down()
                    .withDetail("lockType", "redis")
                    .withDetail("error", e.getMessage())
                    .build();
        }
    }
}
