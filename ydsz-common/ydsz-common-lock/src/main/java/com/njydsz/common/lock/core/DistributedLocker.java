package com.njydsz.common.lock.core;

import java.util.concurrent.TimeUnit;

/**
 * 分布式锁核心接口
 *
 * <p>定义分布式锁的基本操作契约，支持：
 * <ul>
 *   <li>普通锁：不支持重入，简单场景使用</li>
 *   <li>可重入锁：同一线程可多次获取同一把锁</li>
 *   <li>公平锁：按请求顺序获取锁，避免饥饿</li>
 *   <li>读写锁：读共享、写独占</li>
 *   <li>锁续期：WatchDog 机制自动续期</li>
 * </ul>
 *
 * <p>本接口是分布式锁的能力提供者（"Locker" 强调"提供锁的对象"角色），
 * 与 {@link com.njydsz.common.lock.annotation.DistributedLock} 注解（标记方法需要加锁）同名不同物，
 * 命名区分以避免同模块 import 冲突。
 *
 * <p><b>设计原则：</b>
 * <ul>
 *   <li>高可用：锁的获取与释放保证原子性</li>
 *   <li>防死锁：锁必须有过期时间，防止节点宕机导致死锁</li>
 *   <li>高性能：基于 Lua 脚本保证操作原子性，减少网络往返</li>
 * </ul>
 *
 * @author ydsz-team
 * @since 1.0.0
 */
public interface DistributedLocker {

    /**
     * 尝试获取锁（非阻塞）
     *
     * @param lockKey   锁的键
     * @param leaseTime 锁的自动释放时间
     * @param timeUnit  时间单位
     * @return 获取成功返回 lockValue（用于释放锁时校验），获取失败返回 null
     */
    String tryLock(String lockKey, long leaseTime, TimeUnit timeUnit);

    /**
     * 尝试获取锁（阻塞等待）
     *
     * @param lockKey   锁的键
     * @param waitTime  最大等待时间
     * @param leaseTime 锁的自动释放时间
     * @param timeUnit  时间单位
     * @return 获取成功返回 lockValue，等待超时返回 null
     * @throws InterruptedException 线程被中断
     */
    String tryLock(String lockKey, long waitTime, long leaseTime, TimeUnit timeUnit) throws InterruptedException;

    /**
     * 释放锁
     *
     * @param lockKey   锁的键
     * @param lockValue 获取锁时返回的 lockValue，用于校验锁的持有者
     * @return true-释放成功，false-释放失败或锁已过期
     */
    boolean unlock(String lockKey, String lockValue);

    /**
     * 检查锁是否被持有
     *
     * @param lockKey 锁的键
     * @return true-锁被持有，false-锁未被持有
     */
    boolean isLocked(String lockKey);

    /**
     * 获取锁的剩余过期时间
     *
     * @param lockKey 锁的键
     * @return 剩余时间（毫秒），-1 表示锁未被持有，-2 表示获取失败
     */
    long getRemainTime(String lockKey);

    /**
     * 设置锁的过期时间（PEXPIRE），用于续期而不释放锁
     *
     * <p>直接使用 Redis PEXPIRE 命令续期，避免 unlock + tryLock 的竞态窗口。
     * 实现类应根据自身能力覆盖此方法；默认返回 -1 表示不支持自动续期。
     *
     * @param key  锁的键
     * @param time 过期时间
     * @param unit 时间单位
     * @return 续期成功返回正数（剩余 TTL 毫秒），不支持或失败返回 -1
     */
    default long pexpire(String key, long time, TimeUnit unit) {
        return -1L;
    }

    /**
     * 是否支持自动续期（pexpire）
     *
     * <p>调用 {@link #pexpire} 前建议先检查此方法返回 {@code true}，
     * 避免无意义的 Redis 命令往返。
     *
     * @return true 表示 pexpire 可用
     */
    default boolean supportsPexpire() {
        return false;
    }

    // ── 可重入锁扩展方法 ──────────────────────────────────────────────────────

    /**
     * 获取锁的持有计数（可重入锁）
     *
     * <p>支持同一线程多次获取同一把锁，每次释放时计数减一，
     * 直到计数为零时才真正释放锁。
     * 默认返回 -1 表示不支持此能力。
     *
     * @param lockKey   锁的键
     * @param lockValue 获取锁时返回的 lockValue
     * @return 当前持有锁的次数，-1 表示不支持此能力
     */
    default int getHoldCount(String lockKey, String lockValue) {
        return -1;
    }

    /**
     * 检查是否持有该锁（可重入锁）
     *
     * <p>默认返回 false 表示不支持此能力。
     *
     * @param lockKey   锁的键
     * @param lockValue 获取锁时返回的 lockValue
     * @return true-持有锁，false-未持有或不支持此能力
     */
    default boolean isHeldByCurrentThread(String lockKey, String lockValue) {
        return false;
    }

    /**
     * 是否支持可重入计数查询（getHoldCount / isHeldByCurrentThread）
     *
     * @return true 表示可重入计数能力可用
     */
    default boolean supportsReentrantInfo() {
        return false;
    }

    // ── 公平锁扩展方法 ────────────────────────────────────────────────────────

    /**
     * 获取当前排队位置（公平锁）
     *
     * <p>按客户端请求的顺序获取锁，保证先到先得，避免饥饿现象。
     * 默认返回 -1 表示不支持此能力。
     *
     * @param lockKey   锁的键
     * @param lockValue 获取锁时返回的 lockValue
     * @return 排队位置（从 0 开始），-1 表示未排队或不具备此能力
     */
    default int getQueuePosition(String lockKey, String lockValue) {
        return -1;
    }

    /**
     * 获取排队客户端总数（公平锁）
     *
     * <p>默认返回 -1 表示不支持此能力。
     *
     * @param lockKey 锁的键
     * @return 排队客户端总数，-1 表示不具备此能力
     */
    default int getQueueSize(String lockKey) {
        return -1;
    }

    /**
     * 是否支持公平锁队列查询（getQueuePosition / getQueueSize）
     *
     * @return true 表示公平锁队列能力可用
     */
    default boolean supportsQueueInfo() {
        return false;
    }
}
