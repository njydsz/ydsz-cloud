package com.njydsz.pmis.common.health;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.data.redis.connection.RedisConnectionCommands;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

/**
 * Redis 健康检查指示器（P1-5）
 *
 * @author ydsz-pmis-team
 * @since 1.1.0
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class RedisHealthIndicator implements HealthIndicator {

    private final StringRedisTemplate redisTemplate;

    @Override
    public Health health() {
        try {
            String result = redisTemplate.execute(RedisConnectionCommands::ping);
            if ("PONG".equals(result)) {
                return Health.up().withDetail("version", "connected").build();
            }
            return Health.down().withDetail("error", "unexpected PING response: " + result).build();
        } catch (Exception e) {
            log.warn("[HealthCheck] Redis 健康检查失败: {}", e.getMessage());
            return Health.down(e).build();
        }
    }
}