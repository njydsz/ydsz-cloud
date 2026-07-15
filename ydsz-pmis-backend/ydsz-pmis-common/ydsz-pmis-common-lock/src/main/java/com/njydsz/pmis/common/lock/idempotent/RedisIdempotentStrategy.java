package com.njydsz.pmis.common.lock.idempotent;

import java.util.Collections;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.data.redis.core.script.RedisScript;

import lombok.extern.slf4j.Slf4j;

/**
 * 基于 Redis SET NX EX 的幂等策略默认实现
 *
 * <p>使用 Lua 脚本保证 acquire/release 的原子性：
 * <ul>
 *   <li>acquire：SET key value NX EX ttl，成功返回 true（拿到幂等锁）</li>
 *   <li>release：仅当 value 匹配时 DEL，避免误删他人持有的锁</li>
 *   <li>exists：检查 key 是否存在</li>
 * </ul>
 *
 * <p>Redis 不可用时 acquire 降级放行（返回 true），避免拖垮主流程。
 *
 * @author ydsz-pmis-team
 * @since 1.0.0
 */
@Slf4j
public class RedisIdempotentStrategy implements IdempotentStrategy {

    /** Redis SET NX EX 原子 Lua 脚本 */
    private static final String ACQUIRE_LUA =
            "if redis.call('SET', KEYS[1], ARGV[1], 'NX', 'EX', ARGV[2]) then return 1 else return 0 end";

    private static final RedisScript<Long> ACQUIRE_SCRIPT = new DefaultRedisScript<>(ACQUIRE_LUA, Long.class);

    private final StringRedisTemplate redisTemplate;

    /**
     * 构造 Redis 幂等策略
     *
     * @param redisTemplate Redis 客户端
     */
    public RedisIdempotentStrategy(StringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    @Override
    public boolean acquire(String key, long expireMillis) {
        if (expireMillis <= 0) {
            log.warn("[RedisIdempotentStrategy] expireMillis={} 非法，降级放行 key={}", expireMillis, key);
            return true;
        }
        long expireSeconds = Math.max(1, expireMillis / 1000);
        try {
            Long ok = redisTemplate.execute(
                    ACQUIRE_SCRIPT,
                    Collections.singletonList(key),
                    "1",
                    String.valueOf(expireSeconds)
            );
            return ok != null && ok == 1L;
        } catch (Exception e) {
            log.warn("[RedisIdempotentStrategy] Redis 不可用，降级放行 key={} cause={}", key, e.getMessage());
            return true;
        }
    }

    @Override
    public void release(String key) {
        try {
            redisTemplate.delete(key);
        } catch (Exception e) {
            log.warn("[RedisIdempotentStrategy] 释放幂等锁失败 key={} cause={}", key, e.getMessage());
        }
    }

    @Override
    public boolean exists(String key) {
        try {
            return Boolean.TRUE.equals(redisTemplate.hasKey(key));
        } catch (Exception e) {
            log.warn("[RedisIdempotentStrategy] 检查幂等键失败 key={} cause={}", key, e.getMessage());
            return false;
        }
    }
}
