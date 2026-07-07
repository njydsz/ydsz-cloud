package com.njydsz.pmis.cronjob.core.leader;

import com.njydsz.pmis.cronjob.config.CronjobProperties;
import jakarta.annotation.PreDestroy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.Scheduled;

import java.lang.management.ManagementFactory;
import java.net.InetAddress;
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
    /** P0-3: Leader 持有者标识 key 前缀（value=nodeId，供 getCurrentLeader 读取） */
    private static final String HOLDER_KEY_PREFIX = "pmis:job:leader:holder:";

    private final RedissonClient redissonClient;
    private final CronjobProperties cronjobProperties;

    /** 当前节点持有的 Leader 锁（role -> RLock） */
    private final Map<String, RLock> heldLocks = new ConcurrentHashMap<>();

    /** P0-3: 当前节点 ID（hostname:port），用于 getCurrentLeader 返回真实节点标识 */
    private String nodeId;

    /** P0-5: 服务端口 */
    @Value("${server.port:0}")
    private int serverPort;

    @jakarta.annotation.PostConstruct
    private void initNodeId() {
        try {
            String hostname = InetAddress.getLocalHost().getHostName();
            this.nodeId = hostname + ":" + serverPort;
        } catch (Exception e) {
            this.nodeId = ManagementFactory.getRuntimeMXBean().getName();
        }
        log.info("[LeaderElector] 节点 ID 初始化: nodeId={}", nodeId);
    }

    @Override
    public boolean tryAcquire(String role, Duration lease) {
        String key = LOCK_KEY_PREFIX + role;
        RLock lock = redissonClient.getLock(key);
        try {
            boolean acquired = lock.tryLock(0, lease.toMillis(), TimeUnit.MILLISECONDS);
            if (acquired) {
                heldLocks.put(role, lock);
                // P0-3: 写入 Leader 持有者标识，供 getCurrentLeader 返回真实节点
                String holderKey = HOLDER_KEY_PREFIX + role;
                redissonClient.<String>getBucket(holderKey).set(nodeId, lease);
                log.info("[LeaderElector] 抢占 Leader 成功: role={} lease={}ms nodeId={}",
                        role, lease.toMillis(), nodeId);
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
                // Redisson RLock 自身不暴露 renew / expire API（4.6.1）：
                //   - 内部通过 scheduleExpirationRenewal 自动续期，无需调用方介入
                //   - 这里仅续期 holder 标识 key，供 getCurrentLeader() 读取真实节点
                Duration lease = Duration.ofSeconds(cronjobProperties.getLeader().getLeaseSeconds());
                String holderKey = HOLDER_KEY_PREFIX + role;
                redissonClient.<String>getBucket(holderKey).set(nodeId, lease);
                log.debug("[LeaderElector] 续期 holder key: role={} lease={}s nodeId={}",
                        role, lease.toSeconds(), nodeId);
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
                // P0-3: 清理 holder key
                String holderKey = HOLDER_KEY_PREFIX + role;
                redissonClient.getBucket(holderKey).delete();
                log.info("[LeaderElector] 释放 Leader: role={}", role);
            } catch (Exception e) {
                log.warn("[LeaderElector] 释放 Leader 失败: role={} reason={}", role, e.getMessage());
            }
        }
    }

    @Override
    public String getCurrentLeader(String role) {
        // P0-3: 从 holder key 读取真实 Leader 节点标识
        // 修复之前返回 "unknown" 的问题
        String holderKey = HOLDER_KEY_PREFIX + role;
        String holder = redissonClient.<String>getBucket(holderKey).get();
        if (holder != null && !holder.isBlank()) {
            return holder;
        }
        // 兜底: holder key 不存在（可能未启用 P0-3 改造），检查锁是否存在
        RLock lock = redissonClient.getLock(LOCK_KEY_PREFIX + role);
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
