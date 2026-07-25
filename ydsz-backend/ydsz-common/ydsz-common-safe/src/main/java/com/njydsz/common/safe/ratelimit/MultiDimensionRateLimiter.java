package com.njydsz.common.safe.ratelimit;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;

/**
 * 多维限流器
 *
 * <p>支持按多维度组合进行限流，如 IP+USER+API 三维组合限流。
 * 使用 Redis Lua 脚本保证原子性，支持滑动窗口算法。
 *
 * <p>限流维度组合示例：
 * <ul>
 *   <li>{@code IP} - 仅按 IP 限流</li>
 *   <li>{@code IP+API} - 按 IP 和 API 路径组合限流</li>
 *   <li>{@code USER+API} - 按用户和 API 路径组合限流</li>
 *   <li>{@code IP+USER+API} - 三维组合限流</li>
 * </ul>
 *
 * <p>每个维度组合可以配置独立的限流规则（QPS、窗口、突发容量）。
 *
 * @since 1.0.0
 */
public class MultiDimensionRateLimiter {

    private static final Logger log = LoggerFactory.getLogger(MultiDimensionRateLimiter.class);

    private static final String LUA_SCRIPT = """
            local key = KEYS[1]
            local limit = tonumber(ARGV[1])
            local window = tonumber(ARGV[2])
            local burst = tonumber(ARGV[3])
            local now = tonumber(ARGV[4])
            local cleared = now - window * 1000

            redis.call('ZREMRANGEBYSCORE', key, 0, cleared)
            local count = redis.call('ZCARD', key)
            if count >= burst then
                return 0
            end
            redis.call('ZADD', key, now, now .. ':' .. math.random())
            redis.call('EXPIRE', key, window + 1)
            return 1
            """;

    private static final DefaultRedisScript<Long> RATE_LIMIT_SCRIPT;

    static {
        RATE_LIMIT_SCRIPT = new DefaultRedisScript<>(LUA_SCRIPT, Long.class);
    }

    private final StringRedisTemplate redisTemplate;

    /**
     * @param redisTemplate Redis 模板
     */
    public MultiDimensionRateLimiter(StringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    /**
     * 执行多维限流检查
     *
     * @param rules   限流规则列表（多维度组合，AND 关系：所有规则都通过才放行）
     * @param context 限流上下文（包含 IP、userId、apiPath 等）
     * @return true 放行，false 限流
     */
    public boolean checkRateLimit(List<RateLimitRule> rules, RateLimitContext context) {
        for (RateLimitRule rule : rules) {
            String key = buildKey(rule, context);
            long now = System.currentTimeMillis();
            boolean allowed = tryAcquire(key, rule.limit(), rule.windowSeconds(), rule.burstCapacity(), now);

            if (!allowed) {
                log.warn("Rate limited: key={}, dimension={}", key, rule.dimension());
                return false;
            }
        }
        return true;
    }

    private boolean tryAcquire(String key, int limit, int windowSeconds, int burst, long nowMillis) {
        try {
            Long result = redisTemplate.execute(
                    RATE_LIMIT_SCRIPT,
                    Collections.singletonList(key),
                    String.valueOf(limit),
                    String.valueOf(windowSeconds),
                    String.valueOf(burst),
                    String.valueOf(nowMillis)
            );
            return result != null && result == 1L;
        } catch (Exception e) {
            log.error("Redis rate limit check failed: key={}", key, e);
            return true;
        }
    }

    private String buildKey(RateLimitRule rule, RateLimitContext context) {
        List<String> parts = new ArrayList<>();
        parts.add("ratelimit");

        String[] dimensions = rule.dimension().split("\\+");
        for (String dim : dimensions) {
            switch (dim.trim()) {
                case "IP" -> {
                    parts.add("ip");
                    parts.add(context.ip());
                }
                case "USER" -> {
                    parts.add("user");
                    parts.add(context.userId() != null ? context.userId() : "anonymous");
                }
                case "API" -> {
                    parts.add("api");
                    parts.add(context.apiPath());
                }
                case "GLOBAL" -> parts.add("global");
                default -> log.warn("Unknown rate limit dimension: {}", dim);
            }
        }
        return String.join(":", parts);
    }

    /**
     * 限流规则
     *
     * @param dimension       维度组合（如 "IP+API"）
     * @param limit            窗口内允许的请求数
     * @param windowSeconds    窗口大小（秒）
     * @param burstCapacity    突发容量
     */
    public record RateLimitRule(String dimension, int limit, int windowSeconds, int burstCapacity) {}

    /**
     * 限流上下文
     *
     * @param ip       客户端 IP
     * @param userId   用户 ID（可为 null）
     * @param apiPath  API 路径
     */
    public record RateLimitContext(String ip, String userId, String apiPath) {}
}
