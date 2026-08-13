package com.njydsz.common.lock.scheduler;
import com.njydsz.common.lock.annotation.LockType;
import com.njydsz.common.lock.metrics.LockMetrics;
import com.njydsz.common.lock.renewal.LockRenewalService;
import jakarta.annotation.PreDestroy;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReentrantLock;
import java.util.concurrent.ScheduledFuture;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.scheduling.TaskScheduler;


/**
 * 锁续期看门狗
 *
 * <p>实现类似 Redisson 的 WatchDog 机制：
 * <ul>
 *   <li>当客户端持有锁时，定期续期</li>
 *   <li>默认续期间隔为锁过期时间的 1/3</li>
 *   <li>客户端释放锁后，自动停止续期</li>
 *   <li>按锁分组批量续期，提升性能</li>
 *   <li>续期失败自动重试（最多 3 次）</li>
 *   <li>支持优雅停机</li>
 * </ul>
 *
 * <p><b>设计原则：</b>
 * <ul>
 *   <li>防止业务执行时间超过锁过期时间导致锁被自动释放</li>
 *   <li>续期操作使用 Lua 脚本保证原子性</li>
 *   <li>使用独立线程池管理续期任务</li>
 *   <li>批量续期减少 Redis 网络往返次数</li>
 * </ul>
 *
 * <p><b>v1.2.0 变更：</b>续期脚本统一委托给 {@link LockRenewalService}，消除双锁冗余。
 *
 * @author ydsz-team
 * @since 1.0.0
 */
@Slf4j
public class LockWatchDog {

    /**
     * 最大重试次数
     */
    private static final int MAX_RETRY_COUNT = 3;

    /**
     * 默认最大续期次数（约 30 分钟：100 次 * leaseTime/3 间隔，假设 leaseTime=30s 则 100*10s≈1000s）
     */
    private static final int DEFAULT_MAX_RENEW_TIMES = 100;

    /**
     * 统一的续期脚本服务（v1.2.0 引入，替代散落在各处的重复脚本）
     */
    private final LockRenewalService renewalService;

    /**
     * Redis 模板
     */
    private final StringRedisTemplate stringRedisTemplate;

    /**
     * 指标收集器（可选）
     */
    private volatile LockMetrics lockMetrics;

    /**
     * 续期任务调度器
     */
    private final TaskScheduler scheduler;

    /**
     * 最大续期次数限制
     *
     * <p>使用 volatile 保证可见性：该字段可能由主线程通过 {@link #setMaxRenewTimes} 修改，
     * 而续期任务在调度线程中读取。缺少 volatile 可能导致调度线程读到陈旧值。
     */
    private volatile int maxRenewTimes;

    /**
     * 活跃的续期任务
     * <p>Key: lockKey, Value: 续期任务上下文
     */
    private final Map<String, WatchTask> activeTasks = new ConcurrentHashMap<>();

    /**
     * 启动续期任务的互斥锁
     * <p>使用 ReentrantLock 替代 synchronized，避免 JDK 21 虚拟线程被固定（pinning）。
     * synchronized 块在 JDK 21 中会导致虚拟线程固定到载体平台线程，
     * ReentrantLock 则基于 AQS，虚拟线程可以正常 unpark。
     */
    private final ReentrantLock startWatchLock = new ReentrantLock();

    /**
     * 续期任务上下文
     */
    static class WatchTask {
        final String clientId;
        final long leaseTime;
        final LockType lockType;
        final AtomicBoolean running;
        final ScheduledFuture<?> future;
        /**
         * 已续期次数
         *
         * <p>使用 volatile 保证可见性：在调度线程中递增，主线程通过 {@link #getRenewCount} 读取。
         */
        volatile int renewCount;

        WatchTask(String clientId, long leaseTime, LockType lockType, AtomicBoolean running, ScheduledFuture<?> future) {
            this.clientId = clientId;
            this.leaseTime = leaseTime;
            this.lockType = lockType;
            this.running = running;
            this.future = future;
            this.renewCount = 0;
        }

        public String getClientId() {
            return clientId;
        }

        public long getLeaseTime() {
            return leaseTime;
        }

        public LockType getLockType() {
            return lockType;
        }

        /**
         * 获取已续期次数
         *
         * @return 已续期次数
         */
        public int getRenewCount() {
            return renewCount;
        }
    }

    /**
     * 构造器注入（使用默认最大续期次数）
     *
     * @param renewalService      统一的锁续期服务
     * @param scheduler           调度线程池（由 Spring 管理）
     * @param stringRedisTemplate Redis 模板
     */
    public LockWatchDog(LockRenewalService renewalService,
                        TaskScheduler scheduler,
                        StringRedisTemplate stringRedisTemplate) {
        this(renewalService, scheduler, stringRedisTemplate, DEFAULT_MAX_RENEW_TIMES);
    }

    /**
     * 构造器注入
     *
     * @param renewalService      统一的锁续期服务（v1.2.0 引入）
     * @param scheduler           调度线程池（由 Spring 管理）
     * @param stringRedisTemplate Redis 模板
     * @param maxRenewTimes       最大续期次数，超过后停止续期，锁自动过期
     */
    public LockWatchDog(LockRenewalService renewalService,
                        TaskScheduler scheduler,
                        StringRedisTemplate stringRedisTemplate,
                        int maxRenewTimes) {
        this.renewalService = renewalService;
        this.scheduler = scheduler;
        this.stringRedisTemplate = stringRedisTemplate;
        this.maxRenewTimes = maxRenewTimes;
    }

    /**
     * 设置指标收集器
     *
     * @param lockMetrics 锁指标收集器
     */
    public void setLockMetrics(LockMetrics lockMetrics) {
        this.lockMetrics = lockMetrics;
    }

    /**
     * 启动续期任务
     *
     * @param lockKey   锁的键
     * @param clientId  客户端标识
     * @param leaseTime 锁的过期时间（毫秒）
     */
    public void startWatch(String lockKey, String clientId, long leaseTime) {
        startWatch(lockKey, clientId, leaseTime, LockType.REENTRANT);
    }

    /**
     * 启动续期任务（带锁类型）
     *
     * <p>锁类型决定续期时使用的 Lua 脚本：
     * <ul>
     *   <li>REENTRANT：使用 HEXISTS 检查 clientId 作为 Hash field</li>
     *   <li>FAIR：使用 HGET 'owner' 检查 clientId 作为 owner 值</li>
     * </ul>
     *
     * @param lockKey   锁的键
     * @param clientId  客户端标识
     * @param leaseTime 锁的过期时间（毫秒）
     * @param lockType  锁类型，决定续期时使用哪个 Lua 脚本
     */
    public void startWatch(String lockKey, String clientId, long leaseTime, LockType lockType) {
        startWatchLock.lock();
        try {
            if (activeTasks.containsKey(lockKey)) {
                log.debug("[ydsz-lock] [watchdog]续期任务已存在 | lockKey={}", lockKey);
                return;
            }

            long renewInterval = leaseTime / 3;
            if (renewInterval <= 0) {
                renewInterval = Math.max(leaseTime / 2, 1000);
            }

            AtomicBoolean running = new AtomicBoolean(true);

            ScheduledFuture<?> future = scheduler.scheduleAtFixedRate(
                    () -> renewLockWithRetry(lockKey, clientId, leaseTime, lockType),
                    Duration.ofMillis(renewInterval)
            );

            activeTasks.put(lockKey, new WatchTask(clientId, leaseTime, lockType, running, future));
            log.info("[ydsz-lock] [watchdog]启动续期任务 | lockKey={} | leaseTime={}ms | interval={}ms | lockType={}",
                    lockKey, leaseTime, renewInterval, lockType);
        } finally {
            startWatchLock.unlock();
        }
    }

    /**
     * 停止续期任务
     *
     * @param lockKey 锁的键
     */
    public void stopWatch(String lockKey) {
        WatchTask task = activeTasks.remove(lockKey);
        if (task != null) {
            task.running.set(false);
            task.future.cancel(false);
            log.info("[ydsz-lock] [watchdog]停止续期任务 | lockKey={}", lockKey);
        }
    }

    /**
     * 清理所有续期任务
     * <p>由 Spring 容器在销毁时自动调用，替代原来的 ShutdownHook
     */
    @PreDestroy
    public void shutdown() {
        for (Map.Entry<String, WatchTask> entry : activeTasks.entrySet()) {
            entry.getValue().running.set(false);
            entry.getValue().future.cancel(false);
            log.info("[ydsz-lock] [watchdog]清理续期任务 | lockKey={}", entry.getKey());
        }
        activeTasks.clear();
        log.info("[ydsz-lock] [watchdog]已关闭所有续期任务");
    }

    /**
     * 获取活跃续期任务快照（用于锁泄漏检测）
     *
     * @return 活跃续期任务的不可变快照
     */
    public Map<String, WatchTask> getActiveTasksSnapshot() {
        return Collections.unmodifiableMap(new HashMap<>(activeTasks));
    }

    /**
     * 判断指定锁是否正在被看门狗续期。
     *
     * <p>仅当续期任务存在、运行标记为 true 且底层定时任务未完成/未取消时返回 {@code true}；
     * 用于锁泄漏检测与资源清理判定。
     *
     * @param lockKey 锁的键
     * @return true 表示该锁仍有活跃的续期任务
     */
    public boolean isWatching(String lockKey) {
        WatchTask task = activeTasks.get(lockKey);
        return task != null && task.running.get() && !task.future.isDone() && !task.future.isCancelled();
    }

    /**
     * 获取当前活跃续期任务数量
     *
     * @return 活跃续期任务数
     */
    public int getActiveTaskCount() {
        return activeTasks.size();
    }

    /**
     * 取消指定锁的续期任务（运维强制释放场景）
     *
     * <p>等同于 {@link #stopWatch(String)}，提供别名以适配运维 API 语义。
     *
     * @param lockKey 锁的键
     */
    public void cancelRenewal(String lockKey) {
        stopWatch(lockKey);
    }

    /**
     * 获取最大续期次数
     *
     * @return 最大续期次数
     */
    public int getMaxRenewTimes() {
        return maxRenewTimes;
    }

    /**
     * 设置最大续期次数
     *
     * @param maxRenewTimes 最大续期次数
     */
    public void setMaxRenewTimes(int maxRenewTimes) {
        this.maxRenewTimes = maxRenewTimes;
    }

    /**
     * 获取指定锁的已续期次数
     *
     * @param lockKey 锁的键
     * @return 已续期次数，任务不存在返回 -1
     */
    public int getRenewCount(String lockKey) {
        WatchTask task = activeTasks.get(lockKey);
        return task != null ? task.renewCount : -1;
    }

    /**
     * 带重试机制的续期（根据锁类型选择对应脚本）
     *
     * <p>v1.2.0 变更：统一使用 {@link LockRenewalService} 执行续期，消除本地脚本冗余。
     *
     * @param lockKey   锁的键
     * @param clientId  客户端标识
     * @param leaseTime 锁的过期时间（毫秒）
     * @param lockType  锁类型，决定使用哪个续期脚本
     */
    private void renewLockWithRetry(String lockKey, String clientId, long leaseTime, LockType lockType) {
        WatchTask currentTask = activeTasks.get(lockKey);
        if (currentTask != null && currentTask.renewCount >= maxRenewTimes) {
            log.warn("[ydsz-lock] [watchdog]续期次数超过最大限制（{}次），停止续期，锁将自动过期 | lockKey={}", maxRenewTimes, lockKey);
            stopWatch(lockKey);
            return;
        }

        for (int retry = 0; retry < MAX_RETRY_COUNT; retry++) {
            try {
                boolean success = renewalService.renew(
                        stringRedisTemplate,
                        lockKey,
                        clientId,
                        leaseTime,
                        lockType
                );
                if (success) {
                    log.debug("[ydsz-lock] [watchdog]锁续期成功 | lockKey={} | lockType={}", lockKey, lockType);
                    WatchTask task = activeTasks.get(lockKey);
                    if (task != null) {
                        task.renewCount++;
                    }
                    if (lockMetrics != null) {
                        lockMetrics.recordWatchdogRenew(lockType.name().toLowerCase());
                    }
                    return;
                }
                log.warn("[ydsz-lock] [watchdog]锁续期失败，可能锁已释放 | lockKey={} | lockType={}", lockKey, lockType);
                stopWatch(lockKey);
                return;
            } catch (Exception e) {
                log.warn("[ydsz-lock] [watchdog]锁续期异常 | lockKey={} | lockType={} | retry={}/{} | error={}",
                        lockKey, lockType, retry + 1, MAX_RETRY_COUNT, e.getMessage());
                if (retry == MAX_RETRY_COUNT - 1) {
                    log.error("[ydsz-lock] [watchdog]锁续期最终失败，停止续期 | lockKey={} | lockType={}", lockKey, lockType);
                    stopWatch(lockKey);
                }
            }
        }
    }

}
