package com.njydsz.common.redis.service;

import java.time.Duration;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.LockSupport;
import java.util.function.Supplier;


import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;

import com.njydsz.common.redis.service.ops.RedisStringOps;

/**
 * Redis 缓存防护工具类
 *
 * <p>提供三大缓存保护策略：</p>
 * <ul>
 *   <li><b>防穿透</b>：布隆过滤器模式，对不存在的数据进行空值缓存，防止大量不存在的 key 打到数据库</li>
 *   <li><b>防击穿</b>：分布式锁模式，对热点 key 在缓存失效时只允许一个线程回源，防止瞬时高并发打到数据库</li>
 *   <li><b>防雪崩</b>：随机过期时间，在基础 TTL 上叠加随机扰动，防止大量缓存同时失效</li>
 * </ul>
 *
 * <p><b>使用示例：</b></p>
 * <pre>{@code
 * // 防穿透 - 缓存空值
 * User user = cacheGuard.antiPenetration(
 *     "user:" + id,
 *     () -> userMapper.selectById(id),
 *     User.class
 * );
 *
 * // 防击穿 - 分布式锁保护热点 key
 * Product product = cacheGuard.antiBreakdown(
 *     "product:hot:" + id,
 *     300,
 *     () -> productService.getById(id),
 *     Product.class
 * );
 *
 * // 防雪崩 - 随机 TTL
 * List<Order> orders = cacheGuard.antiAvalanche(
 *     "user:orders:" + userId,
 *     600,
 *     300000,  // 最多额外 5 分钟随机
 *     () -> orderMapper.selectByUserId(userId),
 *     List.class
 * );
 * }</pre>
 *
 * <p><b>防击穿锁实现说明：</b></p>
 * <p>使用 {@link LockWatchDog} 公共组件实现锁续期机制，
 * 确保锁在缓存重建期间不会因业务执行时间超过 leaseTime 而自动释放。
 * 通过抽取公共 LockWatchDog 组件，避免各组件各自实现 WatchDog 导致的代码重复。</p>
 *
 * @author ydsz-team
 * @since 1.0.0
 */
@Slf4j
public class RedisCacheGuard {

    private static final String NULL_PLACEHOLDER = "__NULL__";
    private static final String PENETRATION_LOCK_PREFIX = "cache:guard:penetration:";
    private static final String BREAKDOWN_LOCK_PREFIX = "cache:guard:breakdown:";

    /** 自旋等待最大时长（毫秒），等待持锁线程回填缓存 */
    private static final long SPIN_MAX_WAIT_MS = 3000;
    /** 自旋等待初始退避间隔（毫秒） */
    private static final long SPIN_INITIAL_BACKOFF_MS = 20;
    /** 自旋等待最大退避间隔（毫秒） */
    private static final long SPIN_MAX_BACKOFF_MS = 500;
    /** 防击穿锁等待获取最大时长（毫秒） */
    private static final long LOCK_WAIT_MS = 2000;
    /** 防击穿锁等待获取初始退避间隔（毫秒） */
    private static final long LOCK_WAIT_INITIAL_BACKOFF_MS = 10;
    /** 防击穿锁等待获取最大退避间隔（毫秒） */
    private static final long LOCK_WAIT_MAX_BACKOFF_MS = 200;
    /** 防穿透锁租约时间（秒） */
    private static final int PENETRATION_LOCK_LEASE_SECONDS = 5;
    /** 防击穿锁租约时间（秒） */
    private static final int BREAKDOWN_LOCK_LEASE_SECONDS = 10;

    private final RedisStringOps stringOps;
    private final RedisTemplate<String, Object> redisTemplate;
    private final int nullValueTtlSeconds;

    /**
     * WatchDog 续期调度器（公共组件），用于防击穿锁的自动续期
     */
    private final LockWatchDog lockWatchDog;

    public RedisCacheGuard(RedisStringOps stringOps, RedisTemplate<String, Object> redisTemplate) {
        this(stringOps, redisTemplate, 1800);
    }

    public RedisCacheGuard(RedisStringOps stringOps, RedisTemplate<String, Object> redisTemplate,
                          int nullValueTtlSeconds) {
        this.stringOps = stringOps;
        this.redisTemplate = redisTemplate;
        this.nullValueTtlSeconds = nullValueTtlSeconds;
        this.lockWatchDog = new LockWatchDog(redisTemplate);
    }

    /**
     * 优雅关闭：停止所有 WatchDog 续期任务并关闭调度器
     *
     * <p>由 Spring 容器在销毁时自动调用（当 RedisCacheGuard 作为 Bean 注册时）。
     * 对于手动 new 的场景，调用方应主动调用 {@link #shutdown()}。</p>
     */
    @PreDestroy
    public void shutdown() {
        lockWatchDog.shutdown();
        log.info("【RedisCacheGuard】WatchDog 调度器已关闭");
    }

    /**
     * 防缓存穿透 - 布隆过滤器模式（空值缓存）
     *
     * <p>当查询结果为 null 时，写入一个特殊的空值标记到缓存中，
     * 设置较短的过期时间。后续相同 key 的查询直接返回 null，
     * 避免大量不存在的 key 反复打到数据库。</p>
     *
     * @param key      缓存 key
     * @param supplier 回源加载数据的逻辑（查询数据库等）
     * @param clazz    返回值类型
     * @param <T>      数据类型
     * @return 查询结果，不存在时返回 null
     */
    public <T> T antiPenetration(String key, Supplier<T> supplier, Class<T> clazz) {
        return antiPenetration(key, supplier, clazz, 60);
    }

    /**
     * 防缓存穿透 - 布隆过滤器模式（空值缓存）
     *
     * @param key           缓存 key
     * @param supplier      回源加载数据的逻辑
     * @param clazz         返回值类型
     * @param nullCacheSec  空值缓存时长（秒），默认 60 秒
     * @param <T>           数据类型
     * @return 查询结果，不存在时返回 null
     */
    public <T> T antiPenetration(String key, Supplier<T> supplier, Class<T> clazz, int nullCacheSec) {
        // 先尝试从缓存获取
        Object cached = stringOps.get(key);
        if (cached != null) {
            if (NULL_PLACEHOLDER.equals(cached)) {
                log.debug("【RedisCacheGuard】命中空值缓存 | key={}", key);
                return null;
            }
            if (clazz.isInstance(cached)) {
                return clazz.cast(cached);
            }
            return stringOps.get(key, clazz);
        }

        // 缓存未命中，使用分布式锁防止并发穿透
        String lockKey = PENETRATION_LOCK_PREFIX + key;
        String lockValue = null;
        try {
            lockValue = acquireLock(lockKey, PENETRATION_LOCK_LEASE_SECONDS);
            if (lockValue != null) {
                // 获取锁成功，双重检查缓存
                cached = stringOps.get(key);
                if (cached != null) {
                    return NULL_PLACEHOLDER.equals(cached) ? null : stringOps.get(key, clazz);
                }

                // 回源查询数据库
                T data = supplier.get();
                if (data == null) {
                    stringOps.set(key, NULL_PLACEHOLDER, nullCacheSec);
                    log.info("【RedisCacheGuard】设置空值缓存 | key={} | ttl={}s", key, nullCacheSec);
                    return null;
                } else {
                    stringOps.set(key, data, nullValueTtlSeconds);
                    return data;
                }
            } else {
                // 未获取到锁，自旋等待缓存就绪（指数退避），与防击穿保持一致
                return spinWaitForCacheOrPenetration(key, supplier, clazz, nullCacheSec);
            }
        } finally {
            if (lockValue != null) {
                releaseLock(lockKey, lockValue);
            }
        }
    }

    /**
     * 防缓存击穿 - 分布式锁模式（带 WatchDog 续期 + singleflight）
     *
     * <p>针对热点 key 在缓存失效瞬间，大量请求同时打到数据库的问题。
     * 使用带 WatchDog 续期的分布式锁确保同一时刻只有一个线程回源加载数据，
     * 锁在缓存重建期间不会因业务执行时间超过 leaseTime 而自动释放。</p>
     *
     * <p><b>singleflight 机制：</b>等待锁的线程在获取到锁后，会先检查缓存是否已被
     * 第一个线程填充，如已被填充则直接返回缓存值，不再重建，避免重复回源。</p>
     *
     * @param key      缓存 key
     * @param expire   缓存过期时间（秒）
     * @param supplier 回源加载数据的逻辑
     * @param clazz    返回值类型
     * @param <T>      数据类型
     * @return 查询结果
     */
    public <T> T antiBreakdown(String key, long expire, Supplier<T> supplier, Class<T> clazz) {
        // 先尝试从缓存获取
        Object cached = stringOps.get(key);
        if (cached != null) {
            if (clazz.isInstance(cached)) {
                return clazz.cast(cached);
            }
            return stringOps.get(key, clazz);
        }

        // 缓存失效，使用分布式锁保护（带 WatchDog 续期 + 等待重试，实现 singleflight）
        String lockKey = BREAKDOWN_LOCK_PREFIX + key;
        String lockValue = null;
        try {
            // 尝试获取锁（带等待），获取后 WatchDog 自动续期，防止重建期间锁过期
            lockValue = acquireLockWithWait(lockKey, BREAKDOWN_LOCK_LEASE_SECONDS, LOCK_WAIT_MS);
            if (lockValue != null) {
                // 获取锁成功，双重检查缓存（singleflight：可能在等待锁期间缓存已被其他线程填充）
                cached = stringOps.get(key);
                if (cached != null) {
                    log.debug("【RedisCacheGuard】singleflight 命中已回填缓存，复用结果 | key={}", key);
                    if (clazz.isInstance(cached)) {
                        return clazz.cast(cached);
                    }
                    return stringOps.get(key, clazz);
                }

                // 回源查询
                T data = supplier.get();
                if (data != null) {
                    stringOps.set(key, data, expire);
                    log.info("【RedisCacheGuard】防击穿缓存回填 | key={} | ttl={}s", key, expire);
                }
                return data;
            } else {
                // 等待锁超时，自旋等待缓存就绪（指数退避），避免大量线程同时回源击穿数据库
                return spinWaitForCache(key, supplier, clazz);
            }
        } finally {
            if (lockValue != null) {
                releaseLock(lockKey, lockValue);
            }
        }
    }

    /**
     * 防缓存雪崩 - 随机过期时间
     *
     * <p>在基础 TTL 上叠加随机扰动（0 ~ randomJitterMs），
     * 使不同 key 的过期时间分散开，避免大量缓存在同一时刻失效。</p>
     *
     * @param key            缓存 key
     * @param expire         基础过期时间（秒）
     * @param randomJitterMs 随机扰动范围（毫秒），会在 0~randomJitterMs 之间随机叠加
     * @param supplier       回源加载数据的逻辑
     * @param clazz          返回值类型
     * @param <T>            数据类型
     * @return 查询结果
     */
    public <T> T antiAvalanche(String key, long expire, long randomJitterMs,
                                Supplier<T> supplier, Class<T> clazz) {
        // 先尝试从缓存获取
        Object cached = stringOps.get(key);
        if (cached != null) {
            if (clazz.isInstance(cached)) {
                return clazz.cast(cached);
            }
            return stringOps.get(key, clazz);
        }

        // 缓存未命中，回源查询
        T data = supplier.get();
        if (data != null) {
            long jitterSeconds = TimeUnit.MILLISECONDS.toSeconds(ThreadLocalRandom.current().nextLong(Math.max(1, randomJitterMs)));
            long expireWithJitter = expire + jitterSeconds;
            stringOps.set(key, data, expireWithJitter);
            log.debug("【RedisCacheGuard】防雪崩缓存写入 | key={} | baseExpire={}s | jitter={}s | total={}s",
                    key, expire, jitterSeconds, expireWithJitter);
        }
        return data;
    }

    /**
     * 防穿透模式最大退避间隔（毫秒）
     */
    private static final long PENETRATION_SPIN_MAX_BACKOFF_MS = 200;

    /**
     * 自旋等待缓存就绪（指数退避），用于防穿透锁未获取时等待持锁线程回填
     *
     * <p>与 {@link #spinWaitForCache} 类似，但额外处理空值标记：
     * 当缓存中为 {@link #NULL_PLACEHOLDER} 时表示数据确实不存在，直接返回 null。</p>
     *
     * <p>使用 {@link LockSupport#parkNanos} 替代 {@link Thread.sleep}，避免持有监视器锁，
     * 减少对业务线程池的阻塞影响。</p>
     *
     * @param key        缓存 key
     * @param supplier   降级回源逻辑（自旋超时后调用）
     * @param clazz      返回值类型
     * @param nullCacheSec 空值缓存时长（秒），降级回源后若结果为 null 则写入空值标记
     * @param <T>        数据类型
     * @return 查询结果
     */
    private <T> T spinWaitForCacheOrPenetration(String key, Supplier<T> supplier,
                                                 Class<T> clazz, int nullCacheSec) {
        final long spinStart = System.currentTimeMillis();
        long backoffMs = SPIN_INITIAL_BACKOFF_MS;
        while (true) {
            Object cached = stringOps.get(key);
            if (cached != null) {
                if (NULL_PLACEHOLDER.equals(cached)) {
                    log.debug("【RedisCacheGuard】自旋等待命中空值缓存 | key={}", key);
                    return null;
                }
                log.debug("【RedisCacheGuard】自旋等待命中缓存 | key={}", key);
                if (clazz.isInstance(cached)) {
                    return clazz.cast(cached);
                }
                return stringOps.get(key, clazz);
            }
            long elapsed = System.currentTimeMillis() - spinStart;
            if (elapsed >= SPIN_MAX_WAIT_MS) {
                log.warn("【RedisCacheGuard】防穿透自旋等待超时，降级直接回源 | key={}", key);
                T data = supplier.get();
                if (data == null) {
                    stringOps.set(key, NULL_PLACEHOLDER, nullCacheSec);
                }
                return data;
            }
            long sleepMs = Math.min(backoffMs, SPIN_MAX_WAIT_MS - elapsed);
            if (sleepMs > 0) {
                LockSupport.parkNanos(sleepMs * 1_000_000L);
            }
            if (Thread.currentThread().isInterrupted()) {
                log.warn("【RedisCacheGuard】防穿透自旋等待被中断，降级直接回源 | key={}", key);
                return supplier.get();
            }
            backoffMs = Math.min(backoffMs * 2, PENETRATION_SPIN_MAX_BACKOFF_MS);
        }
    }

    /**
     * 防击穿模式最大退避间隔（毫秒）
     */
    private static final long BREAKDOWN_SPIN_MAX_BACKOFF_MS = 200;

    /**
     * 自旋等待缓存就绪（指数退避），用于防击穿锁等待超时后避免直接回源
     *
     * <p>当获取锁超时时不立即调用 {@code supplier.get()}，而是以指数退避方式自旋检查缓存，
     * 等待持锁线程回填缓存。仅当自旋等待也超时后才降级直接回源，
     * 避免大量等待线程同时击穿数据库。</p>
     *
     * <p>使用 {@link LockSupport#parkNanos} 替代 {@link Thread.sleep}，避免持有监视器锁，
     * 减少对业务线程池的阻塞影响。</p>
     *
     * @param key      缓存 key
     * @param supplier 降级回源逻辑（自旋超时后调用）
     * @param clazz    返回值类型
     * @param <T>      数据类型
     * @return 查询结果
     */
    private <T> T spinWaitForCache(String key, Supplier<T> supplier, Class<T> clazz) {
        final long spinStart = System.currentTimeMillis();
        long backoffMs = SPIN_INITIAL_BACKOFF_MS;
        while (true) {
            Object cached = stringOps.get(key);
            if (cached != null) {
                log.debug("【RedisCacheGuard】自旋等待命中缓存 | key={}", key);
                if (clazz.isInstance(cached)) {
                    return clazz.cast(cached);
                }
                return stringOps.get(key, clazz);
            }
            long elapsed = System.currentTimeMillis() - spinStart;
            if (elapsed >= SPIN_MAX_WAIT_MS) {
                log.warn("【RedisCacheGuard】自旋等待超时，降级直接回源 | key={}", key);
                return supplier.get();
            }
            long sleepMs = Math.min(backoffMs, SPIN_MAX_WAIT_MS - elapsed);
            if (sleepMs > 0) {
                LockSupport.parkNanos(sleepMs * 1_000_000L);
            }
            if (Thread.currentThread().isInterrupted()) {
                log.warn("【RedisCacheGuard】自旋等待被中断，降级直接回源 | key={}", key);
                return supplier.get();
            }
            backoffMs = Math.min(backoffMs * 2, BREAKDOWN_SPIN_MAX_BACKOFF_MS);
        }
    }

    /**
     * 获取分布式锁（非阻塞，快速失败），获取成功后启动 WatchDog 自动续期
     *
     * @param lockKey   锁键
     * @param leaseTime 锁租约时间（秒）
     * @return 锁值（获取成功）或 null（获取失败）
     */
    private String acquireLock(String lockKey, int leaseTime) {
        try {
            String lockValue = UUID.randomUUID().toString().replace("-", "");
            // 使用 redisTemplate 直接操作，与 releaseLock/lockWatchDog 保持 key 处理一致
            Boolean locked = redisTemplate.opsForValue().setIfAbsent(
                    lockKey, lockValue, Duration.ofSeconds(leaseTime));
            if (Boolean.TRUE.equals(locked)) {
                // 启动 WatchDog 自动续期，防止业务执行时间超过 leaseTime 导致锁自动释放
                lockWatchDog.start(lockKey, lockValue, leaseTime * 1000L);
                return lockValue;
            }
            return null;
        } catch (Exception e) {
            log.error("【RedisCacheGuard】获取防护锁失败 | key={}", lockKey, e);
            return null;
        }
    }

    /**
     * 获取分布式锁（带等待重试），实现 singleflight 等待语义
     *
     * <p>使用指数退避策略在 waitMs 内反复尝试获取锁，获取成功后 WatchDog 自动续期。
     * 调用方在获取锁成功后应进行缓存双重检查，复用其他线程已回填的结果。</p>
     *
     * @param lockKey   锁键
     * @param leaseTime 锁租约时间（秒）
     * @param waitMs    最大等待时间（毫秒）
     * @return 锁值（获取成功）或 null（等待超时）
     */
    private String acquireLockWithWait(String lockKey, int leaseTime, long waitMs) {
        long startTime = System.currentTimeMillis();
        long backoff = LOCK_WAIT_INITIAL_BACKOFF_MS;
        while (true) {
            String lockValue = acquireLock(lockKey, leaseTime);
            if (lockValue != null) {
                return lockValue;
            }
            long elapsed = System.currentTimeMillis() - startTime;
            if (elapsed >= waitMs) {
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
            backoff = Math.min(backoff * 2, LOCK_WAIT_MAX_BACKOFF_MS);
        }
    }

    /**
     * 释放分布式锁（通过 Lua 脚本原子比较并删除），并停止 WatchDog 续期
     *
     * @param lockKey   锁键
     * @param lockValue 锁值
     */
    private void releaseLock(String lockKey, String lockValue) {
        lockWatchDog.release(lockKey, lockValue);
    }
}
