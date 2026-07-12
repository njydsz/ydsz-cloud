package com.njydsz.pmis.common.lock.impl;

import com.njydsz.pmis.common.lock.core.AbstractRedisDistributedLock;
import com.njydsz.pmis.common.lock.core.DistributedLocker;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;

import java.time.Duration;
import java.util.Arrays;
import java.util.Collections;
import java.util.concurrent.TimeUnit;

/**
 * Redis 公平分布式锁实现
 *
 * <p>基于 Redis List 队列实现公平调度，按客户端请求顺序获取锁（先到先得）。
 * 内部使用 Lua 脚本保证入队、出队、锁获取的原子性。
 *
 * <p><b>实现机制：</b>
 * <ul>
 *   <li>队列管理：通过 Redis List 维护等待队列，新请求追加到队尾</li>
 *   <li>原子调度：Lua 脚本检查队首客户端，仅队首客户端可获取锁</li>
 *   <li>可重入支持：同一客户端可多次获取锁，内部维护重入计数</li>
 * </ul>
 *
 * <p><b>适用场景：</b>需要严格按顺序执行的分布式任务，避免饥饿问题。
 *
 * @author Marvin Lee
 * @email limw1888@126.com
 * @version 3.5.0
 * @since 1.0.0
 * @see DistributedLocker
 * @see RedisReentrantLock
 */
@Slf4j
public class RedisFairLock extends AbstractRedisDistributedLock {

    /**
     * 获取公平锁 Lua 脚本
     * <p>支持可重入：当前客户端已持有时递增计数；否则检查等待队列队首，仅队首客户端可获取锁
     * <p>兼容 Redis 6.0 以下版本：使用 LINDEX 遍历替代 LPOS 检查队列中是否存在客户端
     */
    private static final String ACQUIRE_LOCK_LUA_SCRIPT =
            "local lockKey = KEYS[1] " +
            "local queueKey = KEYS[2] " +
            "local clientId = ARGV[1] " +
            "local leaseTimeMs = ARGV[2] " +
            "local function isInQueue(queueKey, clientId) " +
            "    local len = redis.call('LLEN', queueKey) " +
            "    for i = 0, len - 1, 1 do " +
            "        if redis.call('LINDEX', queueKey, i) == clientId then " +
            "            return true " +
            "        end " +
            "    end " +
            "    return false " +
            "end " +
            "if redis.call('HEXISTS', lockKey, 'owner') == 1 then " +
            "    if redis.call('HGET', lockKey, 'owner') == clientId then " +
            "        local count = redis.call('HINCRBY', lockKey, '__count', 1) " +
            "        redis.call('PEXPIRE', lockKey, leaseTimeMs) " +
            "        return 1 " +
            "    else " +
            "        if not isInQueue(queueKey, clientId) then " +
            "            redis.call('RPUSH', queueKey, clientId) " +
            "        end " +
            "        return 0 " +
            "    end " +
            "end " +
            "local headClient = redis.call('LINDEX', queueKey, 0) " +
            "if headClient == false then " +
            "    redis.call('HSET', lockKey, 'owner', clientId) " +
            "    redis.call('HSET', lockKey, '__count', 1) " +
            "    redis.call('HSET', lockKey, '__leaseTime', leaseTimeMs) " +
            "    redis.call('PEXPIRE', lockKey, leaseTimeMs) " +
            "    return 1 " +
            "end " +
            "if headClient == clientId then " +
            "    redis.call('HSET', lockKey, 'owner', clientId) " +
            "    redis.call('HSET', lockKey, '__count', 1) " +
            "    redis.call('HSET', lockKey, '__leaseTime', leaseTimeMs) " +
            "    redis.call('PEXPIRE', lockKey, leaseTimeMs) " +
            "    redis.call('LPOP', queueKey) " +
            "    return 1 " +
            "end " +
            "if not isInQueue(queueKey, clientId) then " +
            "    redis.call('RPUSH', queueKey, clientId) " +
            "end " +
            "return 0";

    /**
     * 释放公平锁 Lua 脚本
     * <p>递减重入计数，计数归零时删除锁并从等待队列中移除客户端
     */
    private static final String RELEASE_LOCK_LUA_SCRIPT =
            "local lockKey = KEYS[1] " +
            "local queueKey = KEYS[2] " +
            "local clientId = ARGV[1] " +
            "local owner = redis.call('HGET', lockKey, 'owner') " +
            "if owner == clientId then " +
            "    local count = redis.call('HGET', lockKey, '__count') " +
            "    if count and tonumber(count) > 1 then " +
            "        redis.call('HINCRBY', lockKey, '__count', -1) " +
            "        local leaseTimeMs = redis.call('HGET', lockKey, '__leaseTime') " +
            "        if leaseTimeMs then " +
            "            redis.call('PEXPIRE', lockKey, leaseTimeMs) " +
            "        end " +
            "        return 1 " +
            "    else " +
            "        redis.call('DEL', lockKey) " +
            "        redis.call('LREM', queueKey, 1, clientId) " +
            "        return 1 " +
            "    end " +
            "else " +
            "    return 0 " +
            "end";

    /**
     * 续期公平锁 Lua 脚本
     * <p>仅当当前客户端是锁的持有者时才续期
     */
    private static final String RENEW_LOCK_LUA_SCRIPT =
            "local lockKey = KEYS[1] " +
            "local clientId = ARGV[1] " +
            "local leaseTimeMs = ARGV[2] " +
            "if redis.call('HGET', lockKey, 'owner') == clientId then " +
            "    redis.call('PEXPIRE', lockKey, leaseTimeMs) " +
            "    return 1 " +
            "else " +
            "    return 0 " +
            "end";

    /**
     * 等待队列默认过期时间（秒）
     */
    private static final long QUEUE_EXPIRE_SECONDS = 3600;

    /**
     * 清理等待队列中指定客户端 Lua 脚本
     * <p>从队列中移除 clientId，并设置队列 TTL 防止孤立队列
     */
    private static final String CLEANUP_QUEUE_LUA_SCRIPT =
            "local queueKey = KEYS[1] " +
            "local clientId = ARGV[1] " +
            "local queueTtlSeconds = ARGV[2] " +
            "redis.call('LREM', queueKey, 1, clientId) " +
            "if redis.call('LLEN', queueKey) == 0 then " +
            "    redis.call('DEL', queueKey) " +
            "else " +
            "    redis.call('EXPIRE', queueKey, queueTtlSeconds) " +
            "end " +
            "return 1";

    /**
     * 获取锁脚本封装
     */
    private final DefaultRedisScript<Long> acquireLockScript;
    /**
     * 释放锁脚本封装
     */
    private final DefaultRedisScript<Long> releaseLockScript;
    /**
     * 续期锁脚本封装
     */
    private final DefaultRedisScript<Long> renewLockScript;
    /**
     * 清理队列脚本封装
     */
    private final DefaultRedisScript<Long> cleanupQueueScript;

    /**
     * 构造公平锁（无命名空间）
     *
     * @param stringRedisTemplate Redis 操作模板
     */
    public RedisFairLock(StringRedisTemplate stringRedisTemplate) {
        this(stringRedisTemplate, null);
    }

    /**
     * 构造公平锁（带命名空间）
     *
     * @param stringRedisTemplate Redis 操作模板
     * @param namespace           锁键命名空间前缀，用于多应用共享 Redis 时的隔离
     */
    public RedisFairLock(StringRedisTemplate stringRedisTemplate, String namespace) {
        super(stringRedisTemplate, namespace);
        this.acquireLockScript = new DefaultRedisScript<>(ACQUIRE_LOCK_LUA_SCRIPT, Long.class);
        this.releaseLockScript = new DefaultRedisScript<>(RELEASE_LOCK_LUA_SCRIPT, Long.class);
        this.renewLockScript = new DefaultRedisScript<>(RENEW_LOCK_LUA_SCRIPT, Long.class);
        this.cleanupQueueScript = new DefaultRedisScript<>(CLEANUP_QUEUE_LUA_SCRIPT, Long.class);
    }

    /**
     * 获取公平锁等待队列的 Redis Key
     *
     * @param lockKey 锁的键
     * @return 等待队列键
     */
    private String getQueueKey(String lockKey) {
        return lockKey + ":fair:queue";
    }

    /**
     * 尝试获取公平锁（不等待）
     *
     * <p>按等待队列顺序获取锁，当前客户端在队首或锁空闲时可获取。
     *
     * @param lockKey   锁的键
     * @param leaseTime 租约时间
     * @param timeUnit  时间单位
     * @return 锁值（客户端标识），获取失败返回 null
     */
    @Override
    public String tryLock(String lockKey, long leaseTime, TimeUnit timeUnit) {
        String namespacedKey = buildNamespacedKey(lockKey);
        long leaseTimeMs = timeUnit.toMillis(leaseTime);
        String clientId = getClientId(namespacedKey);
        String queueKey = getQueueKey(namespacedKey);
        boolean acquired = false;
        try {
            stringRedisTemplate.expire(queueKey, Duration.ofSeconds(QUEUE_EXPIRE_SECONDS));
            Long result = stringRedisTemplate.execute(
                    acquireLockScript,
                    Arrays.asList(namespacedKey, queueKey),
                    clientId,
                    String.valueOf(leaseTimeMs)
            );
            acquired = Long.valueOf(1L).equals(result);
            if (acquired) {
                log.debug("【分布式锁】获取公平锁成功 | lockKey={} | clientId={}", lockKey, clientId);
                recordLeaseTime(namespacedKey, leaseTimeMs);
                startWatchDog(namespacedKey, clientId, leaseTimeMs);
                return clientId;
            }
            return null;
        } catch (Exception e) {
            log.error("【分布式锁】获取公平锁异常 | lockKey={} | error={}", lockKey, e.getMessage(), e);
            return null;
        } finally {
            // 锁获取失败时清理 ThreadLocal 和等待队列，防止泄漏（调用方不会调用 unlock）
            if (!acquired) {
                clearClientId(namespacedKey);
                clearLeaseTime(namespacedKey);
                cleanupQueue(queueKey, clientId);
            }
        }
    }

    /**
     * 尝试获取公平锁（带等待时间）
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

    @Override
    protected String doAcquireLock(String lockKey, String clientId, long leaseTime, TimeUnit timeUnit) {
        return tryLock(lockKey, leaseTime, timeUnit);
    }

    @Override
    protected boolean doReleaseLock(String lockKey, String clientId) {
        String queueKey = getQueueKey(lockKey);
        try {
            Long result = stringRedisTemplate.execute(
                    releaseLockScript,
                    Arrays.asList(lockKey, queueKey),
                    clientId
            );
            boolean released = Long.valueOf(1L).equals(result);
            if (released) {
                log.debug("【分布式锁】释放公平锁成功 | lockKey={} | clientId={}", lockKey, clientId);
            }
            return released;
        } catch (Exception e) {
            log.error("【分布式锁】释放公平锁异常 | lockKey={} | error={}", lockKey, e.getMessage(), e);
            return false;
        }
    }

    @Override
    protected boolean doIsLocked(String lockKey) {
        try {
            return Boolean.TRUE.equals(stringRedisTemplate.hasKey(lockKey));
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
     * 续期公平锁，延长锁的过期时间
     *
     * <p>仅当当前客户端是锁的持有者时才续期，否则返回失败。
     *
     * @param lockKey   锁的键
     * @param lockValue 锁的值（客户端标识）
     * @param leaseTime 新的租约时间
     * @param timeUnit  时间单位
     * @return true-续期成功，false-续期失败
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
     * 清理等待队列中的指定客户端
     * <p>在获取锁失败或超时时调用，防止客户端遗留在队列中
     *
     * @param queueKey 队列键
     * @param clientId 客户端标识
     */
    private void cleanupQueue(String queueKey, String clientId) {
        try {
            stringRedisTemplate.execute(
                    cleanupQueueScript,
                    Collections.singletonList(queueKey),
                    clientId,
                    String.valueOf(QUEUE_EXPIRE_SECONDS)
            );
            log.debug("【分布式锁】公平锁等待队列清理 | queueKey={} | clientId={}", queueKey, clientId);
        } catch (Exception e) {
            log.debug("【分布式锁】清理等待队列异常 | queueKey={} | error={}", queueKey, e.getMessage());
        }
    }

    @Override
    public int getQueuePosition(String lockKey, String lockValue) {
        String queueKey = getQueueKey(lockKey);
        try {
            Long index = stringRedisTemplate.opsForList().indexOf(queueKey, lockValue);
            return index != null ? index.intValue() : -1;
        } catch (Exception e) {
            log.error("【分布式锁】获取排队位置异常 | lockKey={} | error={}", lockKey, e.getMessage(), e);
            return -1;
        }
    }

    @Override
    public int getQueueSize(String lockKey) {
        String queueKey = getQueueKey(lockKey);
        try {
            Long size = stringRedisTemplate.opsForList().size(queueKey);
            return size != null ? size.intValue() : -1;
        } catch (Exception e) {
            log.error("【分布式锁】获取排队大小异常 | lockKey={} | error={}", lockKey, e.getMessage(), e);
            return -1;
        }
    }

    /**
     * 设置键的过期时间（毫秒精度）
     *
     * @param key  Redis 键
     * @param time 过期时间
     * @param unit 时间单位
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
