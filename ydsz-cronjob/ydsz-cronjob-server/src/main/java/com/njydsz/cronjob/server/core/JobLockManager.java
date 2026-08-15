package com.njydsz.cronjob.server.core;

import java.util.concurrent.TimeUnit;

import com.njydsz.common.lock.annotation.LockType;
import com.njydsz.common.lock.core.DistributedLocker;
import com.njydsz.common.lock.strategy.LockStrategy;

import lombok.extern.slf4j.Slf4j;

/**
 * P2-1: 任务锁管理器（从 DefaultTaskDispatcher 提取）。
 *
 * <p>封装分布式锁的获取与释放逻辑，委托给 ydsz-common-lock 公共模块的
 * {@link DistributedLocker} 接口实现，复用其可重入锁、WatchDog 自动续约、
 * 锁监控指标（LockMetrics）等公共能力。
 *
 * <h3>核心能力</h3>
 * <ul>
 *   <li>{@link #tryAcquireLock}：抢占分布式锁（委托 {@link DistributedLocker#tryLock}）</li>
 *   <li>{@link #releaseLock}：安全释放锁（委托 {@link DistributedLocker#unlock}，仅持有者可释放）</li>
 *   <li>{@link #isLocked}：检查锁是否被持有（委托 {@link DistributedLocker#isLocked}）</li>
 * <li>{@link #getLockHolder}：已废弃（接口能力不提供），使用 {@link #isLocked(String, Integer)} 替代</li>
 * </ul>
 *
 * <h3>改造动机</h3>
 * <p>原实现直接使用 {@code StringRedisTemplate} + Lua CAS 脚本自实现锁的获取与释放，
 * 绕过了 ydsz-common-lock 模块提供的 {@link DistributedLocker} 接口。改造为通过
 * {@link LockStrategy} 获取可重入锁实例后，可获得：
 * <ul>
 *   <li>可重入锁：同一线程可多次获取同一把锁，内部维护重入计数</li>
 *   <li>WatchDog 自动续约：长任务执行期间锁不会被意外释放</li>
 *   <li>锁监控指标（LockMetrics）：锁竞争、等待时长、活跃锁数等指标自动采集</li>
 *   <li>统一锁 key 构造（通过 {@link LockKeyUtil}）</li>
 *   <li>统一错误处理和日志格式</li>
 *   <li>便于单元测试（Mock {@link DistributedLocker} 即可）</li>
 * </ul>
 *
 * @author ydsz-team
 * @since 1.0.0
 */
@Slf4j
public class JobLockManager {

    /**
     * 可重入分布式锁实例（构造时一次性获取并缓存）
     *
     * <p>由 {@link LockStrategy#getLock(LockType)} 返回，{@code LockStrategy} 内部
     * 按锁类型缓存实例，WatchDog 与 LockMetrics 已在工厂创建时注入。
     */
    private final DistributedLocker distributedLocker;

    /**
     * 构造 JobLockManager。
     *
     * @param lockStrategy 锁策略工厂（Spring 容器中由
     *                    {@code DistributedLockAutoConfiguration} 自动装配）
     */
    public JobLockManager(LockStrategy lockStrategy) {
        this.distributedLocker = lockStrategy.getLock(LockType.REENTRANT);
    }

    /**
     * 抢占分布式锁。
     *
     * <p>委托给 {@link DistributedLocker#tryLock(String, long, TimeUnit)}，
     * 实际实现为可重入锁（{@link LockType#REENTRANT}），获取成功后自动启动
     * WatchDog 续约任务。
     *
     * @param jobKey     任务 KEY
     * @param shardIndex 分片索引（null=非分片任务）
     * @param ttlMs      锁 TTL（毫秒）
     * @return 锁持有者标识（clientId）；获取失败返回 null
     */
    public String tryAcquireLock(String jobKey, Integer shardIndex, long ttlMs) {
        String lockKey = LockKeyUtil.buildJobLockKey(jobKey, shardIndex);
        return distributedLocker.tryLock(lockKey, ttlMs, TimeUnit.MILLISECONDS);
    }

    /**
     * 安全释放分布式锁（仅持有者可释放）。
     *
     * <p>委托给 {@link DistributedLocker#unlock(String, String)}，
     * 通过 Lua 脚本原子性递减重入计数，计数归零时删除整个 Hash 键，
     * 释放成功后自动停止 WatchDog 续期任务。
     *
     * @param jobKey     任务 KEY
     * @param shardIndex 分片索引
     * @param lockValue  锁持有者标识（{@link #tryAcquireLock} 返回值）
     * @return true=释放成功，false=锁已被其他节点持有或已过期
     */
    public boolean releaseLock(String jobKey, Integer shardIndex, String lockValue) {
        String lockKey = LockKeyUtil.buildJobLockKey(jobKey, shardIndex);
        return distributedLocker.unlock(lockKey, lockValue);
    }

    /**
     * 检查锁是否被持有。
     *
     * <p>委托给 {@link DistributedLocker#isLocked(String)}。
     *
     * @param jobKey     任务 KEY
     * @param shardIndex 分片索引
     * @return true=锁存在
     */
    public boolean isLocked(String jobKey, Integer shardIndex) {
        String lockKey = LockKeyUtil.buildJobLockKey(jobKey, shardIndex);
        return distributedLocker.isLocked(lockKey);
    }

    /**
     * 获取当前锁持有者标识。
     *
     * <p><b>已废弃：</b>委托至 {@link DistributedLocker} 后，锁内部以 Redis Hash 结构
     * 存储可重入计数（Hash Field 为 clientId），{@link DistributedLocker} 接口未暴露
     * 获取 holder 的能力。本方法始终返回 {@code null}，仅保留签名避免破坏性变更。
     *
     * <p><b>替代方案：</b>使用 {@link #isLocked(String, Integer)} 判断锁状态；
     * 如需释放其他节点的锁，参考 {@code FailoverScanner.releaseLockSafe()} 通过
     * {@code JobLog#getLockHolder()} 获取锁持有者（dispatch 时写入 DB）后直接通过
     * Lua 脚本安全释放。
     *
     * @param jobKey     任务 KEY
     * @param shardIndex 分片索引
     * @return 始终返回 {@code null}
     *
     * @deprecated 自 v1.1.0 起废弃，使用 {@link #isLocked(String, Integer)} 替代
     */
    @Deprecated
    public String getLockHolder(String jobKey, Integer shardIndex) {
        log.debug("[JobLock] getLockHolder 已废弃，请使用 isLocked() 替代 | jobKey={} shardIndex={}",
                jobKey, shardIndex);
        return null;
    }
}
