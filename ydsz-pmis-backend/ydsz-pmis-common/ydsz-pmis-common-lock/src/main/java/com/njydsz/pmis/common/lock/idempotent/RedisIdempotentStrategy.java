package com.njydsz.pmis.common.lock.idempotent;

import java.util.Collections;
import java.util.UUID;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.data.redis.core.script.RedisScript;

import lombok.extern.slf4j.Slf4j;

/**
 * 基于 Redis SET NX EX 的幂等策略默认实现
 *
 * <p>使用 Lua 脚本保证 acquire/release 的原子性：
 * <ul>
 *   <li>acquire：生成 UUID token，SET key token NX EX ttl，成功返回 token</li>
 *   <li>release：Lua 脚本校验 token 匹配后 DEL，避免误删他人持有的锁</li>
 *   <li>exists：检查 key 是否存在</li>
 * </ul>
 *
 * <p>Redis 不可用时 acquire 降级放行（返回非 null token），避免拖垮主流程。
 *
 * @since 1.0.0
 */
@Slf4j
public class RedisIdempotentStrategy implements IdempotentStrategy {

    /** Redis SET NX EX 原子 Lua 脚本 */
    private static final String ACQUIRE_LUA =
            "if redis.call('SET', KEYS[1], ARGV[1], 'NX', 'EX', ARGV[2]) then return 1 else return 0 end";

    /** 释放幂等锁的 Lua 脚本：仅当 value 匹配时才 DEL */
    private static final String RELEASE_LUA =
            "if redis.call('get', KEYS[1]) == ARGV[1] then return redis.call('del', KEYS[1]) else return 0 end";

    private static final RedisScript<Long> ACQUIRE_SCRIPT = new DefaultRedisScript<>(ACQUIRE_LUA, Long.class);
    private static final RedisScript<Long> RELEASE_SCRIPT = new DefaultRedisScript<>(RELEASE_LUA, Long.class);

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
    public String acquire(String key, long expireMillis) {
        if (expireMillis <= 0) {
            log.warn("[RedisIdempotentStrategy] expireMillis={} 非法，降级放行 key={}", expireMillis, key);
            return UUID.randomUUID().toString().replace("-", "");
        }
        long expireSeconds = Math.max(1, expireMillis / 1000);
        String token = UUID.randomUUID().toString().replace("-", "");
        try {
            Long ok = redisTemplate.execute(
                    ACQUIRE_SCRIPT,
                    Collections.singletonList(key),
                    token,
                    String.valueOf(expireSeconds)
            );
            if (ok != null && ok == 1L) {
                return token;
            }
            return null;
        } catch (Exception e) {
            log.warn("[RedisIdempotentStrategy] Redis 不可用，降级放行 key={} cause={}", key, e.getMessage());
            return token;
        }
    }

    @Override
    public boolean release(String key, String token) {
        if (token == null || token.isEmpty()) {
            return false;
        }
        try {
            Long result = redisTemplate.execute(
                    RELEASE_SCRIPT,
                    Collections.singletonList(key),
                    token
            );
            return Long.valueOf(1L).equals(result);
        } catch (Exception e) {
            log.warn("[RedisIdempotentStrategy] 释放幂等锁失败 key={} cause={}", key, e.getMessage());
            return false;
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
