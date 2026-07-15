package com.njydsz.pmis.common.lock.strategy;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.scheduling.TaskScheduler;

import com.njydsz.pmis.common.lock.RedisReadWriteLock;
import com.njydsz.pmis.common.lock.RedisSemaphore;
import com.njydsz.pmis.common.lock.annotation.LockType;
import com.njydsz.pmis.common.lock.core.AbstractRedisDistributedLock;
import com.njydsz.pmis.common.lock.core.DistributedLocker;
import com.njydsz.pmis.common.lock.impl.RedisFairLock;
import com.njydsz.pmis.common.lock.impl.RedisReentrantLock;
import com.njydsz.pmis.common.lock.metrics.LockMetrics;
import com.njydsz.pmis.common.lock.scheduler.LockWatchDog;
import com.njydsz.pmis.common.redis.service.RedisService;

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
 *   <li>READ_WRITE - 读写锁（需要 RedisService）</li>
 *   <li>SEMAPHORE - 信号量（需要 RedisService）</li>
 * </ul>
 *
 * @author ydsz-pmis-team
 * @since 1.0.0
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
     * Redis 服务，用于读写锁和信号量
     */
    private final RedisService redisService;
    /**
     * 锁指标收集器
     */
    private final LockMetrics lockMetrics;
    /**
     * 调度线程池，用于信号量超时调度
     */
    private final TaskScheduler scheduler;
    /**
     * 锁键命名空间前缀
     */
    private final String namespace;
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
     * 构造器（仅包含 StringRedisTemplate）
     *
     * @param stringRedisTemplate Redis 模板
     */
    public DefaultLockStrategy(StringRedisTemplate stringRedisTemplate) {
        this(stringRedisTemplate, null, null, null, null);
    }

    /**
     * 构造器（包含 StringRedisTemplate 和 LockWatchDog）
     *
     * @param stringRedisTemplate Redis 模板
     * @param lockWatchDog 看门狗
     */
    public DefaultLockStrategy(StringRedisTemplate stringRedisTemplate, LockWatchDog lockWatchDog) {
        this(stringRedisTemplate, lockWatchDog, null, null, null);
    }

    /**
     * 构造器（包含 StringRedisTemplate、LockWatchDog 和 RedisService）
     *
     * @param stringRedisTemplate Redis 模板
     * @param lockWatchDog 看门狗
     * @param redisService Redis 服务
     */
    public DefaultLockStrategy(StringRedisTemplate stringRedisTemplate, LockWatchDog lockWatchDog, RedisService redisService) {
        this(stringRedisTemplate, lockWatchDog, redisService, null, null);
    }

    /**
     * 构造器（包含 StringRedisTemplate、LockWatchDog、RedisService 和调度器）
     *
     * @param stringRedisTemplate Redis 模板
     * @param lockWatchDog 看门狗
     * @param redisService Redis 服务
     * @param scheduler 调度线程池（用于信号量超时调度）
     */
    public DefaultLockStrategy(StringRedisTemplate stringRedisTemplate, LockWatchDog lockWatchDog, RedisService redisService,
                               TaskScheduler scheduler) {
        this(stringRedisTemplate, lockWatchDog, redisService, null, scheduler);
    }

    /**
     * 完整构造器（支持 LockMetrics 指标注入）
     *
     * @param stringRedisTemplate Redis 模板
     * @param lockWatchDog 看门狗
     * @param redisService Redis 服务
     * @param lockMetrics 锁指标收集器
     * @param scheduler 调度线程池（用于信号量超时调度）
     */
    public DefaultLockStrategy(StringRedisTemplate stringRedisTemplate, LockWatchDog lockWatchDog,
                               RedisService redisService, LockMetrics lockMetrics, TaskScheduler scheduler) {
        this(stringRedisTemplate, lockWatchDog, redisService, lockMetrics, scheduler, null);
    }

    /**
     * 完整构造器（包含命名空间前缀）
     *
     * @param stringRedisTemplate Redis 模板
     * @param lockWatchDog 看门狗
     * @param redisService Redis 服务
     * @param lockMetrics 锁指标收集器
     * @param scheduler 调度线程池（用于信号量超时调度）
     * @param namespace 锁键命名空间前缀（${spring.application.name}）
     */
    public DefaultLockStrategy(StringRedisTemplate stringRedisTemplate, LockWatchDog lockWatchDog,
                               RedisService redisService, LockMetrics lockMetrics, TaskScheduler scheduler, String namespace) {
        this.stringRedisTemplate = stringRedisTemplate;
        this.lockWatchDog = lockWatchDog;
        this.redisService = redisService;
        this.lockMetrics = lockMetrics;
        this.scheduler = scheduler;
        this.namespace = namespace;
    }

    /**
     * 根据锁类型获取对应的分布式锁实例（带缓存）
     *
     * @param lockType 锁类型
     * @return 分布式锁实例
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
     * @throws IllegalStateException 当 RedisService 未配置时
     */
    @Override
    public RedisReadWriteLock getReadWriteLock(String key) {
        if (redisService == null) {
            throw new IllegalStateException("RedisService is required for ReadWriteLock, please configure it in the constructor");
        }
        return new RedisReadWriteLock(redisService, key, DEFAULT_EXPIRE_MILLIS, DEFAULT_WAIT_MILLIS, namespace);
    }

    /**
     * 创建信号量实例
     *
     * @param key 锁键
     * @param permits 许可数量
     * @return RedisSemaphore 实例
     * @throws IllegalStateException 当 RedisService 未配置时
     */
    @Override
    public RedisSemaphore getSemaphore(String key, int permits) {
        if (redisService == null) {
            throw new IllegalStateException("RedisService is required for Semaphore, please configure it in the constructor");
        }
        return new RedisSemaphore(redisService, key, permits, DEFAULT_EXPIRE_MILLIS, scheduler, namespace);
    }

    /**
     * 创建读写锁实例（支持自定义过期时间和等待时间）
     *
     * @param key 锁键
     * @param expireMillis 锁过期时间（毫秒）
     * @param waitMillis 最大等待时间（毫秒）
     * @return RedisReadWriteLock 实例
     * @throws IllegalStateException 当 RedisService 未配置时
     */
    public RedisReadWriteLock getReadWriteLock(String key, long expireMillis, long waitMillis) {
        if (redisService == null) {
            throw new IllegalStateException("RedisService is required for ReadWriteLock, please configure it in the constructor");
        }
        return new RedisReadWriteLock(redisService, key, expireMillis, waitMillis, namespace);
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
     */
    private DistributedLocker createLock(LockType lockType) {
        DistributedLocker lock = switch (lockType) {
            case REENTRANT -> new RedisReentrantLock(stringRedisTemplate, namespace);
            case FAIR -> new RedisFairLock(stringRedisTemplate, namespace);
            default -> new RedisReentrantLock(stringRedisTemplate, namespace);
        };
        if (lock instanceof AbstractRedisDistributedLock abstractLock) {
            if (lockWatchDog != null) {
                abstractLock.setLockWatchDog(lockWatchDog);
            }
            if (lockMetrics != null) {
                abstractLock.setLockMetrics(lockMetrics);
            }
        }
        return lock;
    }
}
