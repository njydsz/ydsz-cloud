package com.njydsz.common.lock.impl;

import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.ReentrantLock;

import com.njydsz.common.cache.YdszCache;
import com.njydsz.common.cache.api.Cache;
import com.njydsz.common.cache.builder.CacheType;
import com.njydsz.common.lock.core.DistributedLocker;

import lombok.extern.slf4j.Slf4j;

/**
 * 锁降级策略实现 - Redis 不可用时自动降级为本地 ReentrantLock
 *
 * <p>包装 DistributedLocker，当 Redis 不可用时降级为本地锁，保证服务可用性。
 *
 * <p><b>降级策略：</b>
 * <ul>
 *   <li>tryLock 时先尝试 Redis 锁，捕获异常后降级为本地锁</li>
 *   <li>unlock 时先尝试 Redis 释放，失败则释放本地锁</li>
 *   <li>通过配置开关 ydsz.lock.fallback-enabled 控制是否启用降级（默认 true）</li>
 * </ul>
 *
 * <p><b>自动恢复：</b>
 * <ul>
 *   <li>连续失败达到阈值后，全局标记 Redis 不可用，直接走本地锁</li>
 *   <li>Redis 恢复后，自动探测并切换回分布式锁模式</li>
 *   <li>通过 {@code ydsz.lock.fallback-recovery-threshold} 控制探测间隔</li>
 * </ul>
 *
 * <p><b>注意事项：</b>
 * <ul>
 *   <li>降级为本地锁后，分布式一致性无法保证，仅保证服务不因 Redis 故障而中断</li>
 *   <li>适用于对一致性要求不高、但可用性优先的场景</li>
 * </ul>
 *
 * @since 1.0.0
 */
@Slf4j
public class FallbackDistributedLock implements DistributedLocker {

    /**
     * 连续失败阈值，达到后全局标记 Redis 不可用
     */
    private static final int FAILURE_THRESHOLD = 3;

    /**
     * Redis 恢复探测间隔（次数），每 N 次尝试一次 Redis
     */
    private static final int RECOVERY_PROBE_INTERVAL = 10;

    /**
     * 被包装的分布式锁实例
     */
    private final DistributedLocker delegate;
    /**
     * 是否启用降级策略
     */
    private final boolean fallbackEnabled;

    /**
     * 本地锁映射表（使用 ydsz-common-cache，自动过期清理，防止内存泄漏）
     */
    private final Cache<String, ReentrantLock> localLocks = YdszCache.<String, ReentrantLock>newBuilder()
            .type(CacheType.STRIPED)
            .expireAfterWrite(30, TimeUnit.MINUTES)
            .maximumSize(10000)
            .build();

    /**
     * 降级状态标记（按 lockKey 分组）
     */
    private final ConcurrentHashMap<String, Boolean> degradedKeys = new ConcurrentHashMap<>();

    /**
     * 降级状态下的 lockValue 记录（按 lockKey 分组），用于 unlock 时校验
     */
    private final ConcurrentHashMap<String, String> degradedLockValues = new ConcurrentHashMap<>();

    /**
     * 全局 Redis 可用状态（静态字段，所有实例共享降级状态）
     */
    private static final AtomicBoolean redisAvailable = new AtomicBoolean(true);

    /**
     * 连续失败计数器（静态字段，所有实例共享降级状态）
     */
    private static final AtomicInteger consecutiveFailures = new AtomicInteger(0);

    /**
     * 降级状态下的尝试计数器（用于定期探测 Redis 是否恢复）
     */
    private final AtomicInteger degradedAttempts = new AtomicInteger(0);

    /**
     * 构造器
     *
     * @param delegate         被包装的分布式锁实例
     * @param fallbackEnabled  是否启用降级策略
     */
    public FallbackDistributedLock(DistributedLocker delegate, boolean fallbackEnabled) {
        this.delegate = delegate;
        this.fallbackEnabled = fallbackEnabled;
    }

    /**
     * 构造器（默认启用降级策略）
     *
     * @param delegate 被包装的分布式锁实例
     */
    public FallbackDistributedLock(DistributedLocker delegate) {
        this(delegate, true);
    }

    /**
     * 尝试获取锁（不等待），Redis 异常时降级为本地锁
     *
     * @param lockKey   锁的键
     * @param leaseTime 租约时间
     * @param timeUnit  时间单位
     * @return 锁值，获取成功返回非 null
     */
    @Override
    public String tryLock(String lockKey, long leaseTime, TimeUnit timeUnit) {
        if (!fallbackEnabled) {
            return delegate.tryLock(lockKey, leaseTime, timeUnit);
        }

        // Redis 不可用时，定期探测恢复
        if (!redisAvailable.get()) {
            if (shouldProbeRecovery()) {
                return probeRedisRecovery(lockKey, leaseTime, timeUnit);
            }
            degradedKeys.put(lockKey, Boolean.TRUE);
            return acquireLocalLock(lockKey);
        }

        try {
            String lockValue = delegate.tryLock(lockKey, leaseTime, timeUnit);
            if (lockValue != null) {
                resetFailureCounter();
                degradedKeys.remove(lockKey);
                return lockValue;
            }
            // Redis 锁获取失败（非异常），不降级
            return null;
        } catch (Exception e) {
            onRedisFailure(lockKey, e);
            return acquireLocalLock(lockKey);
        }
    }

    /**
     * 尝试获取锁（带等待时间），Redis 异常时降级为本地锁
     *
     * @param lockKey   锁的键
     * @param waitTime  最大等待时间
     * @param leaseTime 租约时间
     * @param timeUnit  时间单位
     * @return 锁值，获取成功返回非 null
     * @throws InterruptedException 等待过程中线程被中断
     */
    @Override
    public String tryLock(String lockKey, long waitTime, long leaseTime, TimeUnit timeUnit) throws InterruptedException {
        if (!fallbackEnabled) {
            return delegate.tryLock(lockKey, waitTime, leaseTime, timeUnit);
        }

        // Redis 不可用时，定期探测恢复
        if (!redisAvailable.get()) {
            if (shouldProbeRecovery()) {
                return probeRedisRecoveryWithWait(lockKey, waitTime, leaseTime, timeUnit);
            }
            degradedKeys.put(lockKey, Boolean.TRUE);
            return acquireLocalLock(lockKey, waitTime, timeUnit);
        }

        try {
            String lockValue = delegate.tryLock(lockKey, waitTime, leaseTime, timeUnit);
            if (lockValue != null) {
                resetFailureCounter();
                degradedKeys.remove(lockKey);
                return lockValue;
            }
            return null;
        } catch (Exception e) {
            onRedisFailure(lockKey, e);
            return acquireLocalLock(lockKey, waitTime, timeUnit);
        }
    }

    /**
     * 释放锁，Redis 异常时释放本地锁
     *
     * @param lockKey   锁的键
     * @param lockValue 锁的值
     * @return true-释放成功
     */
    @Override
    public boolean unlock(String lockKey, String lockValue) {
        if (!fallbackEnabled) {
            return delegate.unlock(lockKey, lockValue);
        }

        // 如果当前处于降级状态，释放本地锁
        if (degradedKeys.containsKey(lockKey)) {
            boolean released = releaseLocalLock(lockKey, lockValue);
            degradedKeys.remove(lockKey);
            return released;
        }

        try {
            boolean released = delegate.unlock(lockKey, lockValue);
            if (!released) {
                // Redis 释放失败，尝试释放本地锁
                log.warn("【锁降级】Redis 锁释放失败，尝试释放本地锁 | lockKey={}", lockKey);
                return releaseLocalLock(lockKey, lockValue);
            }
            resetFailureCounter();
            return true;
        } catch (Exception e) {
            onRedisFailure(lockKey, e);
            return releaseLocalLock(lockKey, lockValue);
        }
    }

    /**
     * 检查锁是否被持有，Redis 异常时检查本地锁状态
     *
     * @param lockKey 锁的键
     * @return true-锁被持有
     */
    @Override
    public boolean isLocked(String lockKey) {
        if (!fallbackEnabled) {
            return delegate.isLocked(lockKey);
        }

        try {
            return delegate.isLocked(lockKey);
        } catch (Exception e) {
            ReentrantLock localLock = localLocks.getIfPresent(lockKey);
            return localLock != null && localLock.isLocked();
        }
    }

    /**
     * 获取锁的剩余有效时间，Redis 异常时检查本地锁状态
     *
     * @param lockKey 锁的键
     * @return 剩余时间（毫秒），本地锁被持有时返回 Long.MAX_VALUE，否则返回 -1
     */
    @Override
    public long getRemainTime(String lockKey) {
        if (!fallbackEnabled) {
            return delegate.getRemainTime(lockKey);
        }

        try {
            return delegate.getRemainTime(lockKey);
        } catch (Exception e) {
            ReentrantLock localLock = localLocks.getIfPresent(lockKey);
            if (localLock != null && localLock.isLocked()) {
                return Long.MAX_VALUE;
            }
            return -1;
        }
    }

    /**
     * 获取本地锁（非阻塞）
     *
     * @param lockKey 锁的键
     * @return 锁值，获取成功返回 "local-lock:<UUID>"
     */
    private String acquireLocalLock(String lockKey) {
        ReentrantLock localLock = localLocks.get(lockKey, k -> new ReentrantLock());
        boolean acquired = localLock.tryLock();
        if (acquired) {
            String lockValue = "local-lock:" + UUID.randomUUID();
            degradedLockValues.put(lockKey, lockValue);
            log.info("【锁降级】本地锁获取成功 | lockKey={}", lockKey);
            return lockValue;
        }
        return null;
    }

    /**
     * 获取本地锁（阻塞等待）
     *
     * @param lockKey   锁的键
     * @param waitTime  最大等待时间
     * @param timeUnit  时间单位
     * @return 锁值，获取成功返回 "local-lock:<UUID>"
     * @throws InterruptedException 线程被中断
     */
    private String acquireLocalLock(String lockKey, long waitTime, TimeUnit timeUnit) throws InterruptedException {
        ReentrantLock localLock = localLocks.get(lockKey, k -> new ReentrantLock());
        boolean acquired = localLock.tryLock(waitTime, timeUnit);
        if (acquired) {
            String lockValue = "local-lock:" + UUID.randomUUID();
            degradedLockValues.put(lockKey, lockValue);
            log.info("【锁降级】本地锁获取成功 | lockKey={}", lockKey);
            return lockValue;
        }
        return null;
    }

    /**
     * 释放本地锁
     *
     * @param lockKey   锁的键
     * @param lockValue 锁的值（用于校验）
     * @return true-释放成功
     */
    private boolean releaseLocalLock(String lockKey, String lockValue) {
        ReentrantLock localLock = localLocks.getIfPresent(lockKey);
        if (localLock == null) {
            return false;
        }
        // 校验 lockValue 匹配
        String expectedValue = degradedLockValues.get(lockKey);
        if (expectedValue != null && !expectedValue.equals(lockValue)) {
            log.warn("【锁降级】本地锁 lockValue 不匹配，拒绝释放 | lockKey={}", lockKey);
            return false;
        }
        if (localLock.isHeldByCurrentThread()) {
            localLock.unlock();
            degradedLockValues.remove(lockKey);
            log.info("【锁降级】本地锁释放成功 | lockKey={}", lockKey);
            return true;
        }
        return false;
    }

    /**
     * 处理 Redis 失败事件
     */
    private void onRedisFailure(String lockKey, Exception e) {
        int failures = consecutiveFailures.incrementAndGet();
        if (failures >= FAILURE_THRESHOLD && redisAvailable.compareAndSet(true, false)) {
            log.warn("【锁降级】Redis 连续失败 {} 次，标记为不可用，全局切换到本地锁模式 | lastError={}",
                    failures, e.getMessage());
        }
        log.warn("【锁降级】Redis 锁操作异常，降级为本地锁 | lockKey={} | error={}", lockKey, e.getMessage());
        degradedKeys.put(lockKey, Boolean.TRUE);
    }

    /**
     * 重置失败计数器（Redis 操作成功时调用）
     */
    private void resetFailureCounter() {
        consecutiveFailures.set(0);
        if (!redisAvailable.get() && redisAvailable.compareAndSet(false, true)) {
            log.info("【锁降级】Redis 已恢复，切换回分布式锁模式");
        }
    }

    /**
     * 判断是否应该探测 Redis 恢复
     */
    private boolean shouldProbeRecovery() {
        int attempts = degradedAttempts.incrementAndGet();
        return attempts % RECOVERY_PROBE_INTERVAL == 0;
    }

    /**
     * 探测 Redis 是否恢复（无等待版本）
     */
    private String probeRedisRecovery(String lockKey, long leaseTime, TimeUnit timeUnit) {
        try {
            String lockValue = delegate.tryLock(lockKey, leaseTime, timeUnit);
            if (lockValue != null) {
                redisAvailable.set(true);
                consecutiveFailures.set(0);
                log.info("【锁降级】Redis 探测成功，已恢复分布式锁模式 | lockKey={}", lockKey);
                return lockValue;
            }
            return null;
        } catch (Exception e) {
            log.debug("【锁降级】Redis 探测失败，继续使用本地锁 | error={}", e.getMessage());
            return acquireLocalLock(lockKey);
        }
    }

    /**
     * 探测 Redis 是否恢复（带等待版本）
     */
    private String probeRedisRecoveryWithWait(String lockKey, long waitTime, long leaseTime, TimeUnit timeUnit) throws InterruptedException {
        try {
            String lockValue = delegate.tryLock(lockKey, waitTime, leaseTime, timeUnit);
            if (lockValue != null) {
                redisAvailable.set(true);
                consecutiveFailures.set(0);
                log.info("【锁降级】Redis 探测成功，已恢复分布式锁模式 | lockKey={}", lockKey);
                return lockValue;
            }
            return null;
        } catch (Exception e) {
            log.debug("【锁降级】Redis 探测失败，继续使用本地锁 | error={}", e.getMessage());
            return acquireLocalLock(lockKey, waitTime, timeUnit);
        }
    }

    /**
     * 检查指定锁是否处于降级状态
     *
     * @param lockKey 锁的键
     * @return true-处于降级状态
     */
    public boolean isDegraded(String lockKey) {
        return degradedKeys.containsKey(lockKey);
    }

    /**
     * 获取被包装的原始分布式锁
     *
     * @return 原始分布式锁实例
     */
    public DistributedLocker getDelegate() {
        return delegate;
    }

    /**
     * 检查当前 Redis 是否可用
     *
     * @return true-Redis 可用
     */
    public boolean isRedisAvailable() {
        return redisAvailable.get();
    }

    /**
     * 获取连续失败次数
     *
     * @return 连续失败次数
     */
    public int getConsecutiveFailures() {
        return consecutiveFailures.get();
    }
}
