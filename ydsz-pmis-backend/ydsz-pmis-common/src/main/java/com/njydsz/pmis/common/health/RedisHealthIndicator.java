package com.njydsz.pmis.common.health;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.health.contributor.Health;
import org.springframework.boot.health.contributor.HealthIndicator;
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

    /** Redis 操作模板，用于执行 PING 命令检查连通性 */
    private final StringRedisTemplate redisTemplate;

    /**
     * 执行 Redis 健康检查：通过 PING 命令验证连接可用性
     *
     * <p>检查成功时返回 UP 状态；PONG 响应异常或连接失败时返回 DOWN 状态。
     *
     * @return 健康状态对象
     */
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