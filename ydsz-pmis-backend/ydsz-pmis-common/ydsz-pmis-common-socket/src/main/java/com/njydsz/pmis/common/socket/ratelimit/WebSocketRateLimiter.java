package com.njydsz.pmis.common.socket.ratelimit;

import java.time.Duration;

import org.springframework.data.redis.core.StringRedisTemplate;

import com.njydsz.pmis.common.socket.config.WebSocketProperties;
import com.njydsz.pmis.common.socket.resilience.WebSocketCircuitBreaker;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * WebSocket 消息速率限制器（Redis-based）。
 *
 * <p>基于 Redis INCR + EXPIRE 实现滑动窗口限流，支持：
 * <ul>
 *   <li>per-user 限流：每用户每分钟最大消息数</li>
 *   <li>per-IP 限流：每 IP 每分钟最大消息数</li>
 * </ul>
 *
 * <p>限流 key 格式：
 * <ul>
 *   <li>{@code pmis:ws:ratelimit:user:{userId}}</li>
 *   <li>{@code pmis:ws:ratelimit:ip:{ip}}</li>
 * </ul>
 *
 * @author ydsz-pmis-team
 * @since 1.0.0
 */
@Slf4j
@RequiredArgsConstructor
public class WebSocketRateLimiter {

    private static final String RATE_LIMIT_USER_PREFIX = "pmis:ws:ratelimit:user:";
    private static final String RATE_LIMIT_IP_PREFIX = "pmis:ws:ratelimit:ip:";
    private static final Duration WINDOW = Duration.ofMinutes(1);

    private final StringRedisTemplate redisTemplate;
    private final WebSocketProperties properties;
    private final WebSocketCircuitBreaker circuitBreaker;

    /**
     * 检查用户是否被限流。
     *
     * @param userId 用户 ID
     * @return true 表示允许（未超限），false 表示被限流
     */
    public boolean checkUser(String userId) {
        if (!properties.getRateLimit().isEnabled() || userId == null) {
            return true;
        }
        return circuitBreaker.execute(
                () -> checkRate(RATE_LIMIT_USER_PREFIX + userId,
                        properties.getRateLimit().getMaxPerUserPerMinute()),
                () -> true
        );
    }

    /**
     * 检查 IP 是否被限流。
     *
     * @param ip 客户端 IP
     * @return true 表示允许（未超限），false 表示被限流
     */
    public boolean checkIp(String ip) {
        if (!properties.getRateLimit().isEnabled() || ip == null) {
            return true;
        }
        return circuitBreaker.execute(
                () -> checkRate(RATE_LIMIT_IP_PREFIX + ip,
                        properties.getRateLimit().getMaxPerIpPerMinute()),
                () -> true
        );
    }

    /**
     * Redis INCR + EXPIRE 滑动窗口限流。
     *
     * @param key   限流 key
     * @param limit 最大请求数
     * @return true 表示允许，false 表示被限流
     */
    private boolean checkRate(String key, int limit) {
        if (redisTemplate == null) return true;
        Long count = redisTemplate.opsForValue().increment(key);
        if (count != null && count == 1) {
            redisTemplate.expire(key, WINDOW);
        }
        return count == null || count <= limit;
    }
}
