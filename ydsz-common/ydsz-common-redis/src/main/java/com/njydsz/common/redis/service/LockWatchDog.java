package com.njydsz.common.redis.service;

import java.nio.charset.StandardCharsets;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.connection.ReturnType;
import org.springframework.data.redis.core.RedisCallback;
import org.springframework.data.redis.core.RedisTemplate;

/**
 * 分布式锁看门狗（WatchDog）续期组件。
 *
 * <p>通过后台定时任务自动续期 Redis 分布式锁的过期时间，
 * 防止业务执行时间超过锁的过期时间而导致锁被错误释放。</p>
 *
 * <h3>核心功能</h3>
 * <ul>
 *   <li>续期脚本 SHA 预加载：首次续期时加载 Lua 脚本，后续使用 EVALSHA</li>
 *   <li>start/stop/release 生命周期管理</li>
 *   <li>shutdown 优雅关闭调度器</li>
 *   <li>无效参数（lease <= 0）的防护</li>
 * </ul>
 *
 * <h3>使用示例</h3>
 * <pre>{@code
 * LockWatchDog watchDog = new LockWatchDog(redisTemplate);
 * try {
 *     watchDog.start("my:lock", lockId, 30_000L);
 *     // 执行业务逻辑...
 * } finally {
 *     watchDog.release("my:lock", lockId);
 * }
 * }</pre>
 *
 * @author ydsz-team
 * @since 1.0.0
 */
public class LockWatchDog {

    private static final Logger log = LoggerFactory.getLogger(LockWatchDog.class);

    /** 续期 Lua 脚本：仅当锁持有者匹配时才延长过期时间 */
    private static final String RENEWAL_SCRIPT =
        "if redis.call('get', KEYS[1]) == ARGV[1] then "
      + "  return redis.call('pexpire', KEYS[1], ARGV[2]) "
      + "else "
      + "  return 0 "
      + "end";

    /** 释放锁 Lua 脚本：仅当锁持有者匹配时才删除锁 */
    private static final String RELEASE_SCRIPT =
        "if redis.call('get', KEYS[1]) == ARGV[1] then "
      + "  return redis.call('del', KEYS[1]) "
      + "else "
      + "  return 0 "
      + "end";

    private static final String THREAD_NAME_PREFIX = "lock-watchdog-";

    private final RedisTemplate<String, Object> redisTemplate;
    private final int renewalTimes;
    private final ConcurrentHashMap<String, ScheduledFuture<?>> renewalTasks = new ConcurrentHashMap<>();
    private final ScheduledExecutorService scheduler;

    /**
     * 默认构造（30 次续期，默认线程名）。
     *
     * @param redisTemplate Redis 模板
     */
    public LockWatchDog(RedisTemplate<String, Object> redisTemplate) {
        this(redisTemplate, 30, THREAD_NAME_PREFIX + "default");
    }

    /**
     * 自定义构造。
     *
     * @param redisTemplate Redis 模板
     * @param renewalTimes  最大续期次数（0 表示不限次数）
     * @param threadName    线程名前缀
     */
    public LockWatchDog(RedisTemplate<String, Object> redisTemplate, int renewalTimes, String threadName) {
        this.redisTemplate = redisTemplate;
        this.renewalTimes = renewalTimes;
        this.scheduler = Executors.newSingleThreadScheduledExecutor(
            r -> new Thread(r, threadName));
    }

    /**
     * 开始锁续期。
     *
     * <p>按 leaseMs/3 的周期执行续期，每次续期重置过期时间为 leaseMs。
     * 若 leaseMs <= 0 则忽略本次调用（不执行任何 Redis 操作）。</p>
     *
     * @param key     锁的 Redis key
     * @param lockId  锁持有者标识（Redis value）
     * @param leaseMs 过期时间（毫秒）
     */
    public void start(String key, String lockId, long leaseMs) {
        if (leaseMs <= 0) {
            log.warn("LockWatchDog.start ignored: leaseMs must be positive, got {}", leaseMs);
            return;
        }
        // 停止已有的续期任务
        stop(key);

        long period = Math.max(leaseMs / 3, 1);
        ScheduledFuture<?> future = scheduler.scheduleAtFixedRate(() -> renewLock(key, lockId, leaseMs),
            period, period, TimeUnit.MILLISECONDS);

        renewalTasks.put(key, future);
    }

    /**
     * 停止指定 key 的续期任务。
     *
     * @param key 锁的 Redis key
     */
    public void stop(String key) {
        ScheduledFuture<?> future = renewalTasks.remove(key);
        if (future != null) {
            future.cancel(false);
        }
    }

    /**
     * 释放锁：停止续期并调用 Redis 释放锁。
     *
     * @param key    锁的 Redis key
     * @param lockId 锁持有者标识
     */
    public void release(String key, String lockId) {
        stop(key);
        releaseLockInternal(key, lockId);
    }

    /**
     * 关闭看门狗：停止所有续期任务并关闭调度器。
     */
    public void shutdown() {
        renewalTasks.values().forEach(f -> f.cancel(false));
        renewalTasks.clear();
        scheduler.shutdownNow();
    }

    /**
     * 执行锁续期。
     */
    private void renewLock(String key, String lockId, long leaseMs) {
        try {
            String renewalSha = preloadRenewalScript();
            RedisCallback<Long> callback = connection -> connection.scriptingCommands().evalSha(
                renewalSha, ReturnType.INTEGER, 1,
                key.getBytes(StandardCharsets.UTF_8),
                lockId.getBytes(StandardCharsets.UTF_8),
                Long.toString(leaseMs).getBytes(StandardCharsets.UTF_8));
            redisTemplate.execute(callback);
        } catch (Exception e) {
            log.warn("LockWatchDog renewal failed for key: {}", key, e);
        }
    }

    /**
     * 释放锁内部实现。
     */
    private void releaseLockInternal(String key, String lockId) {
        try {
            // 第一次 execute：预加载释放脚本
            String releaseSha = preloadReleaseScript();
            // 第二次 execute：执行释放
            RedisCallback<Long> callback = connection -> connection.scriptingCommands().evalSha(
                releaseSha, ReturnType.INTEGER, 1,
                key.getBytes(StandardCharsets.UTF_8),
                lockId.getBytes(StandardCharsets.UTF_8));
            redisTemplate.execute(callback);
        } catch (Exception e) {
            log.warn("LockWatchDog release failed for key: {}", key, e);
        }
    }

    /**
     * 预加载续期脚本并返回 SHA。
     *
     * @return 脚本 SHA
     */
    private String preloadRenewalScript() {
        RedisCallback<String> callback = connection ->
            connection.scriptingCommands().scriptLoad(RENEWAL_SCRIPT.getBytes(StandardCharsets.UTF_8));
        return redisTemplate.execute(callback, true);
    }

    /**
     * 预加载释放脚本并返回 SHA。
     *
     * @return 脚本 SHA
     */
    private String preloadReleaseScript() {
        RedisCallback<String> callback = connection ->
            connection.scriptingCommands().scriptLoad(RELEASE_SCRIPT.getBytes(StandardCharsets.UTF_8));
        return redisTemplate.execute(callback, true);
    }
}
