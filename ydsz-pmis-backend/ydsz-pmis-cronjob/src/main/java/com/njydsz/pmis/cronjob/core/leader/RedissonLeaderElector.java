package com.njydsz.pmis.cronjob.core.leader;

import com.njydsz.pmis.cronjob.config.CronjobProperties;
import jakarta.annotation.PreDestroy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.Scheduled;

import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

/**
 * 基于 Redisson 的 Leader 选举实现。
 *
 * <p>使用 Redisson {@link RLock} 实现分布式 Leader 选举：
 * <ul>
 *   <li>抢锁：{@code tryLock(0, leaseTime, MILLISECONDS)} 非阻塞获取</li>
 *   <li>续期：通过 Spring {@code @Scheduled} 定时任务在 lease 到期前续期</li>
 *   <li>释放：优雅下线时 {@code @PreDestroy} 主动释放</li>
 * </ul>
 *
 * <h3>多节点协作</h3>
 * <ol>
 *   <li>所有节点启动时尝试 {@link #tryAcquire}，仅一节点成功</li>
 *   <li>Leader 节点定期 {@link #renew} 续期（默认 lease 30s，每 10s 续期）</li>
 *   <li>Leader 崩溃后 lease 到期自动释放，Follower 下次 {@link #tryAcquire} 抢占</li>
 * </ol>
 *
 * @author ydsz-pmis-team
 * @since 1.0.0
 */
@Slf4j
@Configuration
@RequiredArgsConstructor
@ConditionalOnClass(name = "org.redisson.api.RedissonClient")
@ConditionalOnProperty(prefix = "pmis.cronjob.leader", name = "enabled", havingValue = "true")
public class RedissonLeaderElector implements LeaderElector {

    /** Leader 锁 key 前缀 */
    private static final String LOCK_KEY_PREFIX = "pmis:job:leader:";

    private final RedissonClient redissonClient;
    private final CronjobProperties cronjobProperties;

    /** 当前节点持有的 Leader 锁（role -> RLock） */
    private final Map<String, RLock> heldLocks = new ConcurrentHashMap<>();

    @Override
    public boolean tryAcquire(String role, Duration lease) {
        String key = LOCK_KEY_PREFIX + role;
        RLock lock = redissonClient.getLock(key);
        try {
            boolean acquired = lock.tryLock(0, lease.toMillis(), TimeUnit.MILLISECONDS);
            if (acquired) {
                heldLocks.put(role, lock);
                log.info("[LeaderElector] 抢占 Leader 成功: role={} lease={}ms", role, lease.toMillis());
            }
            return acquired;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.warn("[LeaderElector] 抢占 Leader 被中断: role={}", role);
            return false;
        }
    }

    @Override
    public boolean renew(String role) {
        RLock lock = heldLocks.get(role);
        if (lock == null) {
            return false;
        }
        try {
            if (lock.isHeldByCurrentThread()) {
                // RLock 续期：通过 Redisson 的 expireAsync 设置新过期时间
                // 注意：RLock 自身不暴露 expire(long, TimeUnit)，但 RedissonObject 基类提供
                // 此处用 redissonClient 获取底层 key 并重新设置 TTL
                String key = LOCK_KEY_PREFIX + role;
                redissonClient.getBucket(key).expire(
                        cronjobProperties.getLeader().getLeaseSeconds(), TimeUnit.SECONDS);
                return true;
            }
            return false;
        } catch (Exception e) {
            log.warn("[LeaderElector] 续期失败: role={} reason={}", role, e.getMessage());
            heldLocks.remove(role);
            return false;
        }
    }

    @Override
    public boolean isLeader(String role) {
        RLock lock = heldLocks.get(role);
        return lock != null && lock.isHeldByCurrentThread();
    }

    @Override
    public void release(String role) {
        RLock lock = heldLocks.remove(role);
        if (lock != null && lock.isHeldByCurrentThread()) {
            try {
                lock.unlock();
                log.info("[LeaderElector] 释放 Leader: role={}", role);
            } catch (Exception e) {
                log.warn("[LeaderElector] 释放 Leader 失败: role={} reason={}", role, e.getMessage());
            }
        }
    }

    @Override
    public String getCurrentLeader(String role) {
        RLock lock = redissonClient.getLock(LOCK_KEY_PREFIX + role);
        // Redisson RLock 不直接暴露持有者标识；返回是否存在活跃锁
        return lock.isLocked() ? "unknown" : null;
    }

    /**
     * 定时续期 Leader 租约（默认每 10s 续期一次，lease 30s）。
     *
     * <p>在 lease 到期前续期，避免误释放导致 Leader 切换。
     */
    @Scheduled(fixedDelayString = "${pmis.cronjob.leader.renew-interval-seconds:10}s")
    public void renewLeaseTask() {
        if (heldLocks.isEmpty()) {
            return;
        }
        for (String role : heldLocks.keySet()) {
            boolean renewed = renew(role);
            if (!renewed) {
                log.warn("[LeaderElector] 续期失败，尝试重新抢占: role={}", role);
                Duration lease = Duration.ofSeconds(cronjobProperties.getLeader().getLeaseSeconds());
                if (tryAcquire(role, lease)) {
                    log.info("[LeaderElector] 重新抢占 Leader 成功: role={}", role);
                } else {
                    log.warn("[LeaderElector] 重新抢占 Leader 失败，等待下次续期: role={}", role);
                }
            }
        }
    }

    /**
     * 优雅下线：释放所有持有的 Leader 锁。
     */
    @PreDestroy
    public void shutdown() {
        for (String role : heldLocks.keySet().toArray(new String[0])) {
            release(role);
        }
    }
}
