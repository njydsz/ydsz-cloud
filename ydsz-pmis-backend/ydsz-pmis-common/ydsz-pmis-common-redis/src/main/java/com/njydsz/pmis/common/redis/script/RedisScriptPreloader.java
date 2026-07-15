package com.njydsz.pmis.common.redis.script;

import java.nio.charset.StandardCharsets;
import java.util.List;

import jakarta.annotation.PostConstruct;

import org.springframework.data.redis.core.RedisCallback;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Component;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Redis Lua 脚本启动预加载器
 *
 * <p>在应用启动时通过 {@code SCRIPT LOAD} 命令将所有 Lua 脚本预加载到 Redis 服务端缓存中，
 * 避免首次请求时因 {@code NOSCRIPT} 错误导致的额外网络往返开销。
 *
 * <p><b>注意：</b>此处的脚本内容需与各业务类中的 Lua 脚本保持同步。
 * 若业务类中的脚本更新，此处也需同步更新。
 * 即使未同步，首次调用时 {@code DefaultRedisScript} 会自动回退到 {@code EVAL}，
 * 功能不受影响，仅首次调用多一次网络往返。
 *
 * @author ydsz-pmis-team
 * @since 1.1.0
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class RedisScriptPreloader {

    private final RedisTemplate<String, Object> redisTemplate;

    /**
     * 全部已知 Lua 脚本（与各业务类中的 private static final 脚本保持同步）
     */
    private static final List<String> SCRIPTS = List.of(
            // RedisRateLimiter — 固定窗口
            "local current = redis.call('INCR', KEYS[1]) " +
                    "if current == 1 then " +
                    "  redis.call('EXPIRE', KEYS[1], ARGV[1]) " +
                    "end " +
                    "return current",

            // RedisRateLimiter — 滑动窗口
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
                    "end",

            // RedisRateLimiter — 令牌桶
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
                    "return {allowed, tokens}",

            // RedisBloomFilter — 添加元素
            "local key = KEYS[1]\n" +
                    "local bits = ARGV\n" +
                    "for i = 1, #bits do\n" +
                    "    redis.call('setbit', key, tonumber(bits[i]), 1)\n" +
                    "end\n" +
                    "return true",

            // RedisBloomFilter — 检查元素
            "local key = KEYS[1]\n" +
                    "local bits = ARGV\n" +
                    "for i = 1, #bits do\n" +
                    "    if redis.call('getbit', key, tonumber(bits[i])) == 0 then\n" +
                    "        return false\n" +
                    "    end\n" +
                    "end\n" +
                    "return true",

            // RedisSnowflakeIdGenerator — 分配 workerId
            "local current = redis.call('INCR', KEYS[1]) " +
                    "if current > tonumber(ARGV[1]) then " +
                    "  redis.call('SET', KEYS[1], 0) " +
                    "  return 0 " +
                    "end " +
                    "return current - 1",

            // RedisSnowflakeIdGenerator — 心跳续约
            "if redis.call('EXISTS', KEYS[1]) == 1 then " +
                    "  redis.call('EXPIRE', KEYS[1], tonumber(ARGV[1])) " +
                    "  return 1 " +
                    "end " +
                    "return 0",

            // 分布式锁 — 解锁（RedisCacheGuard / RedisDistributedLock / RedisStringOps 共用）
            "if redis.call('get', KEYS[1]) == ARGV[1] then return redis.call('del', KEYS[1]) else return 0 end",

            // 分布式锁 — 续期（RedisCacheGuard / RedisDistributedLock 共用）
            "if redis.call('get', KEYS[1]) == ARGV[1] then return redis.call('pexpire', KEYS[1], ARGV[2]) else return 0 end"
    );

    /**
     * 启动时预加载所有 Lua 脚本
     *
     * <p>通过 {@code SCRIPT LOAD} 将脚本加载到 Redis 服务端缓存，
     * 后续 {@code EVALSHA} 调用可直接使用 SHA1 值，无需发送完整脚本。
     */
    @PostConstruct
    public void preload() {
        int loaded = 0;
        int failed = 0;
        for (int i = 0; i < SCRIPTS.size(); i++) {
            String script = SCRIPTS.get(i);
            try {
                redisTemplate.execute((RedisCallback<String>) connection -> {
                    byte[] scriptBytes = script.getBytes(StandardCharsets.UTF_8);
                    return connection.scriptingCommands().scriptLoad(scriptBytes);
                });
                loaded++;
            } catch (Exception e) {
                failed++;
                log.warn("【RedisScriptPreloader】Lua 脚本预加载失败 | index={} | error={}", i, e.getMessage());
            }
        }
        log.info("【RedisScriptPreloader】Lua 脚本预加载完成 | loaded={} | failed={} | total={}",
                loaded, failed, SCRIPTS.size());
    }
}
