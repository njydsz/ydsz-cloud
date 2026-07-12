package com.njydsz.pmis.common.redis.lock;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Tags;
import io.micrometer.core.instrument.Timer;
import org.redisson.api.RLock;
import org.redisson.api.RReadWriteLock;
import org.redisson.api.RSemaphore;
import org.redisson.api.RedissonClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

/**
 * 分布式锁增强组件 —— 基于 Redisson 的多锁类型封装。
 *
 * <p>对标互联网大厂标准，支持：
 * <ul>
 *   <li>可重入锁（Reentrant Lock）</li>
 *   <li>公平锁（Fair Lock）</li>
 *   <li>读写锁（Read-Write Lock）</li>
 *   <li>信号量（Semaphore）—— 限流/资源池</li>
 *   <li>多锁（Multi-Lock）—— 同时锁定多个 key</li>
 *   <li>WatchDog 自动续期（leaseTime=-1，后台每 1/3 leaseTime 续期）</li>
 *   <li>获取失败降级（直接执行或快速失败）</li>
 *   <li>Micrometer 指标监控（获取耗时/成功率/持有时间）</li>
 * </ul>
 *
 * @author njydsz
 * @since 1.0.0
 */
@Component
public class DistributedLockSupport {

    private static final Logger log = LoggerFactory.getLogger(DistributedLockSupport.class);

    /** 默认锁等待时间 */
    private static final long DEFAULT_WAIT_SECONDS = 10;

    /** 默认锁持有时间（-1 表示启用 WatchDog 自动续期） */
    private static final long DEFAULT_LEASE_SECONDS = -1;

    /** 指标名前缀 */
    private static final String METRIC_PREFIX = "pmis_distributed_lock";

    private final RedissonClient redissonClient;
    private final MeterRegistry meterRegistry;

    public DistributedLockSupport(RedissonClient redissonClient,
                                  @Autowired(required = false) MeterRegistry meterRegistry) {
        this.redissonClient = redissonClient;
        this.meterRegistry = meterRegistry;
    }

    // ==================== 可重入锁 ====================

    /**
     * 可重入锁执行（WatchDog 自动续期）。
     *
     * @param lockKey  锁键
     * @param supplier 业务逻辑
     * @param <T>      返回类型
     * @return 业务逻辑返回值
     */
    public <T> T executeWithReentrantLock(String lockKey, Supplier<T> supplier) {
        return executeWithReentrantLock(lockKey, DEFAULT_WAIT_SECONDS, DEFAULT_LEASE_SECONDS, supplier);
    }

    /**
     * 可重入锁执行（自定义参数）。
     *
     * @param lockKey       锁键
     * @param waitSeconds   等待时间（秒）
     * @param leaseSeconds  持有时间（秒），-1 表示 WatchDog 自动续期
     * @param supplier      业务逻辑
     * @param <T>           返回类型
     * @return 业务逻辑返回值
     * @throws RuntimeException 如果获取锁失败
     */
    public <T> T executeWithReentrantLock(String lockKey, long waitSeconds, long leaseSeconds, Supplier<T> supplier) {
        RLock lock = redissonClient.getLock(lockKey);
        return doExecute("reentrant", lockKey, lock, waitSeconds, leaseSeconds, supplier);
    }

    // ==================== 公平锁 ====================

    /**
     * 公平锁执行 —— 按请求顺序获取锁。
     *
     * @param lockKey  锁键
     * @param supplier 业务逻辑
     * @param <T>      返回类型
     * @return 业务逻辑返回值
     */
    public <T> T executeWithFairLock(String lockKey, Supplier<T> supplier) {
        return executeWithFairLock(lockKey, DEFAULT_WAIT_SECONDS, DEFAULT_LEASE_SECONDS, supplier);
    }

    /**
     * 公平锁执行（自定义参数）。
     */
    public <T> T executeWithFairLock(String lockKey, long waitSeconds, long leaseSeconds, Supplier<T> supplier) {
        RLock lock = redissonClient.getFairLock(lockKey);
        return doExecute("fair", lockKey, lock, waitSeconds, leaseSeconds, supplier);
    }

    // ==================== 读写锁 ====================

    /**
     * 读锁执行 —— 允许多线程并发读。
     */
    public <T> T executeWithReadLock(String lockKey, Supplier<T> supplier) {
        return executeWithReadLock(lockKey, DEFAULT_WAIT_SECONDS, DEFAULT_LEASE_SECONDS, supplier);
    }

    /**
     * 读锁执行（自定义参数）。
     */
    public <T> T executeWithReadLock(String lockKey, long waitSeconds, long leaseSeconds, Supplier<T> supplier) {
        RReadWriteLock rwLock = redissonClient.getReadWriteLock(lockKey);
        RLock readLock = rwLock.readLock();
        return doExecute("read", lockKey, readLock, waitSeconds, leaseSeconds, supplier);
    }

    /**
     * 写锁执行 —— 独占写。
     */
    public <T> T executeWithWriteLock(String lockKey, Supplier<T> supplier) {
        return executeWithWriteLock(lockKey, DEFAULT_WAIT_SECONDS, DEFAULT_LEASE_SECONDS, supplier);
    }

    /**
     * 写锁执行（自定义参数）。
     */
    public <T> T executeWithWriteLock(String lockKey, long waitSeconds, long leaseSeconds, Supplier<T> supplier) {
        RReadWriteLock rwLock = redissonClient.getReadWriteLock(lockKey);
        RLock writeLock = rwLock.writeLock();
        return doExecute("write", lockKey, writeLock, waitSeconds, leaseSeconds, supplier);
    }

    // ==================== 多锁 ====================

    /**
     * 多锁执行 —— 同时锁定多个 key（全部成功才执行）。
     *
     * @param lockKeys 锁键列表
     * @param supplier 业务逻辑
     * @param <T>      返回类型
     * @return 业务逻辑返回值
     */
    public <T> T executeWithMultiLock(String[] lockKeys, Supplier<T> supplier) {
        return executeWithMultiLock(lockKeys, DEFAULT_WAIT_SECONDS, DEFAULT_LEASE_SECONDS, supplier);
    }

    /**
     * 多锁执行（自定义参数）。
     *
     * @param lockKeys      锁键列表
     * @param waitSeconds   等待时间
     * @param leaseSeconds  持有时间
     * @param supplier      业务逻辑
     * @param <T>           返回类型
     * @return 业务逻辑返回值
     */
    public <T> T executeWithMultiLock(String[] lockKeys, long waitSeconds, long leaseSeconds, Supplier<T> supplier) {
        RLock[] locks = new RLock[lockKeys.length];
        for (int i = 0; i < lockKeys.length; i++) {
            locks[i] = redissonClient.getLock(lockKeys[i]);
        }
        RLock multiLock = redissonClient.getMultiLock(locks);
        return doExecute("multi", String.join(",", lockKeys), multiLock, waitSeconds, leaseSeconds, supplier);
    }

    // ==================== 信号量 ====================

    /**
     * 信号量执行 —— 限制并发数。
     *
     * <p>适用于限流场景：如限制某个接口最大并发 10 个请求。
     * 信号量在 Redis 中初始化后，每次获取减少一个许可，释放时增加一个。
     *
     * @param semKey       信号量键
     * @param permits      最大许可数（首次使用时初始化）
     * @param supplier     业务逻辑
     * @param <T>          返回类型
     * @return 业务逻辑返回值
     * @throws RuntimeException 获取许可失败
     */
    public <T> T executeWithSemaphore(String semKey, int permits, Supplier<T> supplier) {
        return executeWithSemaphore(semKey, permits, DEFAULT_WAIT_SECONDS, supplier);
    }

    /**
     * 信号量执行（自定义等待时间）。
     *
     * @param semKey       信号量键
     * @param permits      最大许可数
     * @param waitSeconds  等待时间
     * @param supplier     业务逻辑
     * @param <T>          返回类型
     * @return 业务逻辑返回值
     */
    public <T> T executeWithSemaphore(String semKey, int permits, long waitSeconds, Supplier<T> supplier) {
        RSemaphore semaphore = redissonClient.getSemaphore(semKey);
        // 尝试设置许可数（如果尚未初始化）
        semaphore.trySetPermits(permits);

        boolean acquired = false;
        long startNanos = System.nanoTime();
        try {
            acquired = semaphore.tryAcquire(waitSeconds, TimeUnit.SECONDS);
            if (!acquired) {
                recordMetric("semaphore", semKey, false, System.nanoTime() - startNanos);
                throw new IllegalStateException("Failed to acquire semaphore permit: " + semKey +
                        " within " + waitSeconds + "s");
            }
            recordMetric("semaphore", semKey, true, System.nanoTime() - startNanos);
            return supplier.get();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            recordMetric("semaphore", semKey, false, System.nanoTime() - startNanos);
            throw new IllegalStateException("Interrupted while acquiring semaphore: " + semKey, e);
        } finally {
            if (acquired) {
                semaphore.release();
            }
        }
    }

    /**
     * 尝试获取信号量许可（非阻塞）。
     *
     * @param semKey 信号量键
     * @return true 表示获取成功
     */
    public boolean tryAcquireSemaphore(String semKey) {
        RSemaphore semaphore = redissonClient.getSemaphore(semKey);
        return semaphore.tryAcquire();
    }

    /**
     * 释放信号量许可。
     *
     * @param semKey 信号量键
     */
    public void releaseSemaphore(String semKey) {
        RSemaphore semaphore = redissonClient.getSemaphore(semKey);
        semaphore.release();
    }

    // ==================== 降级执行 ====================

    /**
     * 尝试获取锁，失败时执行降级逻辑。
     *
     * @param lockKey       锁键
     * @param waitSeconds   等待时间
     * @param leaseSeconds  持有时间
     * @param supplier      正常逻辑
     * @param fallback      降级逻辑
     * @param <T>           返回类型
     * @return 正常或降级返回值
     */
    public <T> T executeWithFallback(String lockKey, long waitSeconds, long leaseSeconds,
                                      Supplier<T> supplier, Supplier<T> fallback) {
        RLock lock = redissonClient.getLock(lockKey);
        long startNanos = System.nanoTime();
        try {
            boolean acquired = lock.tryLock(waitSeconds, leaseSeconds, TimeUnit.SECONDS);
            if (acquired) {
                recordMetric("reentrant", lockKey, true, System.nanoTime() - startNanos);
                try {
                    return supplier.get();
                } finally {
                    if (lock.isHeldByCurrentThread()) {
                        lock.unlock();
                    }
                }
            } else {
                recordMetric("reentrant", lockKey, false, System.nanoTime() - startNanos);
                log.warn("Lock acquisition failed, executing fallback: key={}", lockKey);
                return fallback.get();
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            recordMetric("reentrant", lockKey, false, System.nanoTime() - startNanos);
            log.warn("Lock acquisition interrupted, executing fallback: key={}", lockKey);
            return fallback.get();
        }
    }

    // ==================== 内部方法 ====================

    /**
     * 执行锁操作的通用模板（含指标记录）。
     */
    private <T> T doExecute(String lockType, String lockKey, RLock lock,
                            long waitSeconds, long leaseSeconds, Supplier<T> supplier) {
        boolean acquired = false;
        long startNanos = System.nanoTime();
        try {
            acquired = lock.tryLock(waitSeconds, leaseSeconds, TimeUnit.SECONDS);
            if (!acquired) {
                recordMetric(lockType, lockKey, false, System.nanoTime() - startNanos);
                throw new IllegalStateException("Failed to acquire " + lockType + " lock: " + lockKey +
                        " within " + waitSeconds + "s");
            }
            recordMetric(lockType, lockKey, true, System.nanoTime() - startNanos);
            return supplier.get();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            recordMetric(lockType, lockKey, false, System.nanoTime() - startNanos);
            throw new IllegalStateException("Interrupted while acquiring " + lockType + " lock: " + lockKey, e);
        } finally {
            if (acquired && lock.isHeldByCurrentThread()) {
                lock.unlock();
            }
        }
    }

    /**
     * 记录锁指标到 Micrometer。
     *
     * @param lockType  锁类型
     * @param lockKey   锁键
     * @param success   是否成功
     * @param elapsedNanos 耗时（纳秒）
     */
    private void recordMetric(String lockType, String lockKey, boolean success, long elapsedNanos) {
        if (meterRegistry == null) {
            return;
        }
        Tags tags = Tags.of("type", lockType, "result", success ? "success" : "failure");
        Timer.builder(METRIC_PREFIX + "_acquire_seconds")
                .description("分布式锁获取耗时")
                .tags(tags)
                .publishPercentileHistogram()
                .register(meterRegistry)
                .record(elapsedNanos, TimeUnit.NANOSECONDS);
    }
}
