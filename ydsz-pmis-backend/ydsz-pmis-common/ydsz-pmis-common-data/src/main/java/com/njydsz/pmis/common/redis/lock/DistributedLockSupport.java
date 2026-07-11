package com.njydsz.pmis.common.redis.lock;

import org.redisson.api.RLock;
import org.redisson.api.RReadWriteLock;
import org.redisson.api.RedissonClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

/**
 * 分布式锁增强组件 —— 基于 Redisson 的多锁类型封装。
 * <p>
 * 对标 remi-comm DistributedLockSupport，支持：
 * <ul>
 *   <li>可重入锁（Reentrant Lock）</li>
 *   <li>公平锁（Fair Lock）</li>
 *   <li>读写锁（Read-Write Lock）</li>
 *   <li>WatchDog 自动续期（默认 30s 锁，后台每 10s 续期）</li>
 *   <li>获取失败降级（直接执行或快速失败）</li>
 * </ul>
 * </p>
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

    private final RedissonClient redissonClient;

    public DistributedLockSupport(RedissonClient redissonClient) {
        this.redissonClient = redissonClient;
    }

    /**
     * 可重入锁执行。
     * <p>
     * 使用 WatchDog 自动续期（leaseTime=-1），适用于执行时间不确定的场景。
     * </p>
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
        boolean acquired = false;
        try {
            acquired = lock.tryLock(waitSeconds, leaseSeconds, TimeUnit.SECONDS);
            if (!acquired) {
                throw new IllegalStateException("Failed to acquire reentrant lock: " + lockKey + " within " + waitSeconds + "s");
            }
            return supplier.get();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted while acquiring lock: " + lockKey, e);
        } finally {
            if (acquired && lock.isHeldByCurrentThread()) {
                lock.unlock();
            }
        }
    }

    /**
     * 公平锁执行 —— 按请求顺序获取锁。
     *
     * @param lockKey  锁键
     * @param supplier 业务逻辑
     * @param <T>      返回类型
     * @return 业务逻辑返回值
     */
    public <T> T executeWithFairLock(String lockKey, Supplier<T> supplier) {
        RLock lock = redissonClient.getFairLock(lockKey);
        boolean acquired = false;
        try {
            acquired = lock.tryLock(DEFAULT_WAIT_SECONDS, DEFAULT_LEASE_SECONDS, TimeUnit.SECONDS);
            if (!acquired) {
                throw new IllegalStateException("Failed to acquire fair lock: " + lockKey);
            }
            return supplier.get();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted while acquiring fair lock: " + lockKey, e);
        } finally {
            if (acquired && lock.isHeldByCurrentThread()) {
                lock.unlock();
            }
        }
    }

    /**
     * 读锁执行 —— 允许多线程并发读。
     *
     * @param lockKey  锁键
     * @param supplier 业务逻辑
     * @param <T>      返回类型
     * @return 业务逻辑返回值
     */
    public <T> T executeWithReadLock(String lockKey, Supplier<T> supplier) {
        RReadWriteLock rwLock = redissonClient.getReadWriteLock(lockKey);
        RLock readLock = rwLock.readLock();
        boolean acquired = false;
        try {
            acquired = readLock.tryLock(DEFAULT_WAIT_SECONDS, DEFAULT_LEASE_SECONDS, TimeUnit.SECONDS);
            if (!acquired) {
                throw new IllegalStateException("Failed to acquire read lock: " + lockKey);
            }
            return supplier.get();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted while acquiring read lock: " + lockKey, e);
        } finally {
            if (acquired && readLock.isHeldByCurrentThread()) {
                readLock.unlock();
            }
        }
    }

    /**
     * 写锁执行 —— 独占写。
     *
     * @param lockKey  锁键
     * @param supplier 业务逻辑
     * @param <T>      返回类型
     * @return 业务逻辑返回值
     */
    public <T> T executeWithWriteLock(String lockKey, Supplier<T> supplier) {
        RReadWriteLock rwLock = redissonClient.getReadWriteLock(lockKey);
        RLock writeLock = rwLock.writeLock();
        boolean acquired = false;
        try {
            acquired = writeLock.tryLock(DEFAULT_WAIT_SECONDS, DEFAULT_LEASE_SECONDS, TimeUnit.SECONDS);
            if (!acquired) {
                throw new IllegalStateException("Failed to acquire write lock: " + lockKey);
            }
            return supplier.get();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted while acquiring write lock: " + lockKey, e);
        } finally {
            if (acquired && writeLock.isHeldByCurrentThread()) {
                writeLock.unlock();
            }
        }
    }

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
        try {
            boolean acquired = lock.tryLock(waitSeconds, leaseSeconds, TimeUnit.SECONDS);
            if (acquired) {
                try {
                    return supplier.get();
                } finally {
                    if (lock.isHeldByCurrentThread()) {
                        lock.unlock();
                    }
                }
            } else {
                log.warn("Lock acquisition failed, executing fallback: key={}", lockKey);
                return fallback.get();
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.warn("Lock acquisition interrupted, executing fallback: key={}", lockKey);
            return fallback.get();
        }
    }
}
