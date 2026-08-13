package com.njydsz.common.lock.strategy;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.scheduling.TaskScheduler;

import com.njydsz.common.lock.RedisReadWriteLock;
import com.njydsz.common.lock.RedisSemaphore;
import com.njydsz.common.lock.annotation.LockType;
import com.njydsz.common.lock.config.LockProperties;
import com.njydsz.common.lock.core.AbstractRedisDistributedLock;
import com.njydsz.common.lock.core.DistributedLocker;
import com.njydsz.common.lock.core.FencingTokenProvider;
import com.njydsz.common.lock.core.LockEventListener;
import com.njydsz.common.lock.impl.RedisFairLock;
import com.njydsz.common.lock.impl.RedisMultiLock;
import com.njydsz.common.lock.impl.RedisReentrantLock;
import com.njydsz.common.lock.metrics.LockMetrics;
import com.njydsz.common.lock.notify.LockReleaseNotifier;
import com.njydsz.common.lock.renewal.LockRenewalService;
import com.njydsz.common.lock.scheduler.LockWatchDog;
import com.njydsz.common.redis.service.ops.RedisStringOps;


/**
 * 默认锁策略实现
 *
 * <p>基于锁类型缓存的锁策略实现，根据 LockType 创建并缓存对应的分布式锁实例，
 * 避免重复创建锁对象，提高性能。
 *
 * <p><b>支持的锁类型：</b>
 * <ul>
 *   <li>REENTRANT - 可重入锁（默认）</li>
 *   <li>FAIR - 公平锁</li>
 * </ul>
 *
 * <p><b>不支持的锁类型：</b>{@link LockType#READ_WRITE} 与 {@link LockType#SEMAPHORE}
 * 为键维度实例，无法通过注解方式创建，请使用 {@link #getReadWriteLock} / {@link #getSemaphore}。
 *
 * @author ydsz-team
 * @since 1.0.0
 */
public class DefaultLockStrategy implements LockStrategy {

    /**
     * Redis 操作模板
     */
    private final StringRedisTemplate stringRedisTemplate;
    /**
     * 锁续期看门狗
     */
    private final LockWatchDog lockWatchDog;
    /**
     * Redis String 操作组件
     */
    private final RedisStringOps redisStringOps;
    /**
     * Redis 模板，用于执行 Lua 脚本
     */
    private final RedisTemplate<String, Object> redisTemplate;
    /**
     * 锁指标收集器
     */
    private final LockMetrics lockMetrics;
    /**
     * 调度线程池，用于信号量超时调度、多锁续期、读写锁续期
     */
    private final TaskScheduler scheduler;
    /**
     * 锁键命名空间前缀
     */
    private final String namespace;
    /**
     * 多 Key 联锁配置
     */
    private final LockProperties.MultiLock multiLockConfig;
    /**
     * 锁释放通知器（可选）
     */
    private final LockReleaseNotifier lockReleaseNotifier;

    /**
     * 统一锁续期服务（可选，注入后多锁启用批量续期）
     */
    private LockRenewalService lockRenewalService;

    /**
     * Fencing Token 提供器（可选，配置后支持单调递增 token 能力）
     */
    private FencingTokenProvider fencingTokenProvider;

    /**
     * 锁事件监听器（可选，用于感知锁生命周期事件）
     */
    private LockEventListener lockEventListener = LockEventListener.NO_OP;

    /**
     * 锁实例缓存，按锁类型缓存避免重复创建
     */
    private final Map<LockType, DistributedLocker> lockCache = new ConcurrentHashMap<>();

    /**
     * 默认锁过期时间（毫秒）
     */
    private static final long DEFAULT_EXPIRE_MILLIS = 30_000;
    /**
     * 默认锁等待时间（毫秒）
     */
    private static final long DEFAULT_WAIT_MILLIS = 5_000;

    /**
     * 完整构造器（包含命名空间前缀与多锁配置）
     *
     * @param stringRedisTemplate Redis 模板
     * @param lockWatchDog        看门狗
     * @param redisStringOps      Redis String 操作组件
     * @param redisTemplate       Redis 模板，用于执行 Lua 脚本
     * @param lockMetrics         锁指标收集器
     * @param scheduler           调度线程池
     * @param namespace           锁键命名空间前缀（${spring.application.name}）
     * @param multiLockConfig     多 Key 联锁配置
     * @param lockReleaseNotifier 锁释放通知器（可为 null）
     */
    public DefaultLockStrategy(StringRedisTemplate stringRedisTemplate, LockWatchDog lockWatchDog,
                               RedisStringOps redisStringOps, RedisTemplate<String, Object> redisTemplate,
                               LockMetrics lockMetrics, TaskScheduler scheduler, String namespace,
                               LockProperties.MultiLock multiLockConfig, LockReleaseNotifier lockReleaseNotifier) {
        this.stringRedisTemplate = stringRedisTemplate;
        this.lockWatchDog = lockWatchDog;
        this.redisStringOps = redisStringOps;
        this.redisTemplate = redisTemplate;
        this.lockMetrics = lockMetrics;
        this.scheduler = scheduler;
        this.namespace = namespace;
        this.multiLockConfig = multiLockConfig;
        this.lockReleaseNotifier = lockReleaseNotifier;
    }

    /**
     * 根据锁类型获取对应的分布式锁实例（带缓存）
     *
     * @param lockType 锁类型
     * @return 分布式锁实例
     * @throws IllegalArgumentException 当 lockType 为 READ_WRITE / SEMAPHORE 时抛出
     */
    @Override
    public DistributedLocker getLock(LockType lockType) {
        return lockCache.computeIfAbsent(lockType, this::createLock);
    }

    /**
     * 创建读写锁实例
     *
     * @param key 锁键
     * @return RedisReadWriteLock 实例
     * @throws IllegalStateException 当 RedisTemplate 未配置时
     */
    @Override
    public RedisReadWriteLock getReadWriteLock(String key) {
        if (redisTemplate == null) {
            throw new IllegalStateException(
                    "RedisTemplate is required for ReadWriteLock, please configure it in the constructor");
        }
        return new RedisReadWriteLock(redisStringOps, redisTemplate, key,
                DEFAULT_EXPIRE_MILLIS, DEFAULT_WAIT_MILLIS, namespace, scheduler);
    }

    /**
     * 创建信号量实例
     *
     * @param key     锁键
     * @param permits 许可数量
     * @return RedisSemaphore 实例
     * @throws IllegalStateException 当 RedisTemplate 未配置时
     */
    @Override
    public RedisSemaphore getSemaphore(String key, int permits) {
        if (redisTemplate == null) {
            throw new IllegalStateException(
                    "RedisTemplate is required for Semaphore, please configure it in the constructor");
        }
        return new RedisSemaphore(redisStringOps, redisTemplate, key, permits,
                DEFAULT_EXPIRE_MILLIS, scheduler, namespace);
    }

    /**
     * 创建读写锁实例（支持自定义过期时间和等待时间）
     *
     * @param key          锁键
     * @param expireMillis 锁过期时间（毫秒）
     * @param waitMillis   最大等待时间（毫秒）
     * @return RedisReadWriteLock 实例
     * @throws IllegalStateException 当 RedisTemplate 未配置时
     */
    public RedisReadWriteLock getReadWriteLock(String key, long expireMillis, long waitMillis) {
        if (redisTemplate == null) {
            throw new IllegalStateException(
                    "RedisTemplate is required for ReadWriteLock, please configure it in the constructor");
        }
        return new RedisReadWriteLock(redisStringOps, redisTemplate, key, expireMillis,
                waitMillis, namespace, scheduler);
    }

    /**
     * 设置统一锁续期服务（可选）
     *
     * <p>注入后多 Key 联锁自动启用批量续期，减少子锁续期的网络往返。</p>
     *
     * @param lockRenewalService 锁续期服务
     */
    public void setLockRenewalService(LockRenewalService lockRenewalService) {
        this.lockRenewalService = lockRenewalService;
    }

    /**
     * 设置 Fencing Token 提供器（可选）
     *
     * <p>配置后所创建的抽象锁实例将具备 fencing token 能力，
     * 通过单调递增 token 解决分布式锁的安全窗口问题。</p>
     *
     * @param fencingTokenProvider Fencing Token 提供器
     */
    public void setFencingTokenProvider(FencingTokenProvider fencingTokenProvider) {
        this.fencingTokenProvider = fencingTokenProvider;
    }

    /**
     * 设置锁事件监听器（可选）
     *
     * <p>配置后，锁生命周期事件（获取、释放、超时、续期失败）将通知监听器。</p>
     *
     * @param lockEventListener 锁事件监听器实例
     */
    public void setLockEventListener(LockEventListener lockEventListener) {
        this.lockEventListener = lockEventListener != null ? lockEventListener : LockEventListener.NO_OP;
    }

    /**
     * 创建多 Key 联锁实例
     *
     * @param locks 底层分布式锁列表（至少 2 个）
     * @return RedisMultiLock 实例
     */
    @Override
    public RedisMultiLock getMultiLock(List<DistributedLocker> locks) {
        LockProperties.MultiLock config = multiLockConfig != null ? multiLockConfig : new LockProperties.MultiLock();
        RedisMultiLock multiLock = new RedisMultiLock(locks, scheduler,
                config.getMaxRenewCount(), config.getRenewIntervalSeconds());
        if (lockRenewalService != null) {
            multiLock.setRenewalService(lockRenewalService);
            multiLock.setRedisTemplate(stringRedisTemplate);
        }
        return multiLock;
    }

    /**
     * 获取看门狗实例
     *
     * @return LockWatchDog 实例
     */
    @Override
    public LockWatchDog getWatchDog() {
        return lockWatchDog;
    }

    /**
     * 停止指定锁键的看门狗续期
     *
     * <p>将用户传入的锁键转换为带命名空间前缀的实际 Redis 键后停止看门狗。
     *
     * @param lockKey 锁的键（用户传入的原始键）
     */
    @Override
    public void stopWatchDog(String lockKey) {
        if (lockWatchDog == null) {
            return;
        }
        String namespacedKey = (namespace != null && !namespace.isEmpty())
                ? namespace + ":lock:" + lockKey : lockKey;
        lockWatchDog.stopWatch(namespacedKey);
    }

    /**
     * 根据锁类型创建对应的分布式锁实例
     *
     * @param lockType 锁类型
     * @return 分布式锁实例
     * @throws IllegalArgumentException 当 lockType 为 READ_WRITE / SEMAPHORE 时抛出
     */
    private DistributedLocker createLock(LockType lockType) {
        DistributedLocker lock = switch (lockType) {
            case REENTRANT -> new RedisReentrantLock(stringRedisTemplate, namespace);
            case FAIR -> new RedisFairLock(stringRedisTemplate, namespace);
            case READ_WRITE, SEMAPHORE -> throw new IllegalArgumentException(
                    "LockType." + lockType + " 为键维度实例，不支持注解方式使用，"
                            + "请通过 LockStrategy.getReadWriteLock / getSemaphore 获取");
        };
        if (lock instanceof AbstractRedisDistributedLock abstractLock) {
            if (lockWatchDog != null) {
                abstractLock.setLockWatchDog(lockWatchDog);
            }
            if (lockMetrics != null) {
                abstractLock.setLockMetrics(lockMetrics);
            }
            if (lockReleaseNotifier != null) {
                abstractLock.setLockReleaseNotifier(lockReleaseNotifier);
            }
            if (fencingTokenProvider != null) {
                abstractLock.setFencingTokenProvider(fencingTokenProvider);
            }
            if (lockEventListener != null) {
                abstractLock.setLockEventListener(lockEventListener);
            }
        }
        return lock;
    }
}
