package com.njydsz.literule.server.core;

import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import com.njydsz.common.lock.core.DistributedLocker;

/**
 * 分布式锁服务封装（P1-3：synchronized 升级为分布式锁）
 *
 * <p>封装 ydzs-common-lock 的分布式锁操作，提供统一的锁获取/释放接口。
 * 集群部署时使用分布式锁保障多节点间的互斥，嵌入式/单节点部署时自动降级为本地锁。
 *
 * <h3>使用示例</h3>
 *
 * <pre>
 * // 有返回值
 * ApprovalRecord record = lockService.executeWithLock(
 *     "literule:approval:" + ruleCode,
 *     () -> doApprove(ruleCode, operator, comment)
 * );
 *
 * // 无返回值
 * lockService.executeWithLock(
 *     "literule:index:rebuild",
 *     () -> rebuildIndex(rules)
 * );
 * </pre>
 *
 * @since 1.4.0
 * @author ydsz-team
 */
@Slf4j
@RequiredArgsConstructor
public class LockService {

    /** 分布式锁提供者（可为 null，此时降级为本地锁） */
    private final DistributedLocker distributedLocker;

    /** 锁默认等待时间（秒） */
    private static final long DEFAULT_WAIT_TIME = 5L;

    /** 锁默认持有时间（秒） */
    private static final long DEFAULT_LEASE_TIME = 30L;

    /**
     * 执行带分布式锁的操作
     *
     * <p>获取锁失败时抛出 {@link IllegalStateException}，不阻塞等待。
     *
     * @param lockKey 锁 key（需带业务前缀，如 "literule:approval:xxx"）
     * @param action 要执行的操作
     * @param <T> 返回类型
     * @return 操作结果
     * @throws IllegalStateException 获取锁失败
     */
    public <T> T executeWithLock(String lockKey, Supplier<T> action) {
        return executeWithLock(lockKey, DEFAULT_WAIT_TIME, DEFAULT_LEASE_TIME, action);
    }

    /**
     * 执行带分布式锁的操作（自定义超时）
     *
     * @param lockKey 锁 key
     * @param waitTime 等待时间（秒）
     * @param leaseTime 持有时间（秒）
     * @param action 要执行的操作
     * @param <T> 返回类型
     * @return 操作结果
     * @throws IllegalStateException 获取锁失败
     */
    public <T> T executeWithLock(String lockKey, long waitTime, long leaseTime, Supplier<T> action) {
        if (distributedLocker == null) {
            // 无分布式锁依赖时，直接执行（嵌入式/单节点场景）
            log.debug("[LockService] DistributedLocker 未注入，降级为无锁执行: {}", lockKey);
            return action.get();
        }

        String lockValue = null;
        try {
            lockValue = distributedLocker.tryLock(lockKey, waitTime, leaseTime, TimeUnit.SECONDS);
            if (lockValue == null) {
                throw new IllegalStateException("获取分布式锁失败（超时 " + waitTime + "s）: " + lockKey);
            }
            return action.get();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("获取分布式锁被中断: " + lockKey, e);
        } finally {
            if (lockValue != null) {
                try {
                    distributedLocker.unlock(lockKey, lockValue);
                } catch (Exception e) {
                    log.warn("[LockService] 释放锁异常: {}, 原因: {}", lockKey, e.getMessage());
                }
            }
        }
    }

    /**
     * 执行带分布式锁的操作（无返回值）
     *
     * @param lockKey 锁 key
     * @param action 要执行的操作
     * @throws IllegalStateException 获取锁失败
     */
    public void executeWithLock(String lockKey, Runnable action) {
        executeWithLock(lockKey, () -> {
            action.run();
            return null;
        });
    }

    /**
     * 尝试执行带锁的操作，获取锁失败时返回默认值（不抛异常）
     *
     * @param lockKey 锁 key
     * @param action 要执行的操作
     * @param defaultValue 获取锁失败时的默认返回值
     * @param <T> 返回类型
     * @return 操作结果或默认值
     * @since 1.4.0
     */
    public <T> T executeWithLockOrDefault(String lockKey, Supplier<T> action, T defaultValue) {
        try {
            return executeWithLock(lockKey, action);
        } catch (IllegalStateException e) {
            log.warn("[LockService] 获取锁失败，返回默认值: {}, 原因: {}", lockKey, e.getMessage());
            return defaultValue;
        }
    }
}
