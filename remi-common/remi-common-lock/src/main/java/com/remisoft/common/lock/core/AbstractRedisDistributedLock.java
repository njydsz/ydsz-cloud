package com.remisoft.common.lock.core;

import java.util.Collections;
import java.util.concurrent.TimeUnit;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;

import com.remisoft.common.cache.RemiCache;
import com.remisoft.common.cache.api.Cache;
import com.remisoft.common.cache.builder.CacheType;
import com.remisoft.common.lock.annotation.LockType;
import com.remisoft.common.lock.metrics.LockMetrics;
import com.remisoft.common.lock.scheduler.LockWatchDog;

import lombok.extern.slf4j.Slf4j;
import com.remisoft.common.util.id.IdGenerator;

/**
 * 抽象 Redis 分布式锁基类
 *
 * <p>提供分布式锁的公共能力：
 * <ul>
 *   <li>客户端标识生成与管理（基于 Redis 存储，线程池安全）</li>
 *   <li>锁超时时间记录与管理</li>
 *   <li>WatchDog 自动续期机制集成</li>
 *   <li>等待重试退避策略</li>
 * </ul>
 *
 * <p><b>设计要点：</b>
 * 客户端标识使用 Redis Hash 存储（lock:client:registry）作为持久存储，Caffeine 缓存作为线程级前置缓存，
 * 确保线程池环境下 clientId 不会因线程复用而混乱。
 * <p><b>内存安全：</b>使用 Caffeine 缓存替代 ThreadLocal，通过 TTL（30 分钟）和最大容量（10000）自动清理，
 * 彻底避免线程池复用场景下的 ThreadLocal 内存泄漏。
 *
 * @author remi-team
 * @since 1.0.0
 */
@Slf4j
public abstract class AbstractRedisDistributedLock implements DistributedLocker {

    /**
     * 客户端标识注册表 Redis Key 前缀
     */
    private static final String CLIENT_REGISTRY_KEY = "lock:client:registry";

    /**
     * 注册表 Lua 脚本 - 获取或创建 clientId
     * 如果已存在则返回现有值，否则生成新值
     */
    private static final String GET_OR_CREATE_CLIENT_LUA =
            "local key = 'lock:client:registry' " +
            "local field = ARGV[1] " +
            "local ttlMs = ARGV[2] " +
            "local existing = redis.call('HGET', key, field) " +
            "if existing then " +
            "    redis.call('PEXPIRE', key, ttlMs) " +
            "    return existing " +
            "else " +
            "    local newId = ARGV[3] " +
            "    redis.call('HSET', key, field, newId) " +
            "    redis.call('PEXPIRE', key, ttlMs) " +
            "    return newId " +
            "end";

    /**
     * 注册表 Lua 脚本 - 删除 clientId
     */
    private static final String REMOVE_CLIENT_LUA =
            "redis.call('HDEL', 'lock:client:registry', ARGV[1]) " +
            "if redis.call('HLEN', 'lock:client:registry') == 0 then " +
            "    redis.call('DEL', 'lock:client:registry') " +
            "end " +
            "return 1";

    /**
     * 释放锁 Lua 脚本
     */
    private static final String RELEASE_LOCK_LUA_SCRIPT =
            "if redis.call('get', KEYS[1]) == ARGV[1] then " +
            "    return redis.call('del', KEYS[1]) " +
            "else " +
            "    return 0 " +
            "end";

    /**
     * Redis 操作模板
     */
    protected final StringRedisTemplate stringRedisTemplate;
    /**
     * 锁续期看门狗
     */
    private LockWatchDog lockWatchDog;
    /**
     * 锁指标收集器
     */
    private LockMetrics lockMetrics;

    /**
     * 本地缓存 clientId，作为 Redis 注册表的前置缓存
     * <p>使用 remi-common-cache 替代 ThreadLocal，通过 TTL 和最大容量自动清理，
     * 彻底避免线程池复用场景下的内存泄漏。
     * <p>缓存键格式：{@code threadId:lockKey}，确保不同线程的 clientId 互不干扰。
     */
    private final Cache<String, String> clientIdCache = RemiCache.<String, String>newBuilder()
            .type(CacheType.STRIPED)
            .expireAfterWrite(30, TimeUnit.MINUTES)
            .maximumSize(10_000)
            .build();

    /**
     * 锁租约时间缓存，key 为 {@code threadId:lockKey}，value 为租约时间（毫秒）
     * <p>使用 remi-common-cache 替代 ThreadLocal，通过 TTL 和最大容量自动清理。
     */
    private final Cache<String, Long> leaseTimeCache = RemiCache.<String, Long>newBuilder()
            .type(CacheType.STRIPED)
            .expireAfterWrite(30, TimeUnit.MINUTES)
            .maximumSize(10_000)
            .build();

    private final DefaultRedisScript<Long> releaseLockScript;
    private final DefaultRedisScript<String> getOrCreateClientScript;
    private final DefaultRedisScript<Long> removeClientScript;

    /**
     * 锁键命名空间前缀，用于多应用共享 Redis 时的隔离
     */
    private final String keyNamespace;

    protected AbstractRedisDistributedLock(StringRedisTemplate stringRedisTemplate) {
        this(stringRedisTemplate, null);
    }

    protected AbstractRedisDistributedLock(StringRedisTemplate stringRedisTemplate, String namespace) {
        this.stringRedisTemplate = stringRedisTemplate;
        this.keyNamespace = (namespace != null && !namespace.isEmpty()) ? namespace : null;
        this.releaseLockScript = new DefaultRedisScript<>(RELEASE_LOCK_LUA_SCRIPT, Long.class);
        this.getOrCreateClientScript = new DefaultRedisScript<>(GET_OR_CREATE_CLIENT_LUA, String.class);
        this.removeClientScript = new DefaultRedisScript<>(REMOVE_CLIENT_LUA, Long.class);
    }

    /**
     * 对锁键添加应用命名空间前缀
     *
     * <p>当配置了 {@code remi.lock.namespace} 时，锁键自动变为：
     * {@code ${namespace}:lock:${userKey}}，用于多应用共享 Redis 时的隔离。</p>
     *
     * @param userKey 用户传入的锁键
     * @return 带命名空间前缀的锁键
     */
    protected String buildNamespacedKey(String userKey) {
        if (keyNamespace == null || keyNamespace.isEmpty()) {
            return userKey;
        }
        return keyNamespace + ":lock:" + userKey;
    }

    /**
     * 设置锁续期看门狗
     *
     * @param lockWatchDog 看门狗实例
     */
    public void setLockWatchDog(LockWatchDog lockWatchDog) {
        this.lockWatchDog = lockWatchDog;
    }

    /**
     * 设置锁指标收集器
     *
     * @param lockMetrics 指标收集器实例
     */
    public void setLockMetrics(LockMetrics lockMetrics) {
        this.lockMetrics = lockMetrics;
    }

    /**
     * 获取锁续期看门狗
     *
     * @return 看门狗实例，未设置时返回 null
     */
    public LockWatchDog getLockWatchDog() {
        return lockWatchDog;
    }

    /**
     * 获取或生成客户端标识
     *
     * <p>优先从本地 Caffeine 缓存获取，未命中则从 Redis 注册表获取或创建。
     * 生成规则: UUID + threadId，确保全局唯一性。
     *
     * @param lockKey 锁的键
     * @return 客户端标识
     */
    protected String getClientId(String lockKey) {
        String cacheKey = buildCacheKey(lockKey);
        String cached = clientIdCache.getIfPresent(cacheKey);
        if (cached != null) {
            return cached;
        }

        String fieldKey = buildRegistryField(lockKey);
        String newClientId = IdGenerator.nextIdStr() + ":" + Thread.currentThread().threadId();
        long ttlMs = 3600000L;

        try {
            String existingId = stringRedisTemplate.execute(
                    getOrCreateClientScript,
                    Collections.singletonList(CLIENT_REGISTRY_KEY),
                    fieldKey,
                    String.valueOf(ttlMs),
                    newClientId
            );

            String resolvedId = existingId != null ? existingId : newClientId;
            clientIdCache.put(cacheKey, resolvedId);

            return resolvedId;
        } catch (Exception e) {
            log.warn("【分布式锁】Redis 注册表获取 clientId 失败，使用本地生成 | lockKey={} | error={}", lockKey, e.getMessage());
            clientIdCache.put(cacheKey, newClientId);
            return newClientId;
        }
    }

    /**
     * 清理本地缓存的 clientId
     *
     * @param lockKey 锁的键
     */
    protected void clearClientId(String lockKey) {
        String cacheKey = buildCacheKey(lockKey);
        clientIdCache.invalidate(cacheKey);

        String fieldKey = buildRegistryField(lockKey);
        try {
            stringRedisTemplate.execute(
                    removeClientScript,
                    Collections.singletonList(CLIENT_REGISTRY_KEY),
                    fieldKey
            );
        } catch (Exception e) {
            log.warn("【分布式锁】清理 Redis 注册表 clientId 失败 | lockKey={} | error={}", lockKey, e.getMessage());
        }
    }

    /**
     * 记录锁的租约时间
     *
     * @param lockKey    锁的键
     * @param leaseTimeMs 租约时间（毫秒）
     */
    protected void recordLeaseTime(String lockKey, long leaseTimeMs) {
        leaseTimeCache.put(buildCacheKey(lockKey), leaseTimeMs);
    }

    /**
     * 获取记录的租约时间
     *
     * @param lockKey 锁的键
     * @return 租约时间（毫秒），未记录返回 null
     */
    protected Long getLeaseTime(String lockKey) {
        return leaseTimeCache.getIfPresent(buildCacheKey(lockKey));
    }

    /**
     * 清理记录的租约时间
     *
     * @param lockKey 锁的键
     */
    protected void clearLeaseTime(String lockKey) {
        leaseTimeCache.invalidate(buildCacheKey(lockKey));
    }

    /**
     * 检查是否已有 clientId
     *
     * @param lockKey 锁的键
     * @return true-已存在
     */
    protected boolean hasClientId(String lockKey) {
        return clientIdCache.getIfPresent(buildCacheKey(lockKey)) != null;
    }

    /**
     * 启动 WatchDog 自动续期
     *
     * @param lockKey     锁的键
     * @param clientId    客户端标识
     * @param leaseTimeMs 租约时间（毫秒）
     */
    protected void startWatchDog(String lockKey, String clientId, long leaseTimeMs) {
        startWatchDog(lockKey, clientId, leaseTimeMs, LockType.REENTRANT);
    }

    /**
     * 启动 WatchDog 自动续期（带锁类型）
     *
     * <p>锁类型决定续期时使用的 Lua 脚本，避免看门狗盲试多个脚本造成额外 Redis 调用。
     *
     * @param lockKey     锁的键
     * @param clientId    客户端标识
     * @param leaseTimeMs 租约时间（毫秒）
     * @param lockType    锁类型
     */
    protected void startWatchDog(String lockKey, String clientId, long leaseTimeMs, LockType lockType) {
        if (leaseTimeMs <= 0) {
            return;
        }
        if (lockWatchDog != null) {
            lockWatchDog.startWatch(lockKey, clientId, leaseTimeMs, lockType);
        }
    }

    /**
     * 构建注册表字段键
     *
     * @param lockKey 锁的键
     * @return 注册表字段键
     */
    private String buildRegistryField(String lockKey) {
        return Thread.currentThread().threadId() + ":" + lockKey;
    }

    /**
     * 构建本地缓存键
     * <p>使用 threadId 前缀确保不同线程的缓存条目互不干扰，
     * 同时避免使用 ThreadLocal 导致的内存泄漏。
     *
     * @param lockKey 锁的键
     * @return 缓存键
     */
    private String buildCacheKey(String lockKey) {
        return Thread.currentThread().threadId() + ":" + lockKey;
    }

    /**
     * 释放锁
     *
     * <p>通过 Lua 脚本原子性释放锁，释放成功后停止看门狗续期并减少活跃锁计数，
     * 无论释放是否成功都会清理本地缓存，防止线程池复用场景下的泄漏。
     *
     * @param lockKey   锁的键
     * @param lockValue 锁的值（客户端标识）
     * @return true-释放成功，false-释放失败或锁键为空
     */
    @Override
    public boolean unlock(String lockKey, String lockValue) {
        if (lockKey == null || lockKey.isEmpty()) {
            log.warn("【分布式锁】解锁失败 | 锁键为空");
            return false;
        }
        try {
            boolean released = doReleaseLock(lockKey, lockValue);
            if (released) {
                if (lockWatchDog != null) {
                    lockWatchDog.stopWatch(lockKey);
                }
                // 减少活跃锁计数
                if (lockMetrics != null) {
                    lockMetrics.decrementActiveLocks();
                }
            }
            return released;
        } catch (Exception e) {
            log.error("【分布式锁】解锁异常 | lockKey={} | error={}", lockKey, e.getMessage(), e);
            return false;
        } finally {
            // 无论解锁是否成功，始终清理 ThreadLocal，防止线程池复用场景下的泄漏
            clearClientId(lockKey);
            clearLeaseTime(lockKey);
        }
    }

    /**
     * 检查锁是否被持有
     *
     * @param lockKey 锁的键
     * @return true-锁已被持有，false-锁未被持有或锁键为空
     */
    @Override
    public boolean isLocked(String lockKey) {
        if (lockKey == null || lockKey.isEmpty()) {
            return false;
        }
        try {
            return doIsLocked(lockKey);
        } catch (Exception e) {
            log.error("【分布式锁】检查锁状态异常 | lockKey={} | error={}", lockKey, e.getMessage(), e);
            return false;
        }
    }

    /**
     * 获取锁的剩余有效时间
     *
     * @param lockKey 锁的键
     * @return 剩余时间（毫秒），锁键为空或异常时返回 -2
     */
    @Override
    public long getRemainTime(String lockKey) {
        if (lockKey == null || lockKey.isEmpty()) {
            return -2;
        }
        try {
            return doGetRemainTime(lockKey);
        } catch (Exception e) {
            log.error("【分布式锁】获取剩余时间异常 | lockKey={} | error={}", lockKey, e.getMessage(), e);
            return -2;
        }
    }

    /**
     * 执行释放锁脚本
     *
     * @param lockKey  锁的键
     * @param clientId 客户端标识
     * @return true-释放成功
     */
    protected boolean executeReleaseScript(String lockKey, String clientId) {
        try {
            Long result = stringRedisTemplate.execute(
                    releaseLockScript,
                    Collections.singletonList(lockKey),
                    clientId
            );
            return Long.valueOf(1L).equals(result);
        } catch (Exception e) {
            log.error("【分布式锁】执行释放锁脚本异常 | lockKey={} | error={}", lockKey, e.getMessage(), e);
            return false;
        }
    }

    /**
     * 最小退避等待时间（毫秒）
     */
    private static final long MIN_BACKOFF_MILLIS = 10;
    /**
     * 最大退避等待时间（毫秒）
     */
    private static final long MAX_BACKOFF_MILLIS = 200;

    /**
     * 带等待重试的锁获取
     *
     * <p>使用指数退避策略，初始等待 10ms，最大 200ms
     *
     * @param lockKey   锁的键
     * @param waitTime  等待时间
     * @param leaseTime 租约时间
     * @param timeUnit  时间单位
     * @return 锁值，获取成功返回非 null
     * @throws InterruptedException 线程中断异常
     */
    protected String tryLockWithWait(String lockKey, long waitTime, long leaseTime, TimeUnit timeUnit) throws InterruptedException {
        long waitNanos = timeUnit.toNanos(waitTime);
        long startTime = System.nanoTime();
        long currentBackoff = MIN_BACKOFF_MILLIS;
        String lockValue = null;
        while (true) {
            lockValue = tryLock(lockKey, leaseTime, timeUnit);
            if (lockValue != null) {
                break;
            }
            // 记录锁竞争
            if (lockMetrics != null) {
                lockMetrics.recordCompetition(getLockType().name(), lockKey);
            }
            long elapsed = System.nanoTime() - startTime;
            if (elapsed >= waitNanos) {
                // 记录锁超时
                if (lockMetrics != null) {
                    lockMetrics.recordLockTimeout(getLockType().name());
                }
                return null;
            }
            long remainingWait = waitNanos - elapsed;
            long sleepMillis = Math.min(TimeUnit.NANOSECONDS.toMillis(remainingWait), currentBackoff);
            if (sleepMillis > 0) {
                Thread.sleep(sleepMillis);
            }
            currentBackoff = Math.min(currentBackoff * 2, MAX_BACKOFF_MILLIS);
            if (Thread.currentThread().isInterrupted()) {
                throw new InterruptedException();
            }
        }
        // 锁获取成功，记录等待时间和活跃锁
        long waitTimeMillis = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startTime);
        if (lockMetrics != null) {
            lockMetrics.recordWaitDuration(waitTimeMillis, getLockType().name());
            lockMetrics.incrementActiveLocks();
        }
        return lockValue;
    }

    /**
     * 返回当前锁实现对应的锁类型，用于指标采集打标
     *
     * @return 锁类型
     */
    protected abstract LockType getLockType();

    /**
     * 获取锁的底层实现
     *
     * @param lockKey   锁的键
     * @param clientId  客户端标识
     * @param leaseTime 租约时间
     * @param timeUnit  时间单位
     * @return 锁值，获取成功返回非 null
     */
    protected abstract String doAcquireLock(String lockKey, String clientId, long leaseTime, TimeUnit timeUnit);

    /**
     * 释放锁的底层实现
     *
     * @param lockKey  锁的键
     * @param clientId 客户端标识
     * @return true-释放成功
     */
    protected abstract boolean doReleaseLock(String lockKey, String clientId);

    /**
     * 检查锁状态的底层实现
     *
     * @param lockKey 锁的键
     * @return true-已锁定
     */
    protected abstract boolean doIsLocked(String lockKey);

    /**
     * 获取剩余时间的底层实现
     *
     * @param lockKey 锁的键
     * @return 剩余时间（毫秒），-2 表示异常
     */
    protected abstract long doGetRemainTime(String lockKey);
}
