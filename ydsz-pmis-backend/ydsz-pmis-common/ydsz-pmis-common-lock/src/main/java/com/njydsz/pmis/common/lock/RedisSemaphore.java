package com.njydsz.pmis.common.lock;

import java.time.Instant;
import java.util.Collections;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import org.springframework.scheduling.TaskScheduler;

import com.njydsz.pmis.common.lock.core.DistributedLocker;
import com.njydsz.pmis.common.redis.service.RedisService;

import lombok.extern.slf4j.Slf4j;

/**
 * 基于 Redis + Lua 脚本的分布式信号量
 * 使用 Lua 脚本保证 acquire/release 的原子操作，解决并发安全问题
 *
 * <p>并发安全保证：
 * <ul>
 *   <li>acquire：Lua 原子检查信号量计数 > 0 + 递减</li>
 *   <li>release：Lua 原子检查信号量计数 < permits + 递增</li>
 *   <li>初始化：Lua 原子 NX set 初始 permits，防止重复初始化</li>
 * </ul>
 *
 * <p><b>超时自动释放：</b>
 * <ul>
 *   <li>acquireWithTimeout：获取信号量后启动定时任务，超时后自动 release</li>
 *   <li>业务代码正常 release 时，取消定时任务，避免误释放</li>
 * </ul>
 *
 * <p>自 v3.5.1 起实现 {@link DistributedLocker} 接口，
 * 可纳入 {@link com.njydsz.pmis.common.lock.strategy.LockStrategy} 统一管理。
 *
 * @author ydsz-pmis-team
 * @since 1.0.0
 * 
 * @since 3.0.0
 */
@Slf4j
public class RedisSemaphore implements DistributedLocker {

    /**
     * Redis 服务，用于执行 Lua 脚本
     */
    private final RedisService redisService;
    /**
     * 信号量 Redis Key
     */
    private final String key;
    /**
     * 许可数量
     */
    private final int permits;
    /**
     * 信号量过期时间（毫秒）
     */
    private final long expireMillis;

    private final TaskScheduler timeoutScheduler;

    /**
     * 每次获取信号量对应的超时自动释放任务
     * Key: acquireId（UUID），Value: ScheduledFuture
     */
    private final ConcurrentHashMap<String, ScheduledFuture<?>> timeoutTasks = new ConcurrentHashMap<>();

    /**
     * 初始化标志（使用 AtomicBoolean 保证线程安全的懒初始化）
     */
    private final AtomicBoolean initialized = new AtomicBoolean(false);

    /**
     * 初始化信号量 Lua 脚本：原子性 NX 设置初始许可数量和过期时间
     */
    private static final String INIT_PERMITS_SCRIPT =
            "local current = redis.call('get', KEYS[1]) " +
                    "if current == false then " +
                    "redis.call('set', KEYS[1], ARGV[1], 'PX', ARGV[2], 'NX') " +
                    "return 1 " +
                    "else return 0 end";

    /**
     * 获取信号量 Lua 脚本：原子性检查许可数大于 0 时递减
     */
    private static final String ACQUIRE_SCRIPT =
            "local current = redis.call('get', KEYS[1]) " +
                    "if current == false then return -1 end " +
                    "local c = tonumber(current) " +
                    "if c > 0 then " +
                    "redis.call('decr', KEYS[1]) " +
                    "return c - 1 " +
                    "else return -1 end";

    /**
     * 释放信号量 Lua 脚本：原子性检查许可数小于上限时递增
     */
    private static final String RELEASE_SCRIPT =
            "local current = redis.call('get', KEYS[1]) " +
                    "if current == false then return -1 end " +
                    "local c = tonumber(current) " +
                    "if c < tonumber(ARGV[1]) then " +
                    "redis.call('incr', KEYS[1]) " +
                    "return c + 1 " +
                    "else return -1 end";

    /**
     * 构造 RedisSemaphore（需要注入调度线程池，便于 Spring 管理和配置化）
     *
     * @param redisService    Redis 服务
     * @param key             信号量键
     * @param permits         许可数量
     * @param expireMillis    过期时间（毫秒）
     * @param timeoutScheduler 超时调度线程池
     */
    public RedisSemaphore(RedisService redisService, String key, int permits, long expireMillis,
                          TaskScheduler timeoutScheduler) {
        this.redisService = redisService;
        this.key = "semaphore:" + key;
        this.permits = permits;
        this.expireMillis = expireMillis;
        this.timeoutScheduler = timeoutScheduler;
        // 懒初始化：不在构造函数中初始化 permits，延迟到首次使用时初始化
    }

    /**
     * 初始化信号量许可数量，仅在 key 不存在时设置
     * <p>使用懒初始化模式，避免在 Spring 启动时执行 Redis 操作导致连接问题
     */
    private void initPermits() {
        // 使用 CAS 保证只初始化一次
        if (!initialized.compareAndSet(false, true)) {
            return;
        }
        try {
            redisService.executeScript(
                    INIT_PERMITS_SCRIPT,
                    Collections.singletonList(key),
                    Long.class,
                    String.valueOf(permits),
                    String.valueOf(expireMillis)
            );
        } catch (Exception e) {
            log.warn("信号量初始化失败: {}", key, e);
            // 初始化失败时重置标志，允许下次重试
            initialized.set(false);
        }
    }

    /**
     * 确保信号量已初始化（在每次操作前调用）
     */
    private void ensureInitialized() {
        if (!initialized.get()) {
            initPermits();
        }
    }

    /**
     * 尝试获取信号量（不等待）
     *
     * @return true-获取成功，false-获取失败
     */
    public boolean tryAcquire() {
        return tryAcquire(0, TimeUnit.MILLISECONDS);
    }

    /**
     * 尝试获取信号量（带等待时间）
     *
     * @param timeout 最大等待时间
     * @param unit    时间单位
     * @return true-获取成功，false-获取失败或超时
     */
    public boolean tryAcquire(long timeout, TimeUnit unit) {
        ensureInitialized(); // 确保已初始化
        long deadline = System.currentTimeMillis() + unit.toMillis(timeout);
        boolean reinitialized = false; // 标记是否已重新初始化过，避免重复重置
        while (true) {
            try {
                Long result = redisService.executeScript(
                        ACQUIRE_SCRIPT,
                        Collections.singletonList(key),
                        Long.class,
                        String.valueOf(permits)
                );
                if (result != null && result >= 0) {
                    return true;
                }
                // 返回 -1 表示 key 不存在（已过期）或许可耗尽；
                // key 过期后 initialized 仍为 true 会导致后续不再重新初始化，信号量永久不可用。
                // 此处重置 initialized 并重新初始化后重试一次。
                if (result != null && result == -1L && !reinitialized) {
                    reinitialized = true;
                    if (initialized.compareAndSet(true, false)) {
                        initPermits();
                        continue; // 重新初始化后立即重试 acquire
                    }
                }
            } catch (Exception e) {
                log.warn("信号量获取异常: {}", key, e);
            }
            if (System.currentTimeMillis() > deadline) {
                return false;
            }
            try {
                Thread.sleep(50);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return false;
            }
        }
    }

    /**
     * 获取信号量（带超时自动释放）
     *
     * <p>获取信号量成功后，启动一个定时任务，在指定超时时间后自动 release。
     * 如果业务代码在超时前正常调用 {@link #release(String)}，定时任务将被取消。
     *
     * @param timeout 超时时间
     * @param unit    时间单位
     * @return 获取成功返回 acquireId（用于 release 时取消定时任务），获取失败返回 null
     */
    public String acquireWithTimeout(long timeout, TimeUnit unit) {
        if (tryAcquire(0, TimeUnit.MILLISECONDS)) {
            String acquireId = UUID.randomUUID().toString();
            long timeoutMillis = unit.toMillis(timeout);
            ScheduledFuture<?> future = timeoutScheduler.schedule(() -> {
                timeoutTasks.remove(acquireId);
                releaseInternal();
                log.warn("信号量超时自动释放: key={}, acquireId={}", key, acquireId);
            }, Instant.now().plusMillis(timeoutMillis));
            timeoutTasks.put(acquireId, future);
            return acquireId;
        }
        return null;
    }

    /**
     * 获取信号量（带等待时间和超时自动释放）
     *
     * <p>在指定等待时间内尝试获取信号量，成功后启动超时自动释放定时任务。
     *
     * @param waitTimeout 等待获取信号量的超时时间
     * @param waitUnit    等待时间单位
     * @param autoReleaseTimeout 获取成功后的自动释放超时时间
     * @param releaseUnit 自动释放时间单位
     * @return 获取成功返回 acquireId，获取失败返回 null
     */
    public String acquireWithTimeout(long waitTimeout, TimeUnit waitUnit,
                                     long autoReleaseTimeout, TimeUnit releaseUnit) {
        if (tryAcquire(waitTimeout, waitUnit)) {
            String acquireId = UUID.randomUUID().toString();
            long timeoutMillis = releaseUnit.toMillis(autoReleaseTimeout);
            ScheduledFuture<?> future = timeoutScheduler.schedule(() -> {
                timeoutTasks.remove(acquireId);
                releaseInternal();
                log.warn("信号量超时自动释放: key={}, acquireId={}", key, acquireId);
            }, Instant.now().plusMillis(timeoutMillis));
            timeoutTasks.put(acquireId, future);
            return acquireId;
        }
        return null;
    }

    /**
     * 释放信号量（无 acquireId，不取消定时任务）
     *
     * <p>适用于未使用 acquireWithTimeout 获取信号量的场景。
     * 如果使用了 acquireWithTimeout，请使用 {@link #release(String)} 方法。
     */
    public void release() {
        releaseInternal();
    }

    /**
     * 释放信号量（带 acquireId，取消超时自动释放定时任务）
     *
     * <p>如果业务代码在超时前正常完成，调用此方法释放信号量，
     * 同时取消对应的超时自动释放定时任务，避免误释放。
     *
     * @param acquireId 获取信号量时返回的 acquireId
     */
    public void release(String acquireId) {
        if (acquireId != null) {
            ScheduledFuture<?> future = timeoutTasks.remove(acquireId);
            if (future != null) {
                future.cancel(false);
                log.debug("信号量超时自动释放任务已取消: key={}, acquireId={}", key, acquireId);
            }
        }
        releaseInternal();
    }

    /**
     * 内部释放信号量实现
     */
    private void releaseInternal() {
        try {
            Long result = redisService.executeScript(
                    RELEASE_SCRIPT,
                    Collections.singletonList(key),
                    Long.class,
                    String.valueOf(permits)
            );
            if (result != null && result == -1L) {
                log.warn("信号量释放失败，已超过最大许可数: {}", key);
            }
        } catch (Exception e) {
            log.error("信号量释放异常: {}", key, e);
        }
    }

    // ======================== DistributedLocker 接口实现 ========================

    /**
     * 尝试获取一个信号量许可（非阻塞）
     * <p>实现 {@link DistributedLocker#tryLock(String, long, TimeUnit)}，
     * 内部使用 {@code tryAcquire()} 方法。
     *
     * @param lockKey   锁的键（当前实现忽略，使用构造时传入的 key）
     * @param leaseTime 许可持有时间（毫秒精度）
     * @param timeUnit  时间单位
     * @return 获取成功返回 acquireId，获取失败返回 null
     */
    @Override
    public String tryLock(String lockKey, long leaseTime, TimeUnit timeUnit) {
        if (tryAcquire()) {
            String acquireId = UUID.randomUUID().toString();
            long timeoutMillis = timeUnit.toMillis(leaseTime);
            if (timeoutMillis > 0 && timeoutScheduler != null) {
                ScheduledFuture<?> future = timeoutScheduler.schedule(() -> {
                    timeoutTasks.remove(acquireId);
                    releaseInternal();
                    log.warn("信号量超时自动释放: key={}, acquireId={}", key, acquireId);
                }, Instant.now().plusMillis(timeoutMillis));
                timeoutTasks.put(acquireId, future);
            }
            return acquireId;
        }
        return null;
    }

    /**
     * 尝试获取一个信号量许可（带等待时间）
     * <p>实现 {@link DistributedLocker#tryLock(String, long, long, TimeUnit)}，
     * 内部使用 {@code tryAcquire(timeout, unit)} 方法。
     *
     * @param lockKey   锁的键（当前实现忽略，使用构造时传入的 key）
     * @param waitTime  最大等待时间
     * @param leaseTime 许可持有时间（自动释放超时）
     * @param timeUnit  时间单位
     * @return 获取成功返回 acquireId，等待超时返回 null
     */
    @Override
    public String tryLock(String lockKey, long waitTime, long leaseTime, TimeUnit timeUnit) throws InterruptedException {
        if (tryAcquire(waitTime, timeUnit)) {
            String acquireId = UUID.randomUUID().toString();
            long timeoutMillis = timeUnit.toMillis(leaseTime);
            if (timeoutMillis > 0 && timeoutScheduler != null) {
                ScheduledFuture<?> future = timeoutScheduler.schedule(() -> {
                    timeoutTasks.remove(acquireId);
                    releaseInternal();
                    log.warn("信号量超时自动释放: key={}, acquireId={}", key, acquireId);
                }, Instant.now().plusMillis(timeoutMillis));
                timeoutTasks.put(acquireId, future);
            }
            return acquireId;
        }
        return null;
    }

    /**
     * 释放信号量许可
     * <p>实现 {@link DistributedLocker#unlock(String, String)}，
     * 同时取消超时自动释放定时任务。
     *
     * @param lockKey   锁的键（当前实现忽略，使用构造时传入的 key）
     * @param lockValue 获取锁时返回的 acquireId
     * @return true-释放成功，false-释放失败
     */
    @Override
    public boolean unlock(String lockKey, String lockValue) {
        release(lockValue);
        return true;
    }

    /**
     * 检查信号量是否还有可用许可
     * <p>实现 {@link DistributedLocker#isLocked(String)}，
     * 当所有许可都被占用时返回 true。
     *
     * @param lockKey 锁的键（当前实现忽略，使用构造时传入的 key）
     * @return true-所有许可都被占用，false-还有可用许可
     */
    @Override
    public boolean isLocked(String lockKey) {
        try {
            Long current = redisService.executeScript(
                    "local current = redis.call('get', KEYS[1]) return current and tonumber(current) or 0",
                    Collections.singletonList(key),
                    Long.class
            );
            return current != null && current <= 0;
        } catch (Exception e) {
            log.error("信号量检查状态异常: {}", key, e);
            return false;
        }
    }

    /**
     * 获取信号量 key 的剩余过期时间
     * <p>实现 {@link DistributedLocker#getRemainTime(String)}。
     *
     * @param lockKey 锁的键（当前实现忽略，使用构造时传入的 key）
     * @return 剩余时间（毫秒），-1 表示 key 不存在，-2 表示获取失败
     */
    @Override
    public long getRemainTime(String lockKey) {
        try {
            long seconds = redisService.getExpire(key);
            return seconds > 0 ? TimeUnit.SECONDS.toMillis(seconds) : seconds;
        } catch (Exception e) {
            log.error("信号量获取剩余时间异常: {}", key, e);
            return -2;
        }
    }
}
