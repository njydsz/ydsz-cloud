package com.njydsz.pmis.common.lock.impl;

import com.njydsz.pmis.common.lock.core.AbstractRedisDistributedLock;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;

import java.time.Duration;
import java.util.Collections;
import java.util.concurrent.TimeUnit;

/**
 * Redis 可重入分布式锁实现
 *
 * <p>基于 Redis Hash 结构实现可重入语义：
 * <ul>
 *   <li>Hash Key: lockKey</li>
 *   <li>Hash Field: clientId</li>
 *   <li>Hash Value: 重入计数</li>
 * </ul>
 *
 * <p><b>核心机制：</b>
 * <ul>
 *   <li>首次获取锁：设置 Hash 字段值为 1</li>
 *   <li>重入获取：原子性递增计数</li>
 *   <li>释放锁：原子性递减计数，计数为 0 时删除整个 Hash</li>
 * </ul>
 *
 * @author Marvin Lee
 * @email limw1888@126.com
 * @version 3.5.0
 * @since 1.0.0
 */
@Slf4j
public class RedisReentrantLock extends AbstractRedisDistributedLock {

    /**
     * 获取可重入锁 Lua 脚本
     * <p>如果当前客户端已持有锁则递增重入计数，否则在无其他持有时创建新锁
     */
    private static final String ACQUIRE_LOCK_LUA_SCRIPT =
            "local key = KEYS[1] " +
            "local clientId = ARGV[1] " +
            "local leaseTimeMs = ARGV[2] " +
            "if redis.call('HEXISTS', key, clientId) == 1 then " +
            "    redis.call('HINCRBY', key, clientId, 1) " +
            "    redis.call('PEXPIRE', key, leaseTimeMs) " +
            "    return 1 " +
            "elseif redis.call('HLEN', key) == 0 then " +
            "    redis.call('HSET', key, clientId, 1) " +
            "    redis.call('HSET', key, '__ydsz_lease_ms__', leaseTimeMs) " +
            "    redis.call('PEXPIRE', key, leaseTimeMs) " +
            "    return 1 " +
            "else " +
            "    return 0 " +
            "end";

    /**
     * 释放可重入锁 Lua 脚本
     * <p>递减重入计数，计数归零时删除整个 Hash 键
     */
    private static final String RELEASE_LOCK_LUA_SCRIPT =
            "local key = KEYS[1] " +
            "local clientId = ARGV[1] " +
            "if redis.call('HEXISTS', key, clientId) == 0 then " +
            "    return 0 " +
            "end " +
            "local count = redis.call('HINCRBY', key, clientId, -1) " +
            "if count > 0 then " +
            "    local leaseTimeMs = redis.call('HGET', key, '__ydsz_lease_ms__') " +
            "    if leaseTimeMs then " +
            "        redis.call('PEXPIRE', key, leaseTimeMs) " +
            "    end " +
            "    return 1 " +
            "else " +
            "    redis.call('HDEL', key, '__ydsz_lease_ms__') " +
            "    redis.call('DEL', key) " +
            "    return 1 " +
            "end";

    /**
     * 获取重入计数 Lua 脚本
     * <p>查询当前客户端在指定锁上的重入计数
     */
    private static final String GET_HOLD_COUNT_LUA_SCRIPT =
            "local key = KEYS[1] " +
            "local clientId = ARGV[1] " +
            "local count = redis.call('HGET', key, clientId) " +
            "if count then " +
            "    return tonumber(count) " +
            "else " +
            "    return 0 " +
            "end";

    /**
     * 续期锁 Lua 脚本
     * <p>仅当当前客户端持有锁时才续期，否则返回失败
     */
    private static final String RENEW_LOCK_LUA_SCRIPT =
            "local key = KEYS[1] " +
            "local clientId = ARGV[1] " +
            "local leaseTimeMs = ARGV[2] " +
            "if redis.call('HEXISTS', key, clientId) == 1 then " +
            "    redis.call('PEXPIRE', key, leaseTimeMs) " +
            "    return 1 " +
            "else " +
            "    return 0 " +
            "end";

    /**
     * 获取锁脚本封装
     */
    private final DefaultRedisScript<Long> acquireLockScript;
    /**
     * 释放锁脚本封装
     */
    private final DefaultRedisScript<Long> releaseLockScript;
    /**
     * 获取重入计数脚本封装
     */
    private final DefaultRedisScript<Long> getHoldCountScript;
    /**
     * 续期锁脚本封装
     */
    private final DefaultRedisScript<Long> renewLockScript;

    /**
     * 构造可重入锁（无命名空间）
     *
     * @param stringRedisTemplate Redis 操作模板
     */
    public RedisReentrantLock(StringRedisTemplate stringRedisTemplate) {
        this(stringRedisTemplate, null);
    }

    /**
     * 构造可重入锁（带命名空间）
     *
     * @param stringRedisTemplate Redis 操作模板
     * @param namespace           锁键命名空间前缀，用于多应用共享 Redis 时的隔离
     */
    public RedisReentrantLock(StringRedisTemplate stringRedisTemplate, String namespace) {
        super(stringRedisTemplate, namespace);
        this.acquireLockScript = new DefaultRedisScript<>(ACQUIRE_LOCK_LUA_SCRIPT, Long.class);
        this.releaseLockScript = new DefaultRedisScript<>(RELEASE_LOCK_LUA_SCRIPT, Long.class);
        this.getHoldCountScript = new DefaultRedisScript<>(GET_HOLD_COUNT_LUA_SCRIPT, Long.class);
        this.renewLockScript = new DefaultRedisScript<>(RENEW_LOCK_LUA_SCRIPT, Long.class);
    }

    @Override
    protected String doAcquireLock(String lockKey, String clientId, long leaseTime, TimeUnit timeUnit) {
        long leaseTimeMs = timeUnit.toMillis(leaseTime);
        try {
            Long result = stringRedisTemplate.execute(
                    acquireLockScript,
                    Collections.singletonList(lockKey),
                    clientId,
                    String.valueOf(leaseTimeMs)
            );
            boolean acquired = Long.valueOf(1L).equals(result);
            if (acquired) {
                log.debug("【分布式锁】获取可重入锁成功 | lockKey={} | clientId={}", lockKey, clientId);
                recordLeaseTime(lockKey, leaseTimeMs);
                startWatchDog(lockKey, clientId, leaseTimeMs);
                return clientId;
            }
            return null;
        } catch (Exception e) {
            log.error("【分布式锁】获取可重入锁异常 | lockKey={} | error={}", lockKey, e.getMessage(), e);
            return null;
        }
    }

    @Override
    protected boolean doReleaseLock(String lockKey, String clientId) {
        try {
            Long result = stringRedisTemplate.execute(
                    releaseLockScript,
                    Collections.singletonList(lockKey),
                    clientId
            );
            boolean released = Long.valueOf(1L).equals(result);
            if (released) {
                log.debug("【分布式锁】释放可重入锁成功 | lockKey={} | clientId={}", lockKey, clientId);
            }
            return released;
        } catch (Exception e) {
            log.error("【分布式锁】释放可重入锁异常 | lockKey={} | error={}", lockKey, e.getMessage(), e);
            return false;
        }
    }

    @Override
    protected boolean doIsLocked(String lockKey) {
        try {
            Long size = stringRedisTemplate.opsForHash().size(lockKey);
            return size != null && size > 0;
        } catch (Exception e) {
            log.error("【分布式锁】检查锁状态异常 | lockKey={} | error={}", lockKey, e.getMessage(), e);
            return false;
        }
    }

    @Override
    protected long doGetRemainTime(String lockKey) {
        try {
            return stringRedisTemplate.getExpire(lockKey, TimeUnit.MILLISECONDS);
        } catch (Exception e) {
            log.error("【分布式锁】获取剩余时间异常 | lockKey={} | error={}", lockKey, e.getMessage(), e);
            return -2;
        }
    }

    /**
     * 获取当前客户端在指定锁上的重入计数
     *
     * @param lockKey   锁的键
     * @param lockValue 锁的值（客户端标识）
     * @return 重入计数，未持有锁时返回 0
     */
    @Override
    public int getHoldCount(String lockKey, String lockValue) {
        try {
            Long result = stringRedisTemplate.execute(
                    getHoldCountScript,
                    Collections.singletonList(lockKey),
                    lockValue
            );
            return result != null ? result.intValue() : 0;
        } catch (Exception e) {
            log.error("【分布式锁】获取重入计数异常 | lockKey={} | error={}", lockKey, e.getMessage(), e);
            return 0;
        }
    }

    /**
     * 判断指定锁是否由当前线程持有
     *
     * @param lockKey   锁的键
     * @param lockValue 锁的值（客户端标识）
     * @return true-当前线程持有该锁
     */
    @Override
    public boolean isHeldByCurrentThread(String lockKey, String lockValue) {
        return getHoldCount(lockKey, lockValue) > 0;
    }

    /**
     * 续期锁，延长锁的过期时间
     *
     * <p>仅当当前客户端持有锁时才续期，否则返回失败。
     *
     * @param lockKey   锁的键
     * @param lockValue 锁的值（客户端标识）
     * @param leaseTime 新的租约时间
     * @param timeUnit  时间单位
     * @return true-续期成功，false-续期失败（锁已被释放或不属于当前客户端）
     */
    public boolean renewLock(String lockKey, String lockValue, long leaseTime, TimeUnit timeUnit) {
        try {
            Long result = stringRedisTemplate.execute(
                    renewLockScript,
                    Collections.singletonList(lockKey),
                    lockValue,
                    String.valueOf(timeUnit.toMillis(leaseTime))
            );
            return Long.valueOf(1L).equals(result);
        } catch (Exception e) {
            log.error("【分布式锁】续期锁异常 | lockKey={} | error={}", lockKey, e.getMessage(), e);
            return false;
        }
    }

    /**
     * 尝试获取锁（不等待）
     *
     * @param lockKey   锁的键
     * @param leaseTime 租约时间
     * @param timeUnit  时间单位
     * @return 锁值（客户端标识），获取失败返回 null
     */
    @Override
    public String tryLock(String lockKey, long leaseTime, TimeUnit timeUnit) {
        String namespacedKey = buildNamespacedKey(lockKey);
        String clientId = getClientId(namespacedKey);
        String result = doAcquireLock(namespacedKey, clientId, leaseTime, timeUnit);
        if (result == null) {
            // 锁获取失败时清理 ThreadLocal，防止泄漏（调用方不会调用 unlock）
            clearClientId(namespacedKey);
            clearLeaseTime(namespacedKey);
        }
        return result;
    }

    /**
     * 尝试获取锁（带等待时间）
     *
     * @param lockKey   锁的键
     * @param waitTime  最大等待时间
     * @param leaseTime 租约时间
     * @param timeUnit  时间单位
     * @return 锁值（客户端标识），获取失败返回 null
     * @throws InterruptedException 等待过程中线程被中断
     */
    @Override
    public String tryLock(String lockKey, long waitTime, long leaseTime, TimeUnit timeUnit) throws InterruptedException {
        String namespacedKey = buildNamespacedKey(lockKey);
        return tryLockWithWait(namespacedKey, waitTime, leaseTime, timeUnit);
    }

    /**
     * 设置键的过期时间（毫秒精度）
     *
     * @param key      Redis 键
     * @param time     过期时间
     * @param unit     时间单位
     * @return 设置成功返回过期时间的毫秒值，失败返回 0
     */
    @Override
    public long pexpire(String key, long time, TimeUnit unit) {
        try {
            Boolean result = stringRedisTemplate.expire(key, Duration.ofMillis(unit.toMillis(time)));
            return Boolean.TRUE.equals(result) ? unit.toMillis(time) : 0;
        } catch (Exception e) {
            log.error("【分布式锁】PEXPIRE 续期异常 | lockKey={} | error={}", key, e.getMessage(), e);
            return 0;
        }
    }
}
