package com.njydsz.common.redis.service;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;

import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;

import com.njydsz.common.redis.config.RedisProperties;
import com.njydsz.common.redis.enums.FailOpenPolicy;

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
 * @author ydsz-team
 * @since 1.0.0
 */
@Slf4j
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
     *
     * @deprecated 自 v1.1.0 起标记废弃，请使用 {@link #tryAcquireTokenBucket(String, int, int, Duration)} 显式指定周期参数。
     */
    @Deprecated(since = "1.1.0", forRemoval = true)
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
     * 滑动窗口限流（ZSET 精确版）
     *
     * <p>使用 Redis ZSET 存储请求时间戳，精确到毫秒级。
     * 与 {@link #tryAcquireSlidingWindowBucketed} 相比精度更高但内存占用更大。
     *
     * <p>此方法为兼容入口，实际委托到分桶实现（内存优化版），
     * 对绝大多数业务场景桶级精度已完全足够。
     *
     * @param key    限流维度键
     * @param limit  窗口内最大请求数
     * @param window 时间窗口长度
     * @return true=允许，false=拒绝
     */
    public boolean tryAcquireSlidingWindow(String key, int limit, Duration window) {
        return tryAcquireSlidingWindowBucketed(key, limit, window);
    }

    /**
     * 滑动窗口限流（分桶计数法，内存优化版）
     *
     * <p>使用 Redis Hash 存储时间桶计数，替代 ZSET 存储每个请求的唯一 member。
     * 内存占用恒定为 O(bucketCount)，默认 10 个桶，不受请求数量影响。
     *
     * <p><b>与 {@link #tryAcquireSlidingWindow} 的对比：</b>
     * <ul>
     *   <li>内存：O(bucketCount) vs O(requestCount)，高并发下节省 90%+ 内存</li>
     *   <li>精度：略有损失（桶级精度 vs 毫秒级精度），对绝大多数场景无影响</li>
     *   <li>兼容：使用不同的 Key 格式（bucketed:），与旧版 ZSET Key 互不干扰</li>
     * </ul>
     *
     * @param key    限流维度键
     * @param limit  窗口内最大请求数
     * @param window 时间窗口长度
     * @return true=允许，false=拒绝
     *
     * @deprecated 自 v1.1.0 起标记废弃。请使用 {@link #tryAcquireSlidingWindow(String, int, Duration)}。
     */
    @Deprecated(since = "1.1.0", forRemoval = true)
    public boolean tryAcquireSlidingWindowBucketed(String key, int limit, Duration window) {
        if (key == null || limit <= 0 || window == null || window.isZero() || window.isNegative()) {
            return false;
        }
        try {
            String formattedKey = formatBucketedKey(key);
            long now = System.currentTimeMillis();
            long windowMs = window.toMillis();
            int bucketCount = 10;
            DefaultRedisScript<?> script = getOrCreateScript(
                    "sliding_window_bucketed", SLIDING_WINDOW_BUCKETED_LUA, List.class);
            Object rawResult = redisTemplate.execute(script,
                    Collections.singletonList(formattedKey),
                    String.valueOf(now),
                    String.valueOf(windowMs),
                    String.valueOf(limit),
                    String.valueOf(bucketCount));
            List<Long> result = castToLongList(rawResult);
            if (result.isEmpty()) {
                return false;
            }
            return result.get(0) != null && result.get(0) == 1L;
        } catch (Exception e) {
            log.error("【RedisRateLimiter】分桶滑动窗口限流异常 | key={} | error={}", key, e);
            return handleException("分桶滑动窗口限流", key, e);
        }
    }

    private String formatBucketedKey(String key) {
        String prefix = redisProperties != null ? redisProperties.getKeyPrefix() : null;
        if (prefix == null || prefix.isEmpty()) {
            return "ratelimit:bucketed:" + key;
        }
        return prefix + ":ratelimit:bucketed:" + key;
    }

    /**
     * 获取限流器当前状态（仅令牌桶）
     *
     * @param key 限流维度键
     * @return 当前可用令牌数；键不存在时返回 -1
     *
     * @deprecated 自 v1.1.0 起标记废弃，无替代方案。该诊断方法未纳入正式 API 契约。
     */
    @Deprecated(since = "1.1.0", forRemoval = true)
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

    /**
     * 获取限流键的剩余 TTL（秒）。
     *
     * <p>用于查询当前限流冷却还需等待多长时间。键不存在时返回 0。
     *
     * @param key 限流维度键
     * @return 剩余秒数（0=可操作，>0=冷却中，<0=键不存在或查询失败）
     *
     * @deprecated 自 v1.1.0 起标记废弃，无替代方案。该诊断方法未纳入正式 API 契约。
     */
    @Deprecated(since = "1.1.0", forRemoval = true)
    public long getTtlSeconds(String key) {
        if (key == null) {
            return -1;
        }
        try {
            String formattedKey = formatKey(key);
            Long ttl = redisTemplate.getExpire(formattedKey);
            return ttl == null ? 0L : ttl;
        } catch (Exception e) {
            log.warn("【RedisRateLimiter】获取 TTL 失败 | key={}", key, e);
            return -1;
        }
    }

    private String formatKey(String key) {
        String prefix = redisProperties != null ? redisProperties.getKeyPrefix() : null;
        if (prefix == null || prefix.isEmpty()) {
            return "ratelimit:" + key;
        }
        return prefix + ":ratelimit:" + key;
    }

    /**
     * 分桶滑动窗口限流 Lua 脚本
     *
     * <p>使用 Hash 存储时间桶计数，替代 ZSET 存储每个请求的唯一 member。
     * 内存占用恒定为 O(bucketCount)，不受请求数量影响。
     *
     * <p>逻辑：
     * <ol>
     *   <li>计算当前时间桶编号</li>
     *   <li>删除超出窗口的旧桶</li>
     *   <li>统计所有存活桶的总计数</li>
     *   <li>若总计数 ≥ 限流阈值，拒绝并返回</li>
     *   <li>递增当前桶计数，设置 Key 过期时间</li>
     * </ol>
     */
    private static final String SLIDING_WINDOW_BUCKETED_LUA =
            "local key = KEYS[1] " +
            "local now = tonumber(ARGV[1]) " +
            "local windowMs = tonumber(ARGV[2]) " +
            "local limit = tonumber(ARGV[3]) " +
            "local bucketCount = tonumber(ARGV[4]) " +
            "local bucketSize = math.floor(windowMs / bucketCount) " +
            "local currentBucket = math.floor(now / bucketSize) " +
            "local fields = redis.call('HKEYS', key) " +
            "for i = 1, #fields do " +
            "  if tonumber(fields[i]) < currentBucket - bucketCount then " +
            "    redis.call('HDEL', key, fields[i]) " +
            "  end " +
            "end " +
            "local allValues = redis.call('HVALS', key) " +
            "local totalCount = 0 " +
            "for i = 1, #allValues do " +
            "  totalCount = totalCount + tonumber(allValues[i]) " +
            "end " +
            "if totalCount >= limit then " +
            "  return {0, totalCount} " +
            "end " +
            "redis.call('HINCRBY', key, currentBucket, 1) " +
            "redis.call('PEXPIRE', key, windowMs + 1000) " +
            "return {1, totalCount + 1}";

    /**
     * ZSet 精确滑动窗口限流 Lua 脚本（完整版）
     *
     * <p>使用 ZSET 存储每个请求的唯一 member（时间戳+计数器），score 为请求时间戳。
     * 精度达到毫秒级，限流平滑无边界突发。内存占用为 O(requestCount)。
     *
     * <p>逻辑：
     * <ol>
     *   <li>移除窗口之外的旧记录（score < now - windowMs）</li>
     *   <li>统计当前窗口内的记录数</li>
     *   <li>若记录数 >= limit，拒绝</li>
     *   <li>否则添加当前请求 member 并设置过期时间</li>
     * </ol>
     */
    private static final String SLIDING_WINDOW_ZSET_LUA =
            "local key = KEYS[1] " +
            "local now = tonumber(ARGV[1]) " +
            "local windowMs = tonumber(ARGV[2]) " +
            "local limit = tonumber(ARGV[3]) " +
            "local member = ARGV[4] " +
            "local clearBefore = now - windowMs " +
            "redis.call('ZREMRANGEBYSCORE', key, 0, clearBefore) " +
            "local current = redis.call('ZCARD', key) " +
            "if current >= limit then " +
            "  return {0, current} " +
            "end " +
            "redis.call('ZADD', key, now, member) " +
            "redis.call('PEXPIRE', key, windowMs + 1000) " +
            "return {1, current + 1}";

    // ==================== P2-13: 恢复 ZSet 滑动窗口完整版 ====================

    /**
     * 滑动窗口限流（ZSet 精确版，保留用于需要毫秒级精度的场景）
     *
     * <p>使用 Redis ZSET 存储每个请求的唯一标记，精度达到毫秒级。
     * 与分桶版相比精度更高但内存占用随请求量线性增长。
     *
     * <p>适用场景：
     * <ul>
     *   <li>限流阈值较低（< 1000 次/窗口）</li>
     *   <li>需要严格滑动窗口语义、不能容忍桶边界突发的场景</li>
     *   <li>对内存敏感度较低，对精度敏感度较高的场景</li>
     * </ul>
     *
     * @param key    限流维度键
     * @param limit  窗口内最大请求数
     * @param window 时间窗口长度
     * @return true=允许，false=拒绝
     *
     * @deprecated 自 v1.1.0 起标记废弃。请使用 {@link #tryAcquireSlidingWindow(String, int, Duration)}。
     */
    @Deprecated(since = "1.1.0", forRemoval = true)
    public boolean tryAcquireSlidingWindowExact(String key, int limit, Duration window) {
        if (key == null || limit <= 0 || window == null || window.isZero() || window.isNegative()) {
            return false;
        }
        try {
            String formattedKey = formatSlidingWindowExactKey(key);
            long now = System.currentTimeMillis();
            long windowMs = window.toMillis();
            // member = 时间戳-计数器，保证唯一性
            String member = now + "-" + Thread.currentThread().getId() + "-" + System.nanoTime();
            DefaultRedisScript<?> script = getOrCreateScript(
                    "sliding_window_zset", SLIDING_WINDOW_ZSET_LUA, List.class);
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
            log.error("【RedisRateLimiter】ZSet 精确滑动窗口限流异常 | key={} | error={}", key, e);
            return handleException("ZSet 精确滑动窗口限流", key, e);
        }
    }

    private String formatSlidingWindowExactKey(String key) {
        String prefix = redisProperties != null ? redisProperties.getKeyPrefix() : null;
        if (prefix == null || prefix.isEmpty()) {
            return "ratelimit:exact:" + key;
        }
        return prefix + ":ratelimit:exact:" + key;
    }

    // ==================== P1-10: 高阶限流工具方法 ====================

    /**
     * 限流异常（被限流时抛出）
     *
     * <p>包含限流键、限制阈值和窗口信息，便于调用方按异常类型处理限流场景。</p>
     */
    public static class RateLimitException extends RuntimeException {
        private final String key;
        private final int limit;
        private final Duration window;

        public RateLimitException(String key, int limit, Duration window) {
            super(String.format("Rate limit exceeded: key=%s, limit=%d per %s", key, limit, window));
            this.key = key;
            this.limit = limit;
            this.window = window;
        }

        public String getKey() {
            return key;
        }

        public int getLimit() {
            return limit;
        }

        public Duration getWindow() {
            return window;
        }
    }

    /**
     * 固定窗口限流（限流时抛出异常版）
     *
     * <p>当请求被限流时抛出 {@link RateLimitException}，
     * 适用于"限流即错误"的业务场景（如 API 返回 429）。
     *
     * @param key    限流维度键
     * @param limit  窗口内最大请求数
     * @param window 时间窗口长度
     * @throws RateLimitException 当请求被限流时
     */
    public void tryAcquireFixedWindowOrThrow(String key, int limit, Duration window) {
        if (!tryAcquireFixedWindow(key, limit, window)) {
            throw new RateLimitException(key, limit, window);
        }
    }

    /**
     * 令牌桶限流（限流时抛出异常版）
     *
     * @param key      限流维度键
     * @param rate     令牌补充速率（每秒）
     * @param capacity 桶容量
     * @throws RateLimitException 当请求被限流时
     */
    public void tryAcquireTokenBucketOrThrow(String key, int rate, int capacity) {
        if (!tryAcquireTokenBucket(key, rate, capacity)) {
            throw new RateLimitException(key, capacity, Duration.ofSeconds(1));
        }
    }

    /**
     * 滑动窗口限流（限流时抛出异常版）
     *
     * @param key    限流维度键
     * @param limit  窗口内最大请求数
     * @param window 时间窗口长度
     * @throws RateLimitException 当请求被限流时
     */
    public void tryAcquireSlidingWindowOrThrow(String key, int limit, Duration window) {
        if (!tryAcquireSlidingWindow(key, limit, window)) {
            throw new RateLimitException(key, limit, window);
        }
    }

    /**
     * 执行被限流保护的操作（限流时执行降级逻辑）
     *
     * <p>这是结构化的限流编程模式：
     * <ul>
     *   <li>通过限流：执行 {@code action} 并返回结果</li>
     *   <li>未通过限流：执行 {@code onRateLimited} 降级逻辑并返回其结果</li>
     * </ul>
     *
     * <p>使用示例：
     * <pre>{@code
     * String result = rateLimiter.executeOrRun(
     *     "api:user:" + userId,
     *     100,
     *     Duration.ofMinutes(1),
     *     () -> callExternalApi(userId),          // 正常逻辑
     *     () -> "[]",                             // 限流降级：返回空 JSON 数组
     * );
     * }</pre>
     *
     * @param key            限流维度键
     * @param limit          窗口内最大请求数
     * @param window         时间窗口长度
     * @param action         正常执行的逻辑
     * @param onRateLimited  限流时的降级逻辑
     * @param <T>            返回值类型
     * @return 执行结果
     */
    public <T> T executeOrRun(String key, int limit, Duration window,
                              java.util.function.Supplier<T> action,
                              java.util.function.Supplier<T> onRateLimited) {
        if (tryAcquireSlidingWindow(key, limit, window)) {
            return action.get();
        }
        log.info("【RedisRateLimiter】限流降级 | key={} | limit={} | window={}", key, limit, window);
        return onRateLimited.get();
    }

    /**
     * 执行被限流保护的操作（限流时抛出异常）
     *
     * <p>语义明确：限流即异常，调用方通过捕获 {@link RateLimitException} 处理。</p>
     *
     * <p>使用示例：
     * <pre>{@code
     * try {
     *     rateLimiter.executeOrThrow(
     *         "order:create:" + userId,
     *         5,
     *         Duration.ofSeconds(1),
     *         () -> orderService.create(order)
     *     );
     * } catch (RateLimitException e) {
     *     return Result.fail("请求过于频繁，请稍后重试");
     * }
     * }</pre>
     *
     * @param key    限流维度键
     * @param limit  窗口内最大请求数
     * @param window 时间窗口长度
     * @param action 要执行的逻辑
     * @param <T>    返回值类型
     * @return 执行结果
     * @throws RateLimitException 当请求被限流时
     */
    public <T> T executeOrThrow(String key, int limit, Duration window,
                                java.util.function.Supplier<T> action) {
        if (!tryAcquireSlidingWindow(key, limit, window)) {
            throw new RateLimitException(key, limit, window);
        }
        return action.get();
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
