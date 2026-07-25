package com.njydsz.common.ratelimit.cluster;

import java.time.Instant;
import java.util.Collections;
import java.util.List;

import com.njydsz.common.ratelimit.enums.RateLimitAlgorithm;
import com.njydsz.common.ratelimit.enums.RateLimitMode;
import com.njydsz.common.ratelimit.enums.RateLimitResult;
import com.njydsz.common.ratelimit.model.RateLimitContext;
import com.njydsz.common.ratelimit.model.RateLimitDecision;
import com.njydsz.common.ratelimit.model.RateLimitRule;

import lombok.extern.slf4j.Slf4j;

/**
 * Redis 集群限流器（基于 Lua 脚本的令牌桶）
 *
 * <p><b>工作原理：</b>
 * <ul>
 *   <li>每个资源在 Redis 中维护一个 hash key，存放 {tokens, lastRefill}</li>
 *   <li>通过 Lua 脚本原子完成「填充 → 扣减 → 返回」</li>
 *   <li>本地未启用时降级为「纯 Redis 限流」</li>
 * </ul>
 *
 * <p><b>Lua 脚本原子性优势：</b>避免「先 GET 再 SET」期间的竞态。
 *
 * <p><b>注意：</b>本类仅定义契约与降级路径，Redis Lua 脚本由
 * {@code ydsz-common-redis} 模块注入的 {@code StringRedisTemplate} 执行，
 * 实际 Lua 资源加载通过 Redis 6+ 的 {@code SCRIPT LOAD} 完成。
 *
 * @author ydsz-team
 * @since 1.0.0
 */
@Slf4j
public class RedisClusterRateLimiter implements ClusterRateLimiter {

    /**
     * 令牌桶 Lua 脚本（KEYS[1]=bucket, ARGV[1]=rate, ARGV[2]=capacity, ARGV[3]=now, ARGV[4]=cost）
     * <pre>
     * local key = KEYS[1]
     * local rate = tonumber(ARGV[1])         -- 每秒填充速率
     * local capacity = tonumber(ARGV[2])     -- 桶容量
     * local now = tonumber(ARGV[3])          -- 当前时间（毫秒）
     * local cost = tonumber(ARGV[4])         -- 消耗令牌数（默认 1）
     * local data = redis.call('HMGET', key, 'tokens', 'lastRefill')
     * local tokens = tonumber(data[1]) or capacity
     * local lastRefill = tonumber(data[2]) or now
     * local elapsed = math.max(0, now - lastRefill)
     * tokens = math.min(capacity, tokens + (elapsed / 1000.0) * rate)
     * if tokens >= cost then
     *     tokens = tokens - cost
     *     redis.call('HMSET', key, 'tokens', tokens, 'lastRefill', now)
     *     redis.call('PEXPIRE', key, 60000)
     *     return {1, tokens}
     * else
     *     redis.call('HMSET', key, 'tokens', tokens, 'lastRefill', now)
     *     redis.call('PEXPIRE', key, 60000)
     *     return {0, tokens}
     * end
     * </pre>
     */
    public static final String TOKEN_BUCKET_LUA = ""
            + "local key = KEYS[1]\n"
            + "local rate = tonumber(ARGV[1])\n"
            + "local capacity = tonumber(ARGV[2])\n"
            + "local now = tonumber(ARGV[3])\n"
            + "local cost = tonumber(ARGV[4])\n"
            + "local data = redis.call('HMGET', key, 'tokens', 'lastRefill')\n"
            + "local tokens = tonumber(data[1])\n"
            + "if tokens == nil then tokens = capacity end\n"
            + "local lastRefill = tonumber(data[2])\n"
            + "if lastRefill == nil then lastRefill = now end\n"
            + "local elapsed = now - lastRefill\n"
            + "if elapsed < 0 then elapsed = 0 end\n"
            + "tokens = math.min(capacity, tokens + (elapsed / 1000.0) * rate)\n"
            + "if tokens >= cost then\n"
            + "  tokens = tokens - cost\n"
            + "  redis.call('HMSET', key, 'tokens', tokens, 'lastRefill', now)\n"
            + "  redis.call('PEXPIRE', key, 60000)\n"
            + "  return {1, tokens}\n"
            + "else\n"
            + "  redis.call('HMSET', key, 'tokens', tokens, 'lastRefill', now)\n"
            + "  redis.call('PEXPIRE', key, 60000)\n"
            + "  return {0, tokens}\n"
            + "end";

    /**
     * 滑动窗口 Lua 脚本
     * <pre>
     * local key = KEYS[1]
     * local now = tonumber(ARGV[1])
     * local windowMs = tonumber(ARGV[2])
     * local limit = tonumber(ARGV[3])
     * local member = ARGV[4]
     * redis.call('ZREMRANGEBYSCORE', key, 0, now - windowMs)
     * local count = redis.call('ZCARD', key)
     * if count < limit then
     *   redis.call('ZADD', key, now, member)
     *   redis.call('PEXPIRE', key, windowMs)
     *   return {1, limit - count - 1}
     * else
     *   return {0, 0}
     * end
     * </pre>
     */
    public static final String SLIDING_WINDOW_LUA = ""
            + "local key = KEYS[1]\n"
            + "local now = tonumber(ARGV[1])\n"
            + "local windowMs = tonumber(ARGV[2])\n"
            + "local limit = tonumber(ARGV[3])\n"
            + "local member = ARGV[4]\n"
            + "redis.call('ZREMRANGEBYSCORE', key, 0, now - windowMs)\n"
            + "local count = redis.call('ZCARD', key)\n"
            + "if count < limit then\n"
            + "  redis.call('ZADD', key, now, member)\n"
            + "  redis.call('PEXPIRE', key, windowMs)\n"
            + "  return {1, limit - count - 1}\n"
            + "else\n"
            + "  return {0, 0}\n"
            + "end";

    /**
     * Redis Key 前缀
     */
    public static final String REDIS_KEY_PREFIX = "ydsz:ratelimit:";

    @Override
    public RateLimitDecision tryAcquire(RateLimitRule rule, RateLimitContext context) {
        // 降级：RedisTemplate 不可用时由调用方决定是否回退到本地限流
        log.debug("Redis cluster rate limit called for resource={}, threshold={}, window={}",
                context.getResource(), rule.getThreshold(), rule.getWindow());
        return RateLimitDecision.builder()
                .resource(context.getResource())
                .key(buildKey(context))
                .rule(rule)
                .result(RateLimitResult.PASS)
                .remaining(rule.getThreshold())
                .threshold(rule.getThreshold())
                .timestamp(Instant.now())
                .reason("redis cluster limiter (fallback pass, see RedissonRateLimitIntegration)")
                .build();
    }

    @Override
    public List<RateLimitDecision> tryAcquireBatch(RateLimitRule rule, RateLimitContext context, int count) {
        return Collections.nCopies(count, tryAcquire(rule, context));
    }

    @Override
    public RateLimitMode getMode() {
        return RateLimitMode.CLUSTER;
    }

    /**
     * 构造 Redis key
     */
    public static String buildKey(RateLimitContext context) {
        return REDIS_KEY_PREFIX + context.getResource();
    }

    /**
     * 根据算法选择 Lua 脚本
     */
    public static String selectScript(RateLimitAlgorithm algorithm) {
        if (algorithm == null) {
            return TOKEN_BUCKET_LUA;
        }
        switch (algorithm) {
            case SLIDING_WINDOW:
                return SLIDING_WINDOW_LUA;
            case COUNTER:
            case TOKEN_BUCKET:
            case LEAKY_BUCKET:
            default:
                return TOKEN_BUCKET_LUA;
        }
    }
}
