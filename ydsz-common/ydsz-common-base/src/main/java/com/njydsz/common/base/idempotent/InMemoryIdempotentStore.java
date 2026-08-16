package com.njydsz.common.base.idempotent;

import java.time.Duration;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 基于 ConcurrentHashMap 的本地幂等键存储。
 *
 * <p>单节点部署时作为 Redis 不可用时的降级实现。
 *
 * <p><b>注意：</b>此实现仅适用于单节点部署，分布式环境请使用 Redis 实现。
 *
 * @author ydsz-team
 * @since 1.0.0
 */
public class InMemoryIdempotentStore implements IdempotentStore {

    private static final Logger log = LoggerFactory.getLogger(InMemoryIdempotentStore.class);

    private final ConcurrentHashMap<String, Long> expireMap = new ConcurrentHashMap<>();

    private final ScheduledExecutorService cleanupScheduler =
            Executors.newSingleThreadScheduledExecutor(r -> {
                Thread t = new Thread(r, "idempotent-cleanup");
                t.setDaemon(true);
                return t;
            });

    public InMemoryIdempotentStore() {
        // 启动定时清理任务，每 30 秒清理过期键
        cleanupScheduler.scheduleWithFixedDelay(this::cleanup, 30, 30, TimeUnit.SECONDS);
    }

    @Override
    public boolean tryAcquire(String key, Duration expire) {
        if (key == null || expire == null || expire.isNegative() || expire.isZero()) {
            return false;
        }
        long expireAt = System.currentTimeMillis() + expire.toMillis();
        Long previous = expireMap.putIfAbsent(key, expireAt);
        if (previous == null) {
            return true;
        }
        // 检查已存在的键是否已过期
        if (previous < System.currentTimeMillis()) {
            // 已过期，尝试替换
            if (expireMap.replace(key, previous, expireAt)) {
                return true;
            }
        }
        return false;
    }

    @Override
    public void release(String key) {
        if (key != null) {
            expireMap.remove(key);
        }
    }

    /**
     * 获取当前存储的键数量（用于监控）。
     *
     * @return 键数量
     */
    public int size() {
        return expireMap.size();
    }

    /**
     * 关闭清理调度器。
     */
    public void shutdown() {
        cleanupScheduler.shutdownNow();
    }

    private void cleanup() {
        long now = System.currentTimeMillis();
        expireMap.entrySet().removeIf(entry -> entry.getValue() < now);
        if (log.isDebugEnabled()) {
            log.debug("幂等键清理完成，剩余 {} 个键", expireMap.size());
        }
    }
}
