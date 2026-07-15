package com.njydsz.pmis.common.redis.lock;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import jakarta.annotation.PreDestroy;

import org.springframework.data.redis.connection.ReturnType;
import org.springframework.data.redis.core.RedisCallback;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Component;

import com.njydsz.pmis.common.redis.config.RedisProperties;

import lombok.extern.slf4j.Slf4j;

/**
 * 轻量级分布式锁（基于 Redis SET NX PX + Lua 脚本 + WatchDog 续期）
 *
 * <p>提供工业级分布式锁能力，核心特性：
 * <ul>
 *   <li>原子加锁：SET key value NX PX leaseTime</li>
 *   <li>原子解锁：Lua 脚本校验持有者后删除，防止误删他人锁</li>
 *   <li>原子续期：Lua 脚本校验持有者后续期，WatchDog 自动执行</li>
 *   <li>WatchDog 续期：获取锁后自动启动，按 leaseTime/3 间隔续期，最大 100 次</li>
 *   <li>等待重试：tryLock(key, leaseTime, waitTime) 带指数退避重试</li>
 * </ul>
 *
 * <p><b>使用示例：</b>
 * <pre>{@code
 * // 基本加锁
 * String lockValue = distributedLock.tryLock("order:lock:10086", Duration.ofSeconds(30));
 * if (lockValue != null) {
 *     try {
 *         // 业务逻辑
 *     } finally {
 *         distributedLock.unlock("order:lock:10086", lockValue);
 *     }
 * }
 *
 * // 带等待的加锁
 * String lockValue = distributedLock.tryLock("order:lock:10086",
 *         Duration.ofSeconds(30), Duration.ofSeconds(5));
 * }</pre>
 *
 * @author ydsz-pmis-team
 * @since 1.1.0
 */
@Slf4j
@Component
public class RedisDistributedLock {

    private static final String UNLOCK_LUA =
            "if redis.call('get', KEYS[1]) == ARGV[1] then return redis.call('del', KEYS[1]) else return 0 end";

    private static final String RENEW_LUA =
            "if redis.call('get', KEYS[1]) == ARGV[1] then return redis.call('pexpire', KEYS[1], ARGV[2]) else return 0 end";

    private static final int MAX_RENEW_TIMES = 100;
    private static final long SHUTDOWN_AWAIT_SECONDS = 5;
    private static final long WAIT_INITIAL_BACKOFF_MS = 10;
    private static final long WAIT_MAX_BACKOFF_MS = 200;

    private final RedisTemplate<String, Object> redisTemplate;
    private final RedisProperties redisProperties;
    private final ScheduledExecutorService watchDogScheduler;
    private final ConcurrentHashMap<String, LockContext> activeLocks = new ConcurrentHashMap<>();

    public RedisDistributedLock(RedisTemplate<String, Object> redisTemplate,
                                RedisProperties redisProperties) {
        this.redisTemplate = redisTemplate;
        this.redisProperties = redisProperties;
        this.watchDogScheduler = Executors.newScheduledThreadPool(1, r -> {
            Thread t = new Thread(r, "ydsz-redis-lock-watchdog");
            t.setDaemon(true);
            return t;
        });
        log.info("【RedisDistributedLock】分布式锁组件初始化完成");
    }

    /**
     * 尝试获取分布式锁（非阻塞）
     *
     * @param key       锁键
     * @param leaseTime 锁租约时间
     * @return 锁值（获取成功）或 null（获取失败）
     */
    public String tryLock(String key, Duration leaseTime) {
        if (key == null || leaseTime == null || leaseTime.isZero() || leaseTime.isNegative()) {
            return null;
        }
        try {
            String formattedKey = formatKey(key);
            String lockValue = UUID.randomUUID().toString().replace("-", "");
            Boolean locked = redisTemplate.opsForValue().setIfAbsent(
                    formattedKey, lockValue, leaseTime);
            if (Boolean.TRUE.equals(locked)) {
                startWatchDog(formattedKey, lockValue, leaseTime.toMillis());
                log.debug("【RedisDistributedLock】加锁成功 | key={} | leaseTime={}ms", key, leaseTime.toMillis());
                return lockValue;
            }
            return null;
        } catch (Exception e) {
            log.warn("【RedisDistributedLock】加锁失败 | key={} | error={}", key, e.getMessage());
            return null;
        }
    }

    /**
     * 尝试获取分布式锁（带等待重试，指数退避）
     *
     * @param key       锁键
     * @param leaseTime 锁租约时间
     * @param waitTime  最大等待时间
     * @return 锁值（获取成功）或 null（等待超时）
     */
    public String tryLock(String key, Duration leaseTime, Duration waitTime) {
        if (key == null || leaseTime == null || waitTime == null || waitTime.isZero() || waitTime.isNegative()) {
            return tryLock(key, leaseTime);
        }
        long startTime = System.currentTimeMillis();
        long waitMs = waitTime.toMillis();
        long backoff = WAIT_INITIAL_BACKOFF_MS;
        while (true) {
            String lockValue = tryLock(key, leaseTime);
            if (lockValue != null) {
                return lockValue;
            }
            long elapsed = System.currentTimeMillis() - startTime;
            if (elapsed >= waitMs) {
                log.debug("【RedisDistributedLock】等待加锁超时 | key={} | waitMs={}", key, waitMs);
                return null;
            }
            long sleepMs = Math.min(backoff, waitMs - elapsed);
            if (sleepMs > 0) {
                try {
                    Thread.sleep(sleepMs);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    return null;
                }
            }
            backoff = Math.min(backoff * 2, WAIT_MAX_BACKOFF_MS);
        }
    }

    /**
     * 释放分布式锁（Lua 脚本原子校验并删除）
     *
     * @param key       锁键
     * @param lockValue 锁值
     * @return true-释放成功，false-锁不存在或不属于当前持有者
     */
    public boolean unlock(String key, String lockValue) {
        if (key == null || lockValue == null) {
            return false;
        }
        String formattedKey = formatKey(key);
        stopWatchDog(formattedKey);
        try {
            Boolean result = redisTemplate.execute((RedisCallback<Boolean>) connection -> {
                byte[] keyBytes = formattedKey.getBytes(StandardCharsets.UTF_8);
                byte[] valueBytes = lockValue.getBytes(StandardCharsets.UTF_8);
                byte[] scriptBytes = UNLOCK_LUA.getBytes(StandardCharsets.UTF_8);
                String sha = connection.scriptingCommands().scriptLoad(scriptBytes);
                Long ret = connection.scriptingCommands().evalSha(sha,
                        ReturnType.INTEGER, 1, keyBytes, valueBytes);
                return Long.valueOf(1L).equals(ret);
            });
            if (Boolean.TRUE.equals(result)) {
                log.debug("【RedisDistributedLock】解锁成功 | key={}", key);
            }
            return Boolean.TRUE.equals(result);
        } catch (Exception e) {
            log.warn("【RedisDistributedLock】解锁失败 | key={} | error={}", key, e.getMessage());
            return false;
        }
    }

    /**
     * 手动续期锁（通常由 WatchDog 自动执行，也可手动调用）
     *
     * @param key       锁键
     * @param lockValue 锁值
     * @param leaseTime 新的租约时间
     * @return true-续期成功，false-锁不存在或不属于当前持有者
     */
    public boolean renewLock(String key, String lockValue, Duration leaseTime) {
        if (key == null || lockValue == null || leaseTime == null) {
            return false;
        }
        String formattedKey = formatKey(key);
        try {
            Boolean result = redisTemplate.execute((RedisCallback<Boolean>) connection -> {
                byte[] keyBytes = formattedKey.getBytes(StandardCharsets.UTF_8);
                byte[] valueBytes = lockValue.getBytes(StandardCharsets.UTF_8);
                byte[] leaseBytes = String.valueOf(leaseTime.toMillis()).getBytes(StandardCharsets.UTF_8);
                byte[] scriptBytes = RENEW_LUA.getBytes(StandardCharsets.UTF_8);
                String sha = connection.scriptingCommands().scriptLoad(scriptBytes);
                Long ret = connection.scriptingCommands().evalSha(sha,
                        ReturnType.INTEGER, 1, keyBytes, valueBytes, leaseBytes);
                return Long.valueOf(1L).equals(ret);
            });
            return Boolean.TRUE.equals(result);
        } catch (Exception e) {
            log.warn("【RedisDistributedLock】续期失败 | key={} | error={}", key, e.getMessage());
            return false;
        }
    }

    /**
     * 检查锁是否被持有
     *
     * @param key 锁键
     * @return true-锁存在
     */
    public boolean isLocked(String key) {
        if (key == null) {
            return false;
        }
        try {
            return Boolean.TRUE.equals(redisTemplate.hasKey(formatKey(key)));
        } catch (Exception e) {
            log.warn("【RedisDistributedLock】检查锁状态失败 | key={} | error={}", key, e.getMessage());
            return false;
        }
    }

    @PreDestroy
    public void shutdown() {
        for (Map.Entry<String, LockContext> entry : activeLocks.entrySet()) {
            LockContext ctx = entry.getValue();
            ctx.running.set(false);
            ctx.future.cancel(false);
        }
        activeLocks.clear();
        watchDogScheduler.shutdown();
        try {
            if (!watchDogScheduler.awaitTermination(SHUTDOWN_AWAIT_SECONDS, TimeUnit.SECONDS)) {
                watchDogScheduler.shutdownNow();
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            watchDogScheduler.shutdownNow();
        }
        log.info("【RedisDistributedLock】WatchDog 调度器已关闭");
    }

    private void startWatchDog(String lockKey, String lockValue, long leaseTimeMs) {
        if (leaseTimeMs <= 0) {
            return;
        }
        long renewInterval = Math.max(leaseTimeMs / 3, 1000);
        AtomicBoolean running = new AtomicBoolean(true);
        ScheduledFuture<?> future = watchDogScheduler.scheduleAtFixedRate(
                () -> renewLockWithCheck(lockKey, lockValue, leaseTimeMs),
                renewInterval, renewInterval, TimeUnit.MILLISECONDS
        );
        LockContext ctx = new LockContext(lockValue, leaseTimeMs, running, future);
        activeLocks.put(lockKey, ctx);
    }

    private void stopWatchDog(String lockKey) {
        LockContext ctx = activeLocks.remove(lockKey);
        if (ctx != null) {
            ctx.running.set(false);
            ctx.future.cancel(false);
        }
    }

    private void renewLockWithCheck(String lockKey, String lockValue, long leaseTimeMs) {
        LockContext ctx = activeLocks.get(lockKey);
        if (ctx == null || !ctx.running.get()) {
            return;
        }
        if (ctx.renewCount >= MAX_RENEW_TIMES) {
            log.warn("【RedisDistributedLock】WatchDog 续期次数超限 | key={} | renewCount={}",
                    lockKey, ctx.renewCount);
            ctx.running.set(false);
            ctx.future.cancel(false);
            activeLocks.remove(lockKey);
            return;
        }
        try {
            Boolean renewed = redisTemplate.execute((RedisCallback<Boolean>) connection -> {
                byte[] keyBytes = lockKey.getBytes(StandardCharsets.UTF_8);
                byte[] valueBytes = lockValue.getBytes(StandardCharsets.UTF_8);
                byte[] leaseBytes = String.valueOf(leaseTimeMs).getBytes(StandardCharsets.UTF_8);
                byte[] scriptBytes = RENEW_LUA.getBytes(StandardCharsets.UTF_8);
                String sha = connection.scriptingCommands().scriptLoad(scriptBytes);
                Long ret = connection.scriptingCommands().evalSha(sha,
                        ReturnType.INTEGER, 1, keyBytes, valueBytes, leaseBytes);
                return Long.valueOf(1L).equals(ret);
            });
            if (Boolean.TRUE.equals(renewed)) {
                ctx.renewCount++;
                log.debug("【RedisDistributedLock】WatchDog 续期成功 | key={} | renewCount={}",
                        lockKey, ctx.renewCount);
            } else {
                log.warn("【RedisDistributedLock】WatchDog 续期失败，锁可能已失效 | key={}", lockKey);
                ctx.running.set(false);
                ctx.future.cancel(false);
                activeLocks.remove(lockKey);
            }
        } catch (Exception e) {
            log.warn("【RedisDistributedLock】WatchDog 续期异常 | key={} | error={}",
                    lockKey, e.getMessage());
        }
    }

    private String formatKey(String key) {
        String prefix = redisProperties != null ? redisProperties.getKeyPrefix() : null;
        if (prefix == null || prefix.isEmpty()) {
            return key;
        }
        return prefix + ":" + key;
    }

    private static class LockContext {
        final String lockValue;
        final long leaseTimeMs;
        final AtomicBoolean running;
        final ScheduledFuture<?> future;
        volatile int renewCount;

        LockContext(String lockValue, long leaseTimeMs,
                    AtomicBoolean running, ScheduledFuture<?> future) {
            this.lockValue = lockValue;
            this.leaseTimeMs = leaseTimeMs;
            this.running = running;
            this.future = future;
            this.renewCount = 0;
        }
    }
}
