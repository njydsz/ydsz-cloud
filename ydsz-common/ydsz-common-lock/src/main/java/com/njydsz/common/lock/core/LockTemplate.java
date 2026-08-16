package com.njydsz.common.lock.core;

import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;
import lombok.extern.slf4j.Slf4j;
import com.njydsz.common.lock.annotation.LockType;
import com.njydsz.common.lock.exception.DistributedLockException;
import com.njydsz.common.lock.strategy.LockStrategy;

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
     * @throws DistributedLockException 锁获取失败时抛出
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
     * @throws DistributedLockException 锁获取失败时抛出
     */
    public <T> T execute(String lockKey, long waitTime, long leaseTime, TimeUnit timeUnit, Supplier<T> action) {
        DistributedLocker lock = lockStrategy.getLock(LockType.REENTRANT);
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
        return tryExecuteOrDefault(lockKey, LockRequest.withoutWait(leaseTime, timeUnit), action, defaultValue);
    }

    /**
     * 尝试在锁保护下执行业务逻辑（带等待时间），锁获取失败时返回 null
     *
     * @param lockKey   锁键
     * @param waitTime  最大等待时间
     * @param leaseTime 租约时间
     * @param timeUnit  时间单位
     * @param action    要执行的业务逻辑
     * @param <T>       返回值类型
     * @return 业务逻辑的返回值，锁获取失败时返回 null
     */
    public <T> T executeOrDefault(String lockKey, long waitTime, long leaseTime, TimeUnit timeUnit,
                                   Supplier<T> action) {
        return tryExecuteOrDefault(lockKey, LockRequest.of(waitTime, leaseTime, timeUnit), action, null);
    }

    /**
     * 尝试在锁保护下执行业务逻辑，锁获取失败时返回默认值
     *
     * @param lockKey      锁键
     * @param request      锁请求参数（等待时间/租约时间/时间单位）
     * @param action       要执行的业务逻辑
     * @param defaultValue 锁获取失败时的默认返回值
     * @param <T>          返回值类型
     * @return 业务逻辑的返回值或默认值
     */
    private <T> T tryExecuteOrDefault(String lockKey, LockRequest request,
                                       Supplier<T> action, T defaultValue) {
        DistributedLocker lock = lockStrategy.getLock(LockType.REENTRANT);
        String lockValue;
        try {
            lockValue = request.waitTime() > 0
                    ? lock.tryLock(lockKey, request.waitTime(), request.leaseTime(), request.timeUnit())
                    : lock.tryLock(lockKey, request.leaseTime(), request.timeUnit());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return defaultValue;
        }

        if (lockValue == null) {
            log.debug("[ydsz-lock] [template] 锁获取失败，返回默认值 key={}", lockKey);
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
     *
     * @param lock      锁实例
     * @param lockKey   锁键
     * @param waitTime  最大等待时间（0 表示不等待）
     * @param leaseTime 租约时间
     * @param timeUnit  时间单位
     * @return 获取成功的锁值
     * @throws DistributedLockException 获取失败或被中断时抛出
     */
    private String acquireLock(DistributedLocker lock, String lockKey,
                                long waitTime, long leaseTime, TimeUnit timeUnit) {
        try {
            String lockValue = waitTime > 0
                    ? lock.tryLock(lockKey, waitTime, leaseTime, timeUnit)
                    : lock.tryLock(lockKey, leaseTime, timeUnit);
            if (lockValue == null) {
                throw new DistributedLockException("获取分布式锁失败: " + lockKey);
            }
            return lockValue;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new DistributedLockException("获取分布式锁被中断: " + lockKey, e);
        }
    }

    /**
     * 释放锁（内部方法）
     *
     * @param lock      锁实例
     * @param lockKey   锁键
     * @param lockValue 获取锁时返回的锁值
     */
    private void releaseLock(DistributedLocker lock, String lockKey, String lockValue) {
        try {
            lock.unlock(lockKey, lockValue);
        } catch (Exception e) {
            log.warn("[ydsz-lock] [template] 释放锁异常 key={} cause={}", lockKey, e.getMessage());
        }
    }

    /**
     * 锁请求参数（等待时间 / 租约时间 / 时间单位）
     *
     * @param waitTime  最大等待时间
     * @param leaseTime 租约时间
     * @param timeUnit  时间单位
     */
    public record LockRequest(long waitTime, long leaseTime, TimeUnit timeUnit) {

        /**
         * 构造带等待时间的请求
         *
         * @param waitTime  最大等待时间
         * @param leaseTime 租约时间
         * @param timeUnit  时间单位
         * @return 请求对象
         */
        public static LockRequest of(long waitTime, long leaseTime, TimeUnit timeUnit) {
            return new LockRequest(waitTime, leaseTime, timeUnit);
        }

        /**
         * 构造不带等待时间的请求
         *
         * @param leaseTime 租约时间
         * @param timeUnit  时间单位
         * @return 请求对象
         */
        public static LockRequest withoutWait(long leaseTime, TimeUnit timeUnit) {
            return new LockRequest(0, leaseTime, timeUnit);
        }
    }
}
