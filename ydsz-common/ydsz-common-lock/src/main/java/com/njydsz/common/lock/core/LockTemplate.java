package com.njydsz.common.lock.core;

import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

import com.njydsz.common.lock.strategy.LockStrategy;

import lombok.extern.slf4j.Slf4j;

/**
 * 编程式锁操作模板 - 简化分布式锁的使用
 *
 * <p>提供统一的 try-with-resources 风格 API，自动管理锁的获取与释放，
 * 避免手动 try-finally 样板代码。
 *
 * <h3>使用示例</h3>
 * <pre>{@code
 * @Autowired
 * private LockTemplate lockTemplate;
 *
 * public void doBusiness(String orderId) {
 *     lockTemplate.execute("order:lock:" + orderId, 30, TimeUnit.SECONDS, () -> {
 *         // 业务逻辑
 *         return null;
 *     });
 * }
 * }</pre>
 *
 * <p>也支持带返回值的执行：
 * <pre>{@code
 * OrderResult result = lockTemplate.execute("order:create", 10, TimeUnit.SECONDS, () -> {
 *     return orderService.create(request);
 * });
 * }</pre>
 *
 * @author ydsz-team
 * @since 1.0.0
 * @see LockStrategy
 */
@Slf4j
public class LockTemplate {

    private final LockStrategy lockStrategy;

    /**
     * 构造 LockTemplate
     *
     * @param lockStrategy 锁策略（工厂方法获取锁实例）
     */
    public LockTemplate(LockStrategy lockStrategy) {
        this.lockStrategy = lockStrategy;
    }

    /**
     * 在分布式锁保护下执行业务逻辑
     *
     * <p>自动获取锁、执行业务、释放锁。锁获取失败时抛出异常。
     *
     * @param lockKey   锁键
     * @param leaseTime 租约时间
     * @param timeUnit  时间单位
     * @param action    要执行的业务逻辑
     * @param <T>       返回值类型
     * @return 业务逻辑的返回值
     * @throws LockAcquireException 锁获取失败时抛出
     */
    public <T> T execute(String lockKey, long leaseTime, TimeUnit timeUnit, Supplier<T> action) {
        return execute(lockKey, 0, leaseTime, timeUnit, action);
    }

    /**
     * 在分布式锁保护下执行业务逻辑（带等待时间）
     *
     * @param lockKey   锁键
     * @param waitTime  最大等待时间（0 表示不等待）
     * @param leaseTime 租约时间
     * @param timeUnit  时间单位
     * @param action    要执行的业务逻辑
     * @param <T>       返回值类型
     * @return 业务逻辑的返回值
     * @throws LockAcquireException 锁获取失败时抛出
     * @throws InterruptedException 等待过程中线程被中断
     */
    public <T> T execute(String lockKey, long waitTime, long leaseTime, TimeUnit timeUnit, Supplier<T> action) {
        DistributedLocker lock = lockStrategy.getLock(lockKey);
        String lockValue = acquireLock(lock, lockKey, waitTime, leaseTime, timeUnit);

        try {
            return action.get();
        } finally {
            releaseLock(lock, lockKey, lockValue);
        }
    }

    /**
     * 在分布式锁保护下执行业务逻辑（无返回值）
     *
     * @param lockKey   锁键
     * @param leaseTime 租约时间
     * @param timeUnit  时间单位
     * @param action    要执行的业务逻辑
     */
    public void execute(String lockKey, long leaseTime, TimeUnit timeUnit, Runnable action) {
        execute(lockKey, leaseTime, timeUnit, () -> {
            action.run();
            return null;
        });
    }

    /**
     * 尝试在锁保护下执行业务逻辑，锁获取失败时返回默认值
     *
     * @param lockKey      锁键
     * @param leaseTime    租约时间
     * @param timeUnit     时间单位
     * @param action       要执行的业务逻辑
     * @param defaultValue 锁获取失败时的默认返回值
     * @param <T>          返回值类型
     * @return 业务逻辑的返回值或默认值
     */
    public <T> T executeOrDefault(String lockKey, long leaseTime, TimeUnit timeUnit,
                                   Supplier<T> action, T defaultValue) {
        return executeOrDefault(lockKey, 0, leaseTime, timeUnit, action, defaultValue);
    }

    /**
     * 尝试在锁保护下执行业务逻辑（带等待时间），锁获取失败时返回默认值
     *
     * @param lockKey      锁键
     * @param waitTime     最大等待时间
     * @param leaseTime    租约时间
     * @param timeUnit     时间单位
     * @param action       要执行的业务逻辑
     * @param defaultValue 锁获取失败时的默认返回值
     * @param <T>          返回值类型
     * @return 业务逻辑的返回值或默认值
     */
    public <T> T executeOrDefault(String lockKey, long waitTime, long leaseTime, TimeUnit timeUnit,
                                   Supplier<T> action, T defaultValue) {
        DistributedLocker lock = lockStrategy.getLock(lockKey);
        String lockValue;
        try {
            lockValue = waitTime > 0
                    ? lock.tryLock(lockKey, waitTime, leaseTime, timeUnit)
                    : lock.tryLock(lockKey, leaseTime, timeUnit);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return defaultValue;
        }

        if (lockValue == null) {
            log.debug("[LockTemplate] 锁获取失败，返回默认值 key={}", lockKey);
            return defaultValue;
        }

        try {
            return action.get();
        } finally {
            releaseLock(lock, lockKey, lockValue);
        }
    }

    /**
     * 获取锁（内部方法）
     */
    private String acquireLock(DistributedLocker lock, String lockKey,
                                long waitTime, long leaseTime, TimeUnit timeUnit) {
        try {
            String lockValue = waitTime > 0
                    ? lock.tryLock(lockKey, waitTime, leaseTime, timeUnit)
                    : lock.tryLock(lockKey, leaseTime, timeUnit);
            if (lockValue == null) {
                throw new LockAcquireException("获取分布式锁失败: " + lockKey);
            }
            return lockValue;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new LockAcquireException("获取分布式锁被中断: " + lockKey, e);
        }
    }

    /**
     * 释放锁（内部方法）
     */
    private void releaseLock(DistributedLocker lock, String lockKey, String lockValue) {
        try {
            lock.unlock(lockKey, lockValue);
        } catch (Exception e) {
            log.warn("[LockTemplate] 释放锁异常 key={} cause={}", lockKey, e.getMessage());
        }
    }

    /**
     * 锁获取失败异常
     */
    public static class LockAcquireException extends RuntimeException {
        public LockAcquireException(String message) {
            super(message);
        }

        public LockAcquireException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
