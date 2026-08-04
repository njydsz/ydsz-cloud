package com.remisoft.common.redis.service;

import java.time.Duration;
import java.util.Collections;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import jakarta.annotation.PreDestroy;

import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;

import com.remisoft.common.redis.config.RedisProperties;
import com.remisoft.common.util.id.WorkerIdRegistry;

import lombok.extern.slf4j.Slf4j;

import com.remisoft.common.util.id.SnowflakeAutoConfiguration;
/**
 * 基于 Redis 的 WorkerId 注册中心实现
 *
 * <p>使用 Redis SET NX EX 抢占式分配 WorkerId，保证集群中每个 Snowflake 实例拿到的 WorkerId 唯一。
 * WorkerId 取值范围 [0, 31]，对应 Snowflake 算法 5 位 workerId。
 *
 * <p><b>分配策略：</b>
 * <ul>
 *   <li>{@code acquire}：从 0 开始遍历到 {@link #MAX_WORKER_ID}，对每个槽位执行 {@code SET NX EX}，
 *       第一个抢占成功的槽位即为该节点的 WorkerId。Value 写入 nodeIp，便于排查与释放校验。</li>
 *   <li>{@code heartbeat}：仅当 key 存在且 value 匹配 nodeIp 时才续约 TTL（Lua 脚本原子操作）。</li>
 *   <li>{@code release}：仅当 key 存在且 value 匹配 nodeIp 时才删除（Lua 脚本原子操作），避免误删他人占用的槽位。</li>
 * </ul>
 *
 * <p><b>自动心跳：</b>acquire 成功后，启动守护线程定期（默认 30s）对持有的 workerId 续约，
 * 续约 TTL 默认 90s。应用关闭时通过 {@link PreDestroy} 释放所有 workerId。
 *
 * <p><b>容错：</b>Redis 不可用时抛出 {@link IllegalStateException}，由上层 {@code SnowflakeAutoConfiguration}
 * 捕获后回退到本地分配策略。
 *
 * <p><b>使用示例：</b>
 * <pre>{@code
 * RedisWorkerIdRegistry registry = new RedisWorkerIdRegistry(redisTemplate, redisProperties);
 * long workerId = registry.acquire("10.0.0.1", 300_000L);
 * // 自动心跳续约，无需手动调用 heartbeat
 * registry.release(workerId, "10.0.0.1");
 * }</pre>
 *
 * @author remi-team
 * @since 1.0.0
 * @see WorkerIdRegistry
 * @see SnowflakeAutoConfiguration
 */
@Slf4j
public class RedisWorkerIdRegistry implements WorkerIdRegistry {

    /** Snowflake 算法 workerId 最大值（5 位，0~31） */
    private static final long MAX_WORKER_ID = 31L;

    /** 默认租约时间（毫秒），当调用方未指定时使用 */
    private static final long DEFAULT_LEASE_MILLIS = 300_000L;

    /** 自动心跳间隔（秒） */
    private static final long HEARTBEAT_INTERVAL_SECONDS = 30L;

    /**
     * 心跳续约 Lua 脚本：仅当 key 存在且 value 匹配 nodeIp 时才续约 TTL（毫秒）
     * <p>KEYS[1] = workerId key
     * <br>ARGV[1] = nodeIp（期望的 value）
     * <br>ARGV[2] = TTL（毫秒）
     */
    private static final String HEARTBEAT_LUA =
            "if redis.call('GET', KEYS[1]) == ARGV[1] then " +
            "  return redis.call('PEXPIRE', KEYS[1], tonumber(ARGV[2])) " +
            "end " +
            "return 0";

    /**
     * 释放 WorkerId Lua 脚本：仅当 key 存在且 value 匹配 nodeIp 时才删除
     * <p>KEYS[1] = workerId key
     * <br>ARGV[1] = nodeIp（期望的 value）
     */
    private static final String RELEASE_LUA =
            "if redis.call('GET', KEYS[1]) == ARGV[1] then " +
            "  return redis.call('DEL', KEYS[1]) " +
            "end " +
            "return 0";

    private final RedisTemplate<String, Object> redisTemplate;
    private final RedisProperties redisProperties;

    /** 缓存 Lua 脚本对象，避免每次执行都重新编译 */
    private final DefaultRedisScript<Long> heartbeatScript;
    private final DefaultRedisScript<Long> releaseScript;

    /** 记录当前节点已持有的 workerId -> nodeIp，便于 shutdown 时统一释放 */
    private final ConcurrentMap<Long, String> heldWorkerIds = new ConcurrentHashMap<>();

    /** 心跳定时任务执行器 */
    private final ScheduledExecutorService heartbeatExecutor;

    /** 关闭标记 */
    private volatile boolean shutdown = false;

    public RedisWorkerIdRegistry(RedisTemplate<String, Object> redisTemplate,
                                 RedisProperties redisProperties) {
        this.redisTemplate = redisTemplate;
        this.redisProperties = redisProperties;
        this.heartbeatScript = new DefaultRedisScript<>(HEARTBEAT_LUA, Long.class);
        this.releaseScript = new DefaultRedisScript<>(RELEASE_LUA, Long.class);
        this.heartbeatExecutor = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "snowflake-redis-heartbeat");
            t.setDaemon(true);
            return t;
        });
        this.heartbeatExecutor.scheduleAtFixedRate(this::renewAllHeld,
                HEARTBEAT_INTERVAL_SECONDS, HEARTBEAT_INTERVAL_SECONDS, TimeUnit.SECONDS);
        log.info("【Snowflake-Registry】RedisWorkerIdRegistry 已初始化 | heartbeatInterval={}s",
                HEARTBEAT_INTERVAL_SECONDS);
    }

    @Override
    public long acquire(String nodeIp, long leaseMillis) {
        long lease = leaseMillis > 0 ? leaseMillis : DEFAULT_LEASE_MILLIS;
        Duration leaseDuration = Duration.ofMillis(lease);
        String ip = nodeIp == null ? "unknown" : nodeIp;

        for (long candidate = 0L; candidate <= MAX_WORKER_ID; candidate++) {
            String key = formatWorkerKey(candidate);
            try {
                Boolean ok = redisTemplate.opsForValue()
                        .setIfAbsent(key, ip, leaseDuration);
                if (Boolean.TRUE.equals(ok)) {
                    heldWorkerIds.put(candidate, ip);
                    log.info("【Snowflake-Registry】acquire workerId={} for node={} | ttl={}ms",
                            candidate, ip, lease);
                    return candidate;
                }
            } catch (Exception e) {
                log.warn("【Snowflake-Registry】acquire workerId={} failed for node={}, error={}",
                        candidate, ip, e.getMessage());
                throw new IllegalStateException("Redis unavailable while acquiring workerId", e);
            }
        }
        throw new IllegalStateException(
                "WorkerId exhausted: all slots [0, " + MAX_WORKER_ID + "] are occupied");
    }

    @Override
    public boolean heartbeat(long workerId, String nodeIp) {
        if (!isValidWorkerId(workerId)) {
            log.warn("【Snowflake-Registry】heartbeat skipped: invalid workerId={}", workerId);
            return false;
        }
        String ip = nodeIp == null ? "unknown" : nodeIp;
        String key = formatWorkerKey(workerId);
        try {
            Long result = redisTemplate.execute(
                    heartbeatScript,
                    Collections.singletonList(key),
                    ip,
                    String.valueOf(DEFAULT_LEASE_MILLIS));
            boolean success = result != null && result == 1L;
            if (!success) {
                log.warn("【Snowflake-Registry】heartbeat workerId={} failed: key expired or value mismatch", workerId);
            }
            return success;
        } catch (Exception e) {
            log.warn("【Snowflake-Registry】heartbeat workerId={} error: {}", workerId, e.getMessage());
            return false;
        }
    }

    @Override
    public void release(long workerId, String nodeIp) {
        if (!isValidWorkerId(workerId)) {
            log.warn("【Snowflake-Registry】release skipped: invalid workerId={}", workerId);
            return;
        }
        String ip = nodeIp == null ? "unknown" : nodeIp;
        String key = formatWorkerKey(workerId);
        try {
            Long result = redisTemplate.execute(
                    releaseScript,
                    Collections.singletonList(key),
                    ip);
            if (result != null && result == 1L) {
                heldWorkerIds.remove(workerId);
                log.info("【Snowflake-Registry】release workerId={} for node={}", workerId, ip);
            } else {
                log.warn("【Snowflake-Registry】release workerId={} no-op: key expired or value mismatch", workerId);
            }
        } catch (Exception e) {
            log.warn("【Snowflake-Registry】release workerId={} error: {}", workerId, e.getMessage());
        }
    }

    /**
     * 释放当前节点所有持有的 workerId（用于优雅停机）
     */
    public void releaseAll() {
        for (ConcurrentMap.Entry<Long, String> entry : heldWorkerIds.entrySet()) {
            release(entry.getKey(), entry.getValue());
        }
    }

    /**
     * 定时为所有持有的 workerId 续约
     */
    private void renewAllHeld() {
        if (shutdown || heldWorkerIds.isEmpty()) {
            return;
        }
        for (ConcurrentMap.Entry<Long, String> entry : heldWorkerIds.entrySet()) {
            try {
                if (!heartbeat(entry.getKey(), entry.getValue())) {
                    // 续约失败：key 已过期或被抢占，从本地记录中移除
                    heldWorkerIds.remove(entry.getKey());
                    log.warn("【Snowflake-Registry】workerId={} 已失效，从本地记录移除", entry.getKey());
                }
            } catch (Exception e) {
                log.warn("【Snowflake-Registry】renew workerId={} error: {}", entry.getKey(), e.getMessage());
            }
        }
    }

    /**
     * 优雅停机钩子：停止心跳、释放全部已持有的 WorkerId。
     *
     * <p>Spring 容器销毁 Bean 时由 {@code @PreDestroy} 触发。先置 {@code shutdown} 标记阻止后续心跳调度，
     * 再两段式关闭调度线程（{@code shutdown} → 最多等待 5s → {@code shutdownNow} 强制中断），最后调用
     * {@link #releaseAll()} 逐个释放持有的槽位。捕获中断异常后恢复中断标记，避免吞掉线程中断信号。
     */
    @PreDestroy
    public void shutdown() {
        shutdown = true;
        if (!heartbeatExecutor.isShutdown()) {
            heartbeatExecutor.shutdown();
            try {
                if (!heartbeatExecutor.awaitTermination(5, TimeUnit.SECONDS)) {
                    heartbeatExecutor.shutdownNow();
                }
            } catch (InterruptedException e) {
                heartbeatExecutor.shutdownNow();
                Thread.currentThread().interrupt();
            }
        }
        releaseAll();
        log.info("【Snowflake-Registry】RedisWorkerIdRegistry 已关闭");
    }

    @Override
    public String type() {
        return "redis";
    }

    private boolean isValidWorkerId(long workerId) {
        return workerId >= 0L && workerId <= MAX_WORKER_ID;
    }

    private String formatWorkerKey(long workerId) {
        String prefix = redisProperties != null ? redisProperties.getKeyPrefix() : null;
        if (prefix == null || prefix.isEmpty()) {
            return "snowflake:worker:" + workerId;
        }
        return prefix + ":snowflake:worker:" + workerId;
    }
}
