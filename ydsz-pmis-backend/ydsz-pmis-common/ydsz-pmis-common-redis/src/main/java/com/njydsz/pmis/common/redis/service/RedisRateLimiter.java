package com.njydsz.pmis.common.redis.service;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Component;

import com.njydsz.pmis.common.redis.config.RedisProperties;
import com.njydsz.pmis.common.redis.enums.FailOpenPolicy;

import lombok.extern.slf4j.Slf4j;

/**
 * 分布式限流器（基于 Redis + Lua）
 *
 * <p>提供三种工业级限流算法，全部基于 Redis Lua 脚本保证原子性，避免竞态条件：
 * <ul>
 *   <li><b>固定窗口（Fixed Window）</b> - 最简单，按时间窗口计数，窗口切换时存在 2x 突发问题</li>
 *   <li><b>滑动窗口（Sliding Window）</b> - 借助 ZSET，时间精度高，限流更平滑</li>
 *   <li><b>令牌桶（Token Bucket）</b> - 支持突发流量，按速率持续补充令牌</li>
 * </ul>
 *
 * <p><b>算法对比：</b>
 * <table>
 *   <tr><th>算法</th><th>精度</th><th>突发容忍</th><th>实现复杂度</th><th>适用场景</th></tr>
 *   <tr><td>固定窗口</td><td>低</td><td>2x 突发</td><td>低</td><td>粗粒度限流</td></tr>
 *   <tr><td>滑动窗口</td><td>高</td><td>无</td><td>中</td><td>严格限流</td></tr>
 *   <tr><td>令牌桶</td><td>高</td><td>可配置</td><td>中</td><td>流量整形</td></tr>
 * </table>
 *
 * <p><b>使用示例：</b>
 * <pre>{@code
 * // 滑动窗口：每分钟最多 100 次
 * boolean allowed = rateLimiter.tryAcquireSlidingWindow(
 *     "api:user:10086", 100, Duration.ofMinutes(1));
 *
 * // 令牌桶：每秒 10 个令牌，桶容量 50
 * boolean allowed = rateLimiter.tryAcquireTokenBucket(
 *     "api:order", 10, 50, Duration.ofSeconds(1));
 *
 * // 固定窗口：每秒 5 次
 * boolean allowed = rateLimiter.tryAcquireFixedWindow(
 *     "sms:send", 5, Duration.ofSeconds(1));
 * }</pre>
 *
 * <p><b>线程安全：</b>所有方法均为线程安全，底层基于 Redis 单线程 + Lua 原子性保证。</p>
 *
 * @author ydsz-pmis-team
 * @since 1.0.0
 * @since 1.0.0
 */
@Slf4j
@Component
public class RedisRateLimiter {

    /**
     * 固定窗口限流 Lua 脚本
     *
     * <p>逻辑：INCR key，若值为 1 则设置过期时间；返回当前值。
     * 原子性保证：Redis 单线程执行 Lua 脚本，INCR + EXPIRE 不会分裂。
     */
    private static final String FIXED_WINDOW_LUA =
            "local current = redis.call('INCR', KEYS[1]) " +
            "if current == 1 then " +
            "  redis.call('EXPIRE', KEYS[1], ARGV[1]) " +
            "end " +
            "return current";

    /**
     * 滑动窗口限流 Lua 脚本
     *
     * <p>逻辑：
     * <ol>
     *   <li>移除 ZSET 中早于窗口起始时间的元素</li>
     *   <li>获取 ZSET 当前元素数</li>
     *   <li>若小于限流阈值，则添加当前请求到 ZSET</li>
     *   <li>设置 ZSET 过期时间（窗口长度 + 1 秒）</li>
     *   <li>返回是否允许（1=允许，0=拒绝）以及当前计数</li>
     * </ol>
     */
    private static final String SLIDING_WINDOW_LUA =
            "local key = KEYS[1] " +
            "local now = tonumber(ARGV[1]) " +
            "local windowMs = tonumber(ARGV[2]) " +
            "local limit = tonumber(ARGV[3]) " +
            "local member = ARGV[4] " +
            "local windowStart = now - windowMs " +
            "redis.call('ZREMRANGEBYSCORE', key, '-inf', windowStart) " +
            "local current = redis.call('ZCARD', key) " +
            "if current < limit then " +
            "  redis.call('ZADD', key, now, member) " +
            "  redis.call('PEXPIRE', key, windowMs + 1000) " +
            "  return {1, current + 1} " +
            "else " +
            "  return {0, current} " +
            "end";

    /**
     * 令牌桶限流 Lua 脚本
     *
     * <p>逻辑：
     * <ol>
     *   <li>读取桶中当前令牌数与上次刷新时间</li>
     *   <li>计算自上次刷新以来应补充的令牌数（rate * elapsed / period）</li>
     *   <li>更新令牌数（不超过 capacity）</li>
     *   <li>若令牌数 >= 1，扣减一个令牌并返回 1；否则返回 0</li>
     * </ol>
     * 使用 Hash 存储令牌桶状态：tokens, lastRefillMs
     */
    private static final String TOKEN_BUCKET_LUA =
            "local key = KEYS[1] " +
            "local capacity = tonumber(ARGV[1]) " +
            "local rate = tonumber(ARGV[2]) " +
            "local periodMs = tonumber(ARGV[3]) " +
            "local now = tonumber(ARGV[4]) " +
            "local requested = tonumber(ARGV[5]) " +
            "local data = redis.call('HMGET', key, 'tokens', 'lastRefillMs') " +
            "local tokens = tonumber(data[1]) " +
            "local lastRefill = tonumber(data[2]) " +
            "if tokens == nil then " +
            "  tokens = capacity " +
            "  lastRefill = now " +
            "end " +
            "local elapsed = now - lastRefill " +
            "if elapsed > 0 then " +
            "  local refill = math.floor(elapsed * rate / periodMs) " +
            "  if refill > 0 then " +
            "    tokens = math.min(capacity, tokens + refill) " +
            "    lastRefill = now " +
            "  end " +
            "end " +
            "local allowed = 0 " +
            "if tokens >= requested then " +
            "  tokens = tokens - requested " +
            "  allowed = 1 " +
            "end " +
            "redis.call('HMSET', key, 'tokens', tokens, 'lastRefillMs', lastRefill) " +
            "redis.call('PEXPIRE', key, math.ceil(periodMs * 2 / 1000) + 1) " +
            "return {allowed, tokens}";

    private final RedisTemplate<String, Object> redisTemplate;
    private final RedisProperties redisProperties;
    private final FailOpenPolicy failOpenPolicy;

    /** 编译后的 Lua 脚本缓存 */
    private final ConcurrentHashMap<String, DefaultRedisScript<?>> scriptCache = new ConcurrentHashMap<>();

    /**
     * 将 Redis 脚本执行结果安全转换为 List<Long>
     *
     * @param rawResult Redis 执行返回的原始对象
     * @return 转换后的 Long 列表，无法转换时返回空列表
     */
    private static List<Long> castToLongList(Object rawResult) {
        if (rawResult instanceof List<?> list) {
            List<Long> result = new ArrayList<>(list.size());
            for (Object item : list) {
                if (item instanceof Number num) {
                    result.add(num.longValue());
                } else {
                    result.add(0L);
                }
            }
            return result;
        }
        return Collections.emptyList();
    }

    public RedisRateLimiter(RedisTemplate<String, Object> redisTemplate,
                            RedisProperties redisProperties) {
        this(redisTemplate, redisProperties, redisProperties.getRateLimiter() != null 
                ? redisProperties.getRateLimiter().getFailOpenPolicy() 
                : FailOpenPolicy.FAIL_CLOSED);
    }

    public RedisRateLimiter(RedisTemplate<String, Object> redisTemplate,
                            RedisProperties redisProperties,
                            FailOpenPolicy failOpenPolicy) {
        this.redisTemplate = redisTemplate;
        this.redisProperties = redisProperties;
        this.failOpenPolicy = failOpenPolicy != null ? failOpenPolicy : FailOpenPolicy.FAIL_CLOSED;
    }

    /**
     * 固定窗口限流
     *
     * <p>在指定时间窗口内最多允许 limit 次请求。窗口切换时可能存在 2x 突发流量。
     * 适用场景：粗粒度限流、对精度要求不高的场景。
     *
     * @param key    限流维度键（如 "api:user:10086"）
     * @param limit  窗口内最大请求数
     * @param window 时间窗口长度
     * @return true=允许，false=拒绝
     */
    public boolean tryAcquireFixedWindow(String key, int limit, Duration window) {
        if (key == null || limit <= 0 || window == null || window.isZero() || window.isNegative()) {
            return false;
        }
        try {
            String formattedKey = formatKey(key);
            long windowSeconds = Math.max(1, window.toSeconds());
            DefaultRedisScript<?> script = getOrCreateScript(
                    "fixed_window", FIXED_WINDOW_LUA, Long.class);
            Object rawResult = redisTemplate.execute(script,
                    Collections.singletonList(formattedKey),
                    String.valueOf(windowSeconds));
            long count = rawResult instanceof Number ? ((Number) rawResult).longValue() : 0L;
            return count <= limit;
        } catch (Exception e) {
            log.error("【RedisRateLimiter】固定窗口限流异常 | key={} | error={}", key, e);
            return handleException("固定窗口限流", key, e);
        }
    }

    /**
     * 滑动窗口限流
     *
     * <p>在任意时刻起算的 window 时间范围内，最多允许 limit 次请求。
     * 使用 ZSET 记录请求时间戳，移除早于窗口的过期记录后判断当前数量。
     * 适用场景：严格限流、避免突发流量。
     *
     * @param key    限流维度键
     * @param limit  窗口内最大请求数
     * @param window 时间窗口长度
     * @return true=允许，false=拒绝
     */
    public boolean tryAcquireSlidingWindow(String key, int limit, Duration window) {
        if (key == null || limit <= 0 || window == null || window.isZero() || window.isNegative()) {
            return false;
        }
        try {
            String formattedKey = formatKey(key);
            long now = System.currentTimeMillis();
            long windowMs = window.toMillis();
            String member = now + ":" + Thread.currentThread().threadId() + ":" + Math.random();
            DefaultRedisScript<?> script = getOrCreateScript(
                    "sliding_window", SLIDING_WINDOW_LUA, List.class);
            Object rawResult = redisTemplate.execute(script,
                    Collections.singletonList(formattedKey),
                    String.valueOf(now),
                    String.valueOf(windowMs),
                    String.valueOf(limit),
                    member);
            List<Long> result = castToLongList(rawResult);
            if (result.isEmpty()) {
                return false;
            }
            return result.get(0) != null && result.get(0) == 1L;
        } catch (Exception e) {
            log.error("【RedisRateLimiter】滑动窗口限流异常 | key={} | error={}", key, e);
            return handleException("滑动窗口限流", key, e);
        }
    }

    /**
     * 令牌桶限流
     *
     * <p>桶容量为 capacity，以每秒 rate 个令牌的速率持续补充令牌。
     * 当桶中有令牌时允许请求，否则拒绝。允许突发流量（最多 burst 到 capacity）。
     * 适用场景：流量整形、API 限流。
     *
     * @param key      限流维度键
     * @param rate     令牌补充速率（每秒）
     * @param capacity 桶容量（最大令牌数）
     * @return true=允许，false=拒绝
     */
    public boolean tryAcquireTokenBucket(String key, int rate, int capacity) {
        return tryAcquireTokenBucket(key, rate, capacity, Duration.ofSeconds(1), 1);
    }

    /**
     * 令牌桶限流（自定义周期）
     *
     * @param key      限流维度键
     * @param rate     周期内补充的令牌数
     * @param capacity 桶容量
     * @param period   补充周期
     * @return true=允许，false=拒绝
     */
    public boolean tryAcquireTokenBucket(String key, int rate, int capacity, Duration period) {
        return tryAcquireTokenBucket(key, rate, capacity, period, 1);
    }

    /**
     * 令牌桶限流（自定义周期和请求令牌数）
     *
     * @param key      限流维度键
     * @param rate     周期内补充的令牌数
     * @param capacity 桶容量
     * @param period   补充周期
     * @param permits  本次请求消耗的令牌数
     * @return true=允许，false=拒绝
     */
    public boolean tryAcquireTokenBucket(String key, int rate, int capacity,
                                         Duration period, int permits) {
        if (key == null || rate <= 0 || capacity <= 0
                || period == null || period.isZero() || period.isNegative()
                || permits <= 0) {
            return false;
        }
        try {
            String formattedKey = formatKey(key);
            long now = System.currentTimeMillis();
            long periodMs = period.toMillis();
            DefaultRedisScript<?> script = getOrCreateScript(
                    "token_bucket", TOKEN_BUCKET_LUA, List.class);
            Object rawResult = redisTemplate.execute(script,
                    Collections.singletonList(formattedKey),
                    String.valueOf(capacity),
                    String.valueOf(rate),
                    String.valueOf(periodMs),
                    String.valueOf(now),
                    String.valueOf(permits));
            List<Long> result = castToLongList(rawResult);
            if (result.isEmpty()) {
                return false;
            }
            return result.get(0) != null && result.get(0) == 1L;
        } catch (Exception e) {
            log.error("【RedisRateLimiter】令牌桶限流异常 | key={} | error={}", key, e);
            return handleException("令牌桶限流", key, e);
        }
    }

    /**
     * 获取限流器当前状态（仅令牌桶）
     *
     * @param key 限流维度键
     * @return 当前可用令牌数；键不存在时返回 -1
     */
    public long getTokenBucketAvailable(String key) {
        if (key == null) {
            return -1;
        }
        try {
            String formattedKey = formatKey(key);
            Object tokens = redisTemplate.opsForHash().get(formattedKey, "tokens");
            return tokens != null ? Long.parseLong(tokens.toString()) : -1L;
        } catch (Exception e) {
            log.warn("【RedisRateLimiter】获取令牌桶状态失败 | key={} | error={}", key, e);
            return -1;
        }
    }

    /**
     * 重置限流器状态
     *
     * @param key 限流维度键
     */
    public void reset(String key) {
        if (key == null) {
            return;
        }
        try {
            redisTemplate.delete(formatKey(key));
        } catch (Exception e) {
            log.warn("【RedisRateLimiter】重置限流状态失败 | key={} | error={}", key, e);
        }
    }

    private String formatKey(String key) {
        String prefix = redisProperties != null ? redisProperties.getKeyPrefix() : null;
        if (prefix == null || prefix.isEmpty()) {
            return "ratelimit:" + key;
        }
        return prefix + ":ratelimit:" + key;
    }

    private DefaultRedisScript<?> getOrCreateScript(String name, String scriptText, Class<?> returnType) {
        return scriptCache.computeIfAbsent(name, k -> {
            DefaultRedisScript<?> script = new DefaultRedisScript<>();
            script.setScriptText(scriptText);
            ((DefaultRedisScript) script).setResultType(returnType);
            return script;
        });
    }

    /**
     * 处理限流异常
     *
     * <p>根据配置的故障转移策略决定在 Redis 异常时的行为：
     * <ul>
     *   <li>FAIL_OPEN: 放行请求（返回 true），保证可用性</li>
     *   <li>FAIL_CLOSED: 拒绝请求（返回 false），保证安全性</li>
     *   <li>FAIL_THROW: 抛出异常，由业务层处理</li>
     * </ul>
     *
     * @param operation 操作名称（用于日志）
     * @param key       限流键
     * @param e         异常
     * @return true=放行，false=拒绝（仅 FAIL_OPEN/FAIL_CLOSED 策略）
     * @throws RuntimeException 当策略为 FAIL_THROW 时
     */
    private boolean handleException(String operation, String key, Exception e) {
        return switch (failOpenPolicy) {
            case FAIL_OPEN -> {
                log.warn("【RedisRateLimiter】{} 异常，策略=FAIL_OPEN，放行请求 | key={}", operation, key);
                yield true;
            }
            case FAIL_CLOSED -> {
                log.warn("【RedisRateLimiter】{} 异常，策略=FAIL_CLOSED，拒绝请求 | key={}", operation, key);
                yield false;
            }
            case FAIL_THROW -> {
                log.error("【RedisRateLimiter】{} 异常，策略=FAIL_THROW，抛出异常 | key={}", operation, key);
                throw new RuntimeException("限流器异常: " + operation, e);
            }
        };
    }
}
