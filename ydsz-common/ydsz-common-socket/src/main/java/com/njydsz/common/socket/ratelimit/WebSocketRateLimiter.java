package com.njydsz.common.socket.ratelimit;

import java.time.Duration;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import com.njydsz.common.socket.config.WebSocketProperties;
import com.njydsz.common.socket.resilience.WebSocketCircuitBreaker;

/**
 * WebSocket 消息速率限制器（Redis-based）。
 *
 * <p>基于 Redis + Lua 脚本实现滑动窗口限流，保证 INCR + EXPIRE 原子性，
 * 避免服务宕机导致 key 无 TTL 泄漏。支持：
 * <ul>
 *   <li>per-user 限流：每用户每分钟最大消息数</li>
 *   <li>per-IP 限流：每 IP 每分钟最大消息数</li>
 * </ul>
 *
 * <p>限流 key 格式：
 * <ul>
 *   <li>{@code ydsz:ws:ratelimit:user:{userId}}</li>
 *   <li>{@code ydsz:ws:ratelimit:ip:{ip}}</li>
 * </ul>
 *
 * @author ydsz-team
 * @since 1.0.0
 */
@Slf4j
@RequiredArgsConstructor
public class WebSocketRateLimiter {

    private static final String RATE_LIMIT_USER_PREFIX = "ydsz:ws:ratelimit:user:";
    private static final String RATE_LIMIT_IP_PREFIX = "ydsz:ws:ratelimit:ip:";
    private static final Duration WINDOW = Duration.ofMinutes(1);

    /** Lua 脚本：原子执行 INCR + 首次创建时 EXPIRE */
    private static final String INCR_EXPIRE_SCRIPT =
            "local current = redis.call('INCR', KEYS[1]) " +
            "if current == 1 then " +
            "    redis.call('EXPIRE', KEYS[1], ARGV[1]) " +
            "end " +
            "return current";

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
     * Redis + Lua 脚本滑动窗口限流。
     *
     * <p>Lua 脚本保证 INCR + EXPIRE 原子执行，避免宕机导致 key 无 TTL 泄漏。
     *
     * @param key   限流 key
     * @param limit 最大请求数
     * @return true 表示允许，false 表示被限流
     */
    private boolean checkRate(String key, int limit) {
        if (redisTemplate == null) {
            return true;
        }
        DefaultRedisScript<Long> script = new DefaultRedisScript<>(INCR_EXPIRE_SCRIPT, Long.class);
        Long count = redisTemplate.execute(script, java.util.Collections.singletonList(key), String.valueOf(WINDOW.getSeconds()));
        return count == null || count <= limit;
    }
}
