package com.njydsz.common.redis.service;

import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.connection.ReturnType;
import org.springframework.data.redis.core.RedisCallback;
import org.springframework.data.redis.core.RedisTemplate;

/**
 * 分布式锁 WatchDog 续期调度器
 *
 * <p>提供通用的锁续期能力，用于防止业务执行时间超过锁租约时间导致锁自动释放。
 * 续期间隔为 leaseTime 的 1/3，与 ydsz-common-lock 的 LockWatchDog 设计一致。
 *
 * <p><b>设计定位：</b>
 * <ul>
 *   <li>抽象为公共组件，供 {@link RedisCacheGuard} 等需要锁续期的场景使用</li>
 *   <li>统一守护线程池管理，避免各组件各自创建线程池造成资源浪费</li>
 *   <li>支持最大续期次数限制，防止业务线程卡死导致锁永不释放</li>
 * </ul>
 *
 * <p><b>脚本预加载优化：</b>
 * <p>续期/释放 Lua 脚本在首次使用时通过 {@code SCRIPT LOAD} 预加载到 Redis，
 * 后续调用直接使用缓存的 SHA 摘要执行 {@code EVALSHA}，避免每次续期都传输脚本字节，
 * 减少网络开销和 Redis 脚本编译缓存压力。
 *
 * <p><b>使用示例：</b>
 * <pre>{@code
 * LockWatchDog watchDog = new LockWatchDog(redisTemplate);
 * watchDog.start("lock:order:123", uuid, 30_000L);  // 启动续期
 * // ... 执行业务逻辑 ...
 * watchDog.stop("lock:order:123");  // 停止续期
 * }</pre>
 *
 * <p><b>迁移说明：</b>自 v1.1.0 起标记废弃，计划 v2.0.0 移除。
 * 当前无业务消费方（仅供内部 RedisCacheGuard 使用，而 CacheGuard 自身已标记废弃）。
 * 锁续期能力请直接使用 ydsz-common-lock 模块提供的分布式锁组件。
 *
 * @author ydsz-team
 * @since 1.0.0
 * @deprecated 自 v1.1.0 起无消费方，计划 v2.0.0 移除。替代方案：ydsz-common-lock 分布式锁。
 */
@Slf4j
@Deprecated(since = "1.1.0", forRemoval = true)
public class LockWatchDog {

    /** 续期 Lua 脚本：仅当锁持有者匹配时才续期 */
    private static final String RENEW_LOCK_LUA =
            "if redis.call('get', KEYS[1]) == ARGV[1] then " +
            "  return redis.call('pexpire', KEYS[1], ARGV[2]) " +
            "else " +
            "  return 0 " +
            "end";

    /** 释放锁 Lua 脚本：仅当锁持有者匹配时才释放 */
    private static final String RELEASE_LOCK_LUA =
            "if redis.call('get', KEYS[1]) == ARGV[1] then " +
            "  return redis.call('del', KEYS[1]) " +
            "else " +
            "  return 0 " +
            "end";

    /** 默认最大续期次数（约 30 分钟，基于 10 秒租约 / 3 ≈ 3.3 秒间隔 */
    private static final int DEFAULT_MAX_RENEW_TIMES = 100;

    /** 优雅关闭等待时间（秒） */
    private static final long SHUTDOWN_AWAIT_SECONDS = 5;

    private final RedisTemplate<String, Object> redisTemplate;
    private final int maxRenewTimes;
    private final ScheduledExecutorService scheduler;
    private final ConcurrentHashMap<String, WatchTask> activeTasks = new ConcurrentHashMap<>();

    /** 续期脚本 SHA（懒加载，首次使用时预加载到 Redis） */
    private volatile String renewScriptSha;

    /** 释放脚本 SHA（懒加载，首次使用时预加载到 Redis） */
    private volatile String releaseScriptSha;

    /**
     * 续期任务上下文
     */
    private static class WatchTask {
        final String lockKey;
        final String lockValue;
        final long leaseTimeMs;
        final AtomicBoolean running;
        final ScheduledFuture<?> future;
        volatile int renewCount;

        WatchTask(String lockKey, String lockValue, long leaseTimeMs,
                  AtomicBoolean running, ScheduledFuture<?> future) {
            this.lockKey = lockKey;
            this.lockValue = lockValue;
            this.leaseTimeMs = leaseTimeMs;
            this.running = running;
            this.future = future;
            this.renewCount = 0;
        }
    }

    /**
     * 创建 WatchDog 实例（使用默认续期次数限制）
     *
     * @param redisTemplate Redis 模板
     */
    public LockWatchDog(RedisTemplate<String, Object> redisTemplate) {
        this(redisTemplate, DEFAULT_MAX_RENEW_TIMES, "ydsz-lock-watchdog");
    }

    /**
     * 创建 WatchDog 实例（可配置续期次数和线程名）
     *
     * @param redisTemplate Redis 模板
     * @param maxRenewTimes 最大续期次数
     * @param threadName    守护线程名称
     */
    public LockWatchDog(RedisTemplate<String, Object> redisTemplate, int maxRenewTimes, String threadName) {
        this.redisTemplate = redisTemplate;
        this.maxRenewTimes = maxRenewTimes;
        this.scheduler = new ScheduledThreadPoolExecutor(1, r -> {
            Thread t = new Thread(r, threadName);
            t.setDaemon(true);
            return t;
        });
    }

    /**
     * 启动锁续期任务
     *
     * @param lockKey     锁键
     * @param lockValue   锁值（用于校验持有者）
     * @param leaseTimeMs 锁租约时间（毫秒）
     */
    public void start(String lockKey, String lockValue, long leaseTimeMs) {
        if (leaseTimeMs <= 0) {
            return;
        }
        // 已存在则先停止
        stop(lockKey);

        long renewInterval = leaseTimeMs / 3;
        if (renewInterval <= 0) {
            renewInterval = Math.max(leaseTimeMs / 2, 1000);
        }
        AtomicBoolean running = new AtomicBoolean(true);
        ScheduledFuture<?> future = scheduler.scheduleAtFixedRate(
                () -> renewLock(lockKey, lockValue, leaseTimeMs),
                renewInterval, renewInterval, TimeUnit.MILLISECONDS
        );
        WatchTask task = new WatchTask(lockKey, lockValue, leaseTimeMs, running, future);
        activeTasks.put(lockKey, task);
        log.debug("【LockWatchDog】启动续期 | key={} | leaseTime={}ms | interval={}ms",
                lockKey, leaseTimeMs, renewInterval);
    }

    /**
     * 停止锁续期任务
     *
     * @param lockKey 锁键
     */
    public void stop(String lockKey) {
        WatchTask task = activeTasks.remove(lockKey);
        if (task != null) {
            task.running.set(false);
            task.future.cancel(false);
            log.debug("【LockWatchDog】停止续期 | key={}", lockKey);
        }
    }

    /**
     * 释放锁（原子操作）并停止续期
     *
     * @param lockKey   锁键
     * @param lockValue 锁值
     */
    public void release(String lockKey, String lockValue) {
        stop(lockKey);
        releaseLockInternal(lockKey, lockValue);
    }

    /**
     * 优雅关闭：停止所有续期任务并关闭调度器
     */
    @PreDestroy
    public void shutdown() {
        for (Map.Entry<String, WatchTask> entry : activeTasks.entrySet()) {
            WatchTask task = entry.getValue();
            task.running.set(false);
            task.future.cancel(false);
        }
        activeTasks.clear();

        scheduler.shutdown();
        try {
            if (!scheduler.awaitTermination(SHUTDOWN_AWAIT_SECONDS, TimeUnit.SECONDS)) {
                log.warn("【LockWatchDog】调度器在 {}s 内未终止，执行强制关闭", SHUTDOWN_AWAIT_SECONDS);
                scheduler.shutdownNow();
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            scheduler.shutdownNow();
        }
        log.info("【LockWatchDog】调度器已关闭");
    }

    /**
     * 预加载 Lua 脚本到 Redis（首次使用时调用）
     *
     * <p>通过 {@code SCRIPT LOAD} 将续期和释放脚本预加载到 Redis，
     * 后续续期操作直接使用 SHA 执行 {@code EVALSHA}，避免每次传输脚本字节。</p>
     */
    private void preloadScripts() {
        if (renewScriptSha == null) {
            synchronized (this) {
                if (renewScriptSha == null) {
                    try {
                        renewScriptSha = loadScript(RENEW_LOCK_LUA);
                        releaseScriptSha = loadScript(RELEASE_LOCK_LUA);
                        log.debug("【LockWatchDog】脚本预加载完成 | renewSha={} | releaseSha={}",
                                renewScriptSha, releaseScriptSha);
                    } catch (Exception e) {
                        log.warn("【LockWatchDog】脚本预加载失败，将降级为每请求 scriptLoad", e);
                    }
                }
            }
        }
    }

    /**
     * 加载单个 Lua 脚本到 Redis 并返回 SHA
     *
     * @param script Lua 脚本内容
     * @return SHA 摘要
     */
    private String loadScript(String script) {
        return redisTemplate.execute((RedisCallback<String>) connection -> {
            byte[] scriptBytes = script.getBytes(StandardCharsets.UTF_8);
            return connection.scriptingCommands().scriptLoad(scriptBytes);
        });
    }

    /**
     * 续期锁（带次数限制检查）
     */
    private void renewLock(String lockKey, String lockValue, long leaseTimeMs) {
        WatchTask task = activeTasks.get(lockKey);
        if (task == null || !task.running.get()) {
            return;
        }
        if (task.renewCount >= maxRenewTimes) {
            log.warn("【LockWatchDog】续期次数超限，停止续期 | key={} | renewCount={}",
                    lockKey, task.renewCount);
            task.running.set(false);
            task.future.cancel(false);
            activeTasks.remove(lockKey);
            return;
        }
        try {
            preloadScripts();
            Boolean renewed;
            if (renewScriptSha != null) {
                // 使用预加载的 SHA 执行 EVALSHA
                renewed = evalShaRenew(lockKey, lockValue, leaseTimeMs);
            } else {
                // 降级：直接执行脚本
                renewed = evalScriptRenew(lockKey, lockValue, leaseTimeMs);
            }
            if (Boolean.TRUE.equals(renewed)) {
                task.renewCount++;
                log.debug("【LockWatchDog】续期成功 | key={} | renewCount={}", lockKey, task.renewCount);
            } else {
                log.warn("【LockWatchDog】续期失败，锁可能已失效 | key={}", lockKey);
                task.running.set(false);
                task.future.cancel(false);
                activeTasks.remove(lockKey);
            }
        } catch (Exception e) {
            log.warn("【LockWatchDog】续期异常 | key={}", lockKey, e);
        }
    }

    /**
     * 使用预加载 SHA 执行续期（正常路径）
     */
    private Boolean evalShaRenew(String lockKey, String lockValue, long leaseTimeMs) {
        return redisTemplate.execute((RedisCallback<Boolean>) connection -> {
            byte[] keyBytes = lockKey.getBytes(StandardCharsets.UTF_8);
            byte[] valueBytes = lockValue.getBytes(StandardCharsets.UTF_8);
            byte[] leaseBytes = String.valueOf(leaseTimeMs).getBytes(StandardCharsets.UTF_8);
            Object result = connection.scriptingCommands().evalSha(renewScriptSha,
                    ReturnType.INTEGER,
                    1, keyBytes, valueBytes, leaseBytes);
            return Long.valueOf(1L).equals(result);
        });
    }

    /**
     * 降级方案：直接发送脚本执行（预加载失败时）
     */
    private Boolean evalScriptRenew(String lockKey, String lockValue, long leaseTimeMs) {
        return redisTemplate.execute((RedisCallback<Boolean>) connection -> {
            byte[] keyBytes = lockKey.getBytes(StandardCharsets.UTF_8);
            byte[] valueBytes = lockValue.getBytes(StandardCharsets.UTF_8);
            byte[] leaseBytes = String.valueOf(leaseTimeMs).getBytes(StandardCharsets.UTF_8);
            byte[] scriptBytes = RENEW_LOCK_LUA.getBytes(StandardCharsets.UTF_8);
            String sha = connection.scriptingCommands().scriptLoad(scriptBytes);
            Long result = connection.scriptingCommands().evalSha(sha,
                    ReturnType.INTEGER,
                    1, keyBytes, valueBytes, leaseBytes);
            return Long.valueOf(1L).equals(result);
        });
    }

    /**
     * 内部释放锁方法
     */
    private void releaseLockInternal(String lockKey, String lockValue) {
        try {
            preloadScripts();
            if (releaseScriptSha != null) {
                evalShaRelease(lockKey, lockValue);
            } else {
                evalScriptRelease(lockKey, lockValue);
            }
        } catch (Exception e) {
            log.warn("【LockWatchDog】释放锁失败 | key={}", lockKey, e);
        }
    }

    /**
     * 使用预加载 SHA 执行释放（正常路径）
     */
    private void evalShaRelease(String lockKey, String lockValue) {
        redisTemplate.execute((RedisCallback<Object>) connection -> {
            byte[] keyBytes = lockKey.getBytes(StandardCharsets.UTF_8);
            byte[] valueBytes = lockValue.getBytes(StandardCharsets.UTF_8);
            connection.scriptingCommands().evalSha(releaseScriptSha,
                    ReturnType.INTEGER,
                    1, keyBytes, valueBytes);
            return null;
        });
    }

    /**
     * 降级方案：直接发送脚本执行释放（预加载失败时）
     */
    private void evalScriptRelease(String lockKey, String lockValue) {
        redisTemplate.execute((RedisCallback<Object>) connection -> {
            byte[] keyBytes = lockKey.getBytes(StandardCharsets.UTF_8);
            byte[] valueBytes = lockValue.getBytes(StandardCharsets.UTF_8);
            byte[] scriptBytes = RELEASE_LOCK_LUA.getBytes(StandardCharsets.UTF_8);
            String sha = connection.scriptingCommands().scriptLoad(scriptBytes);
            connection.scriptingCommands().evalSha(sha,
                    ReturnType.INTEGER,
                    1, keyBytes, valueBytes);
            return null;
        });
    }
}
