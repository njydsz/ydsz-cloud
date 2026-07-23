package com.njydsz.common.lock;

import java.util.Arrays;
import java.util.Collections;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReadWriteLock;

import com.njydsz.common.cache.YdszCache;
import com.njydsz.common.cache.api.Cache;
import com.njydsz.common.cache.builder.CacheType;
import com.njydsz.common.lock.core.DistributedLocker;
import com.njydsz.common.redis.service.RedisService;

import lombok.extern.slf4j.Slf4j;

/**
 * 基于 Redis + Lua 脚本的分布式读写锁
 * 使用 Lua 脚本保证原子操作，解决并发安全问题
 *
 * <p>并发安全保证：
 * <ul>
 *   <li>读锁获取：Lua 原子检查写锁不存在 + incr 读锁计数器</li>
 *   <li>读锁释放：Lua 原子 decr 读锁计数器，计数器归零时删除 key</li>
 *   <li>写锁获取：Lua 原子检查无写锁且读锁计数器为0 + 设置写锁</li>
 *   <li>写锁释放：Lua 原子校验 lockValue 匹配 + 删除写锁 key</li>
 * </ul>
 *
 * <p><b>内存安全：</b>
 * 使用 {@link YdszCache} 替代 {@link ThreadLocal}，通过 TTL（30 分钟）和最大容量（10,000）
 * 自动清理，彻底避免线程池复用场景下的 ThreadLocal 内存泄漏。
 *
 * <p><b>等待策略：</b>
 * 使用指数退避策略（10ms → 200ms）替代固定 50ms 轮询，减少无效 Redis 调用。
 *
 * <p>自 v3.5.1 起实现 {@link DistributedLocker} 接口，
 * 可纳入 {@link com.njydsz.common.lock.strategy.LockStrategy} 统一管理。
 *
 * @since 1.0.0
 *
 * @since 3.0.0
 */
@Slf4j
public class RedisReadWriteLock implements ReadWriteLock, DistributedLocker {

    /**
     * 最小退避等待时间（毫秒）
     */
    private static final long MIN_BACKOFF_MILLIS = 10;

    /**
     * 最大退避等待时间（毫秒）
     */
    private static final long MAX_BACKOFF_MILLIS = 200;

    /**
     * Redis 服务，用于执行 Lua 脚本
     */
    private final RedisService redisService;
    /**
     * 读锁 Redis Key
     */
    private final String readLockKey;
    /**
     * 写锁 Redis Key
     */
    private final String writeLockKey;
    /**
     * 锁过期时间（毫秒）
     */
    private final long expireMillis;
    /**
     * 获取锁最大等待时间（毫秒）
     */
    private final long waitMillis;

    /**
     * 当前线程持有的读锁 lockValue，用于读锁重入
     * <p>使用 ydsz-common-cache 替代 ThreadLocal，通过 TTL 和最大容量自动清理，
     * 彻底避免线程池复用场景下的内存泄漏。
     */
    private final Cache<String, String> readLockValueCache = YdszCache.<String, String>newBuilder()
            .type(CacheType.STRIPED)
            .expireAfterWrite(30, TimeUnit.MINUTES)
            .maximumSize(10_000)
            .build();

    /**
     * 当前线程的读锁重入计数
     * <p>使用 ydsz-common-cache 替代 ThreadLocal，通过 TTL 和最大容量自动清理。
     */
    private final Cache<String, Integer> readLockCountCache = YdszCache.<String, Integer>newBuilder()
            .type(CacheType.STRIPED)
            .expireAfterWrite(30, TimeUnit.MINUTES)
            .maximumSize(10_000)
            .build();

    /**
     * 当前线程持有的写锁 lockValue，用于 DistributedLocker 接口方法
     * <p>使用 ydsz-common-cache 替代 ThreadLocal，通过 TTL 和最大容量自动清理。
     */
    private final Cache<String, String> writeLockValueCache = YdszCache.<String, String>newBuilder()
            .type(CacheType.STRIPED)
            .expireAfterWrite(30, TimeUnit.MINUTES)
            .maximumSize(10_000)
            .build();

    /**
     * 获取读锁 Lua 脚本：检查写锁不存在时，设置读锁计数器并设置过期时间
     */
    private static final String READ_LOCK_ACQUIRE_SCRIPT =
            "if redis.call('exists', KEYS[2]) == 0 then " +
                    "redis.call('hset', KEYS[1], ARGV[1], '1') " +
                    "redis.call('pexpire', KEYS[1], ARGV[2]) " +
                    "return 1 " +
                    "else return 0 end";

    /**
     * 释放读锁 Lua 脚本：删除读锁计数器字段，计数器归零时删除整个 key
     */
    private static final String READ_LOCK_RELEASE_SCRIPT =
            "if redis.call('hexists', KEYS[1], ARGV[1]) == 1 then " +
                    "redis.call('hdel', KEYS[1], ARGV[1]) " +
                    "if redis.call('hlen', KEYS[1]) == 0 then redis.call('del', KEYS[1]) end " +
                    "return 1 " +
                    "else return 0 end";

    /**
     * 获取写锁 Lua 脚本：检查无写锁且读锁计数器为 0 时，设置写锁
     */
    private static final String WRITE_LOCK_ACQUIRE_SCRIPT =
            "local readCount = redis.call('hlen', KEYS[1]) " +
                    "if redis.call('exists', KEYS[2]) == 0 and (readCount == false or tonumber(readCount) == 0) then " +
                    "redis.call('set', KEYS[2], ARGV[1], 'PX', ARGV[2], 'NX') " +
                    "return 1 " +
                    "else return 0 end";

    /**
     * 释放写锁 Lua 脚本：校验 lockValue 匹配后删除写锁 key
     */
    private static final String WRITE_LOCK_RELEASE_SCRIPT =
            "if redis.call('get', KEYS[1]) == ARGV[1] then " +
                    "return redis.call('del', KEYS[1]) " +
                    "else return 0 end";

    /**
     * 构造分布式读写锁（无命名空间）
     *
     * @param redisService Redis 服务
     * @param key          锁键
     * @param expireMillis 锁过期时间（毫秒）
     * @param waitMillis   获取锁最大等待时间（毫秒）
     */
    public RedisReadWriteLock(RedisService redisService, String key,
                               long expireMillis, long waitMillis) {
        this(redisService, key, expireMillis, waitMillis, null);
    }

    /**
     * 构造分布式读写锁（带命名空间）
     *
     * @param redisService Redis 服务
     * @param key          锁键
     * @param expireMillis 锁过期时间（毫秒）
     * @param waitMillis   获取锁最大等待时间（毫秒）
     * @param namespace    锁键命名空间前缀，用于多应用共享 Redis 时的隔离
     */
    public RedisReadWriteLock(RedisService redisService, String key,
                               long expireMillis, long waitMillis, String namespace) {
        this.redisService = redisService;
        String prefix = (namespace != null && !namespace.isEmpty()) ? namespace + ":lock:" : "";
        this.readLockKey = prefix + "rlock:" + key;
        this.writeLockKey = prefix + "wlock:" + key;
        this.expireMillis = expireMillis;
        this.waitMillis = waitMillis;
    }

    /**
     * 构建线程级缓存键
     *
     * @return 基于 threadId 的缓存键
     */
    private static String threadCacheKey() {
        return String.valueOf(Thread.currentThread().threadId());
    }

    /**
     * 指数退避等待
     *
     * @param deadline       截止时间戳
     * @param currentBackoff 当前退避时间
     * @return 下一次退避时间
     */
    private static long backoffSleep(long deadline, long currentBackoff) {
        long remaining = deadline - System.currentTimeMillis();
        if (remaining <= 0) {
            return currentBackoff;
        }
        long sleepMillis = Math.min(remaining, currentBackoff);
        if (sleepMillis > 0) {
            try {
                Thread.sleep(sleepMillis);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
        return Math.min(currentBackoff * 2, MAX_BACKOFF_MILLIS);
    }

    @Override
    public Lock readLock() {
        return new RedisReadLock();
    }

    @Override
    public Lock writeLock() {
        return new RedisWriteLock();
    }

    /**
     * Redis 分布式读锁实现
     * <p>支持读锁重入，同一线程可多次获取读锁
     */
    private class RedisReadLock implements Lock {

        /**
         * 获取读锁，阻塞直到获取成功或超过最大等待时间。
         */
        @Override
        public void lock() {
            tryLock(waitMillis, TimeUnit.MILLISECONDS);
        }

        /**
         * 获取读锁，响应线程中断（当前实现等同于 {@link #lock()}）。
         *
         * @throws InterruptedException 线程被中断时抛出
         */
        @Override
        public void lockInterruptibly() throws InterruptedException {
            lock();
        }

        /**
         * 尝试获取读锁（不等待），支持读锁重入。
         *
         * @return true-获取成功，false-获取失败
         */
        @Override
        public boolean tryLock() {
            return tryLock(0, TimeUnit.MILLISECONDS);
        }

        /**
         * 尝试在指定时间内获取读锁，支持读锁重入。
         *
         * <p>当前线程已持有读锁时直接增加重入计数；否则通过 Lua 脚本原子性获取读锁。
         * 使用指数退避策略（10ms → 200ms）替代固定轮询间隔，减少无效 Redis 调用。
         *
         * @param time 最大等待时间
         * @param unit 时间单位
         * @return true-获取成功，false-超时未获取
         */
        @Override
        public boolean tryLock(long time, TimeUnit unit) {
            String cacheKey = threadCacheKey();

            // 重入检查：当前线程已持有读锁，直接增加重入计数
            String existingLockValue = readLockValueCache.getIfPresent(cacheKey);
            if (existingLockValue != null) {
                Integer count = readLockCountCache.getIfPresent(cacheKey);
                readLockCountCache.put(cacheKey, (count != null ? count : 0) + 1);
                return true;
            }

            // 首次获取读锁
            String lockValue = UUID.randomUUID().toString();
            long deadline = System.currentTimeMillis() + unit.toMillis(time);
            long currentBackoff = MIN_BACKOFF_MILLIS;
            while (true) {
                try {
                    Long result = redisService.executeScript(
                            READ_LOCK_ACQUIRE_SCRIPT,
                            Arrays.asList(readLockKey, writeLockKey),
                            Long.class,
                            lockValue,
                            String.valueOf(expireMillis)
                    );
                    if (result != null && result == 1L) {
                        readLockValueCache.put(cacheKey, lockValue);
                        readLockCountCache.put(cacheKey, 1);
                        return true;
                    }
                } catch (Exception e) {
                    log.warn("读锁获取异常: {}", readLockKey, e);
                }
                if (System.currentTimeMillis() >= deadline) {
                    return false;
                }
                currentBackoff = backoffSleep(deadline, currentBackoff);
            }
        }

        /**
         * 释放读锁，支持重入递减，重入计数归零时真正释放 Redis 读锁。
         */
        @Override
        public void unlock() {
            String cacheKey = threadCacheKey();
            String lockValue = readLockValueCache.getIfPresent(cacheKey);
            if (lockValue == null) {
                return;
            }
            Integer countVal = readLockCountCache.getIfPresent(cacheKey);
            int count = (countVal != null ? countVal : 1) - 1;
            if (count > 0) {
                readLockCountCache.put(cacheKey, count);
                return;
            }
            // 重入计数归零，真正释放锁
            try {
                redisService.executeScript(
                        READ_LOCK_RELEASE_SCRIPT,
                        Collections.singletonList(readLockKey),
                        Long.class,
                        lockValue
                );
            } catch (Exception e) {
                log.error("读锁释放异常: {}", readLockKey, e);
            } finally {
                readLockValueCache.invalidate(cacheKey);
                readLockCountCache.invalidate(cacheKey);
            }
        }

        /**
         * 读锁不支持 Condition 条件。
         *
         * @throws UnsupportedOperationException 始终抛出
         */
        @Override
        public Condition newCondition() {
            throw new UnsupportedOperationException("ReadLock does not support conditions");
        }
    }

    /**
     * Redis 分布式写锁实现
     * <p>写锁互斥，同一时刻只允许一个线程持有
     */
    private class RedisWriteLock implements Lock {

        /**
         * 获取写锁，阻塞直到获取成功或超过最大等待时间。
         */
        @Override
        public void lock() {
            tryLock(waitMillis, TimeUnit.MILLISECONDS);
        }

        /**
         * 获取写锁，响应线程中断（当前实现等同于 {@link #lock()}）。
         *
         * @throws InterruptedException 线程被中断时抛出
         */
        @Override
        public void lockInterruptibly() throws InterruptedException {
            lock();
        }

        /**
         * 尝试获取写锁（不等待）。
         *
         * @return true-获取成功，false-获取失败
         */
        @Override
        public boolean tryLock() {
            return tryLock(0, TimeUnit.MILLISECONDS);
        }

        /**
         * 尝试在指定时间内获取写锁，通过 Lua 脚本原子性检查并设置写锁。
         * 使用指数退避策略（10ms → 200ms）替代固定轮询间隔，减少无效 Redis 调用。
         *
         * @param time 最大等待时间
         * @param unit 时间单位
         * @return true-获取成功，false-超时未获取
         */
        @Override
        public boolean tryLock(long time, TimeUnit unit) {
            String cacheKey = threadCacheKey();
            String lockValue = UUID.randomUUID().toString();
            long deadline = System.currentTimeMillis() + unit.toMillis(time);
            long currentBackoff = MIN_BACKOFF_MILLIS;
            while (true) {
                try {
                    Long result = redisService.executeScript(
                            WRITE_LOCK_ACQUIRE_SCRIPT,
                            Arrays.asList(readLockKey, writeLockKey),
                            Long.class,
                            lockValue,
                            String.valueOf(expireMillis)
                    );
                    if (result != null && result == 1L) {
                        writeLockValueCache.put(cacheKey, lockValue);
                        return true;
                    }
                } catch (Exception e) {
                    log.warn("写锁获取异常: {}", writeLockKey, e);
                }
                if (System.currentTimeMillis() >= deadline) {
                    return false;
                }
                currentBackoff = backoffSleep(deadline, currentBackoff);
            }
        }

        /**
         * 释放写锁，通过 Lua 脚本原子性校验 lockValue 后删除写锁 key。
         */
        @Override
        public void unlock() {
            String cacheKey = threadCacheKey();
            String lockValue = writeLockValueCache.getIfPresent(cacheKey);
            if (lockValue == null) {
                return;
            }
            try {
                redisService.executeScript(
                        WRITE_LOCK_RELEASE_SCRIPT,
                        Collections.singletonList(writeLockKey),
                        Long.class,
                        lockValue
                );
            } catch (Exception e) {
                log.error("写锁释放异常: {}", writeLockKey, e);
            } finally {
                writeLockValueCache.invalidate(cacheKey);
            }
        }

        /**
         * 写锁不支持 Condition 条件。
         *
         * @throws UnsupportedOperationException 始终抛出
         */
        @Override
        public Condition newCondition() {
            throw new UnsupportedOperationException("WriteLock does not support conditions");
        }
    }

    // ======================== DistributedLocker 接口实现 ========================

    /**
     * 尝试获取写锁（非阻塞）
     * <p>实现 {@link DistributedLocker#tryLock(String, long, TimeUnit)}，
     * 内部使用写锁的 {@code tryLock()} 方法。
     *
     * @param lockKey   锁的键（当前实现忽略，使用构造时传入的 key）
     * @param leaseTime 锁的自动释放时间
     * @param timeUnit  时间单位
     * @return 获取成功返回 lockValue，获取失败返回 null
     */
    @Override
    public String tryLock(String lockKey, long leaseTime, TimeUnit timeUnit) {
        try {
            // 复用内部 writeLock 生成的 UUID，避免外层与内层 UUID 不匹配导致无法释放
            if (writeLock().tryLock(0, TimeUnit.MILLISECONDS)) {
                return writeLockValueCache.getIfPresent(threadCacheKey());
            }
        } catch (Exception e) {
            log.error("读写锁获取写锁异常: {}", writeLockKey, e);
        }
        return null;
    }

    /**
     * 尝试获取写锁（带等待时间）
     * <p>实现 {@link DistributedLocker#tryLock(String, long, long, TimeUnit)}，
     * 内部使用写锁的 {@code tryLock(time, unit)} 方法。
     *
     * @param lockKey   锁的键（当前实现忽略，使用构造时传入的 key）
     * @param waitTime  最大等待时间
     * @param leaseTime 锁的自动释放时间
     * @param timeUnit  时间单位
     * @return 获取成功返回 lockValue，等待超时返回 null
     */
    @Override
    public String tryLock(String lockKey, long waitTime, long leaseTime, TimeUnit timeUnit) throws InterruptedException {
        try {
            // 复用内部 writeLock 生成的 UUID，避免外层与内层 UUID 不匹配导致无法释放
            if (writeLock().tryLock(waitTime, timeUnit)) {
                return writeLockValueCache.getIfPresent(threadCacheKey());
            }
        } catch (Exception e) {
            log.error("读写锁获取写锁异常: {}", writeLockKey, e);
        }
        return null;
    }

    /**
     * 释放写锁
     * <p>实现 {@link DistributedLocker#unlock(String, String)}，
     * 内部使用写锁的 {@code unlock()} 方法。
     *
     * @param lockKey   锁的键（当前实现忽略，使用构造时传入的 key）
     * @param lockValue 获取锁时返回的 lockValue
     * @return true-释放成功，false-释放失败或锁已过期
     */
    @Override
    public boolean unlock(String lockKey, String lockValue) {
        String cacheKey = threadCacheKey();
        try {
            // 优先使用外部传入的 lockValue 校验
            String actualValue = writeLockValueCache.getIfPresent(cacheKey);
            if (actualValue != null && !actualValue.equals(lockValue)) {
                log.warn("读写锁 lockValue 不匹配，拒绝释放: lockKey={}", writeLockKey);
                return false;
            }
            writeLock().unlock();
            return true;
        } catch (Exception e) {
            log.error("读写锁释放异常: {}", writeLockKey, e);
            return false;
        }
    }

    /**
     * 检查写锁是否被持有
     * <p>实现 {@link DistributedLocker#isLocked(String)}。
     *
     * @param lockKey 锁的键（当前实现忽略，使用构造时传入的 key）
     * @return true-写锁被持有，false-写锁未被持有
     */
    @Override
    public boolean isLocked(String lockKey) {
        try {
            return Boolean.TRUE.equals(redisService.hasKey(writeLockKey));
        } catch (Exception e) {
            log.error("读写锁检查状态异常: {}", writeLockKey, e);
            return false;
        }
    }

    /**
     * 获取写锁的剩余过期时间
     * <p>实现 {@link DistributedLocker#getRemainTime(String)}。
     *
     * @param lockKey 锁的键（当前实现忽略，使用构造时传入的 key）
     * @return 剩余时间（毫秒），-1 表示锁未被持有，-2 表示获取失败
     */
    @Override
    public long getRemainTime(String lockKey) {
        try {
            long seconds = redisService.getExpire(writeLockKey);
            return seconds > 0 ? TimeUnit.SECONDS.toMillis(seconds) : seconds;
        } catch (Exception e) {
            log.error("读写锁获取剩余时间异常: {}", writeLockKey, e);
            return -2;
        }
    }
}
