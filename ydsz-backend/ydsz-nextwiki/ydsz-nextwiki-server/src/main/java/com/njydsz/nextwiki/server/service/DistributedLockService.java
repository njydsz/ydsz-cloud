package com.njydsz.nextwiki.server.service;

import java.time.Duration;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.TimeUnit;

import org.springframework.stereotype.Service;

import com.njydsz.common.exception.custom.BusinessException;
import com.njydsz.common.lock.annotation.LockType;
import com.njydsz.common.lock.core.DistributedLocker;
import com.njydsz.common.lock.strategy.LockStrategy;
import com.njydsz.nextwiki.domain.enums.NextwikiExceptionCode;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * 分布式锁服务
 * <p>
 * 委托 {@link DistributedLocker} 公共能力实现分布式锁，用于保护目录树操作（移动、重命名、删除）的并发安全。
 * <p>
 * 通过 ydsz-common-lock 模块获得：
 * <ul>
 *   <li>可重入锁（{@link com.njydsz.common.lock.impl.RedisReentrantLock}）</li>
 *   <li>看门狗（WatchDog）自动续约</li>
 *   <li>锁监控指标（LockMetrics）</li>
 *   <li>锁健康检查（LockHealthIndicator）</li>
 * </ul>
 *
 * <p><b>兼容性说明：</b>本服务保留原有方法签名，调用方传入的 {@code ownerId} 作为锁持有者上下文标识，
 * 内部维护 {@code (lockKey, ownerId) -> lockValue} 映射，调用方无需感知
 * {@link DistributedLocker#tryLock(String, long, TimeUnit)} 返回的 lockValue。
 *
 * @author ydsz-team
 * @since 1.0.0
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DistributedLockService {

    private final LockStrategy lockStrategy;

    private static final String LOCK_PREFIX = "nextwiki:lock:";
    private static final Duration DEFAULT_TIMEOUT = Duration.ofSeconds(30);
    private static final long DEFAULT_WAIT_MS = 3000L;

    /**
     * lockKey 与 ownerId 组合到 DistributedLocker 返回的 lockValue 的映射。
     * <p>{@link DistributedLocker#unlock(String, String)} 需要传入获取锁时返回的 lockValue
     * 用于校验锁的持有者；本服务对外仍以 ownerId 为接口契约，内部使用此映射在 unlock 时还原 lockValue。
     */
    private final ConcurrentMap<String, String> lockValueHolder = new ConcurrentHashMap<>();

    /**
     * 尝试获取锁（非阻塞）
     *
     * @param lockKey  锁键
     * @param ownerId  持有者标识（用于安全释放）
     * @param timeout  锁超时时间
     * @return true=获取成功
     */
    public boolean tryLock(String lockKey, String ownerId, Duration timeout) {
        String fullKey = LOCK_PREFIX + lockKey;
        String lockValue = locker().tryLock(fullKey, timeout.toMillis(), TimeUnit.MILLISECONDS);
        if (lockValue != null) {
            lockValueHolder.put(holderKey(fullKey, ownerId), lockValue);
            log.debug("[DistributedLockService] 获取锁成功: key={}, owner={}", lockKey, ownerId);
            return true;
        }
        return false;
    }

    /**
     * 尝试获取锁（带等待时间）
     *
     * @param lockKey  锁键
     * @param ownerId  持有者标识
     * @param timeout  锁超时时间
     * @param waitMs   最大等待时间（毫秒）
     * @return true=获取成功
     */
    public boolean tryLockWithWait(String lockKey, String ownerId, Duration timeout, long waitMs) {
        String fullKey = LOCK_PREFIX + lockKey;
        try {
            String lockValue = locker().tryLock(
                    fullKey, waitMs, timeout.toMillis(), TimeUnit.MILLISECONDS);
            if (lockValue != null) {
                lockValueHolder.put(holderKey(fullKey, ownerId), lockValue);
                log.debug("[DistributedLockService] 获取锁成功(带等待): key={}, owner={}", lockKey, ownerId);
                return true;
            }
            return false;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return false;
        }
    }

    /**
     * 获取锁（阻塞式，获取不到则抛异常）
     *
     * @param lockKey 锁键
     * @param ownerId 持有者标识
     */
    public void acquireLock(String lockKey, String ownerId) {
        if (!tryLockWithWait(lockKey, ownerId, DEFAULT_TIMEOUT, DEFAULT_WAIT_MS)) {
            throw BusinessException.of(NextwikiExceptionCode.LOCK_BUSY).data("lockKey", lockKey);
        }
    }

    /**
     * 释放锁（仅持有者可释放）
     *
     * @param lockKey 锁键
     * @param ownerId 持有者标识
     */
    public void unlock(String lockKey, String ownerId) {
        String fullKey = LOCK_PREFIX + lockKey;
        String holderKey = holderKey(fullKey, ownerId);
        String lockValue = lockValueHolder.remove(holderKey);
        if (lockValue == null) {
            log.warn("[DistributedLockService] 释放锁失败(未找到 lockValue 上下文): key={}, owner={}",
                    lockKey, ownerId);
            return;
        }
        boolean released = locker().unlock(fullKey, lockValue);
        if (released) {
            log.debug("[DistributedLockService] 释放锁成功: key={}, owner={}", lockKey, ownerId);
        } else {
            log.warn("[DistributedLockService] 释放锁失败(锁已被其他线程获取或已过期): key={}, owner={}",
                    lockKey, ownerId);
        }
    }

    /**
     * 获取可重入分布式锁实例（缓存复用，避免重复创建）。
     */
    private DistributedLocker locker() {
        return lockStrategy.getLock(LockType.REENTRANT);
    }

    /**
     * 生成 lockValueHolder 的 key：以 fullKey + ownerId 组合，避免不同 owner 互相覆盖。
     */
    private static String holderKey(String fullKey, String ownerId) {
        return fullKey + "@" + ownerId;
    }

    /**
     * 生成目录树操作锁键
     */
    public static String folderLockKey(String nodeId) {
        return "folder:" + nodeId;
    }
}
