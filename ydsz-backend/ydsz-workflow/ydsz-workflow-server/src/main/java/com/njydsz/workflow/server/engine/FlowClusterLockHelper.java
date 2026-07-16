package com.njydsz.workflow.server.engine;

import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;

import lombok.extern.slf4j.Slf4j;

/**
 * P0-2: 工作流集群调度分布式锁辅助工具
 *
 * <p>用于包装 {@code @Scheduled} 定时任务，确保多节点部署时同一任务同一时刻只有一个节点执行。
 *
 * <p>实现：基于 Redisson RLock 的 tryLock，获取失败时直接跳过本次执行（不阻塞等待）。
 * 锁 key 以 {@code ydsz:flow:schedule:} 为前缀，TTL 略大于扫描间隔，防止任务未执行完锁就释放。
 *
 * <p>降级策略：RedissonClient Bean 不存在（单节点/测试环境）时，{@link #tryRun(String, long, Supplier)}
 * 直接执行任务不做加锁，保证功能可用。
 *
 * @since 1.7.0
 */
@Slf4j
@Component
public class FlowClusterLockHelper {

    /** 锁 key 前缀 */
    private static final String LOCK_PREFIX = "ydsz:flow:schedule:";

    private final RedissonClient redissonClient;

    public FlowClusterLockHelper(
            ObjectProvider<RedissonClient> redissonClientProvider) {
        this.redissonClient = redissonClientProvider.getIfAvailable();
        if (this.redissonClient == null) {
            log.info("[FlowClusterLock] RedissonClient 不可用，定时任务将以单节点模式运行（不加锁）");
        }
    }

    /**
     * 尝试获取分布式锁并执行任务
     *
     * <p>获取失败（其他节点正在执行）时直接返回 null，跳过本次执行。
     *
     * @param lockKey      锁 key（不含前缀，自动添加）
     * @param leaseTimeSec 锁持有时间（秒），应略大于任务预计执行时间
     * @param task         要执行的任务
     * @param <T>          返回类型
     * @return 任务执行结果；未获取锁时返回 null
     */
    public <T> T tryRun(String lockKey, long leaseTimeSec, Supplier<T> task) {
        if (redissonClient == null) {
            return task.get();
        }
        String fullKey = LOCK_PREFIX + lockKey;
        RLock lock = redissonClient.getLock(fullKey);
        boolean acquired = false;
        try {
            // 不等待（waitTime=0），获取不到立即跳过
            acquired = lock.tryLock(0, leaseTimeSec, TimeUnit.SECONDS);
            if (!acquired) {
                log.debug("[FlowClusterLock] 未获取锁，跳过本次执行: key={}", fullKey);
                return null;
            }
            return task.get();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.warn("[FlowClusterLock] 获取锁被中断: key={}", fullKey);
            return null;
        } finally {
            if (acquired && lock.isHeldByCurrentThread()) {
                try {
                    lock.unlock();
                } catch (Exception e) {
                    log.debug("[FlowClusterLock] 解锁异常（可能已超时自动释放）: key={} err={}",
                            fullKey, e.getMessage());
                }
            }
        }
    }

    /**
     * 尝试获取分布式锁并执行无返回值任务
     *
     * @param lockKey      锁 key
     * @param leaseTimeSec 锁持有时间（秒）
     * @param task         要执行的任务
     */
    public void tryRun(String lockKey, long leaseTimeSec, Runnable task) {
        tryRun(lockKey, leaseTimeSec, () -> {
            task.run();
            return null;
        });
    }
}
