package com.njydsz.literule.server.core;

import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import com.njydsz.common.lock.core.LockTemplate;
import com.njydsz.common.lock.exception.DistributedLockException;
import com.njydsz.common.exception.custom.SysException;
import com.njydsz.common.locales.util.I18nMessages;

/**
 * 分布式锁服务封装（P1-3：synchronized 升级为分布式锁；P0-C1：委托 LockTemplate 消除 try-finally 样板代码）
 *
 * <p>封装 ydsz-common-lock 的 {@link LockTemplate} 编程式锁操作，提供统一的锁获取/释放接口。
 * 集群部署时使用分布式锁保障多节点间的互斥，单节点部署时自动降级（LockTemplate 内置 FallbackDistributedLock）。
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

    /** 锁模板（由 Spring 自动装配 Bean 提供，内置 FallbackDistributedLock 降级） */
    private final LockTemplate lockTemplate;

    /** 国际化消息 */
    private final I18nMessages i18n;

    /** 锁默认等待时间（秒） */
    private static final long DEFAULT_WAIT_TIME = 5L;

    /** 锁默认持有时间（秒） */
    private static final long DEFAULT_LEASE_TIME = 30L;

    /**
     * 执行带分布式锁的操作
     *
     * <p>获取锁失败时抛出 {@link SysException}（i18n 文案），不阻塞等待。
     *
     * @param lockKey 锁 key（需带业务前缀，如 "literule:approval:xxx"）
     * @param action 要执行的操作
     * @param <T> 返回类型
     * @return 操作结果
     * @throws SysException 获取锁失败（i18n key: literule.lock.acquire_failed）
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
     * @throws SysException 获取锁失败
     */
    public <T> T executeWithLock(String lockKey, long waitTime, long leaseTime, Supplier<T> action) {
        try {
            return lockTemplate.execute(lockKey, waitTime, leaseTime, TimeUnit.SECONDS, action);
        } catch (DistributedLockException e) {
            throw new SysException(
                i18n.resolve("literule.lock.acquire_failed", new Object[]{waitTime, lockKey}),
                e);
        }
    }

    /**
     * 执行带分布式锁的操作（无返回值）
     *
     * @param lockKey 锁 key
     * @param action 要执行的操作
     * @throws SysException 获取锁失败
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
        return lockTemplate.executeOrDefault(lockKey, DEFAULT_WAIT_TIME, DEFAULT_LEASE_TIME,
            TimeUnit.SECONDS, action, defaultValue);
    }
}
