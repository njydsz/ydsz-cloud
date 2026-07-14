package com.njydsz.pmis.nextwiki.server.service;

import java.time.Duration;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import com.njydsz.pmis.common.exception.custom.BusinessException;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * 分布式锁服务
 * <p>
 * 基于 Redis 实现的分布式锁，用于保护目录树操作（移动、重命名、删除）的并发安全。
 * 使用 SET NX EX 原子操作获取锁，value 存放唯一标识用于安全释放。
 *
 * @author ydsz-pmis-team
 * @since 1.4.0
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DistributedLockService {

    private final StringRedisTemplate redisTemplate;

    private static final String LOCK_PREFIX = "nextwiki:lock:";
    private static final Duration DEFAULT_TIMEOUT = Duration.ofSeconds(30);
    private static final long DEFAULT_WAIT_MS = 3000L;

    /**
     * 尝试获取锁（非阻塞）
     *
     * @param lockKey  锁键
     * @param ownerId  持有者标识（用于安全释放）
     * @param timeout  锁超时时间
     * @return true=获取成功
     */
    public boolean tryLock(String lockKey, String ownerId, Duration timeout) {
        String key = LOCK_PREFIX + lockKey;
        Boolean acquired = redisTemplate.opsForValue()
                .setIfAbsent(key, ownerId, timeout);
        if (Boolean.TRUE.equals(acquired)) {
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
        long deadline = System.currentTimeMillis() + waitMs;
        while (System.currentTimeMillis() < deadline) {
            if (tryLock(lockKey, ownerId, timeout)) {
                return true;
            }
            try {
                Thread.sleep(100);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return false;
            }
        }
        return false;
    }

    /**
     * 获取锁（阻塞式，获取不到则抛异常）
     *
     * @param lockKey 锁键
     * @param ownerId 持有者标识
     */
    public void acquireLock(String lockKey, String ownerId) {
        if (!tryLockWithWait(lockKey, ownerId, DEFAULT_TIMEOUT, DEFAULT_WAIT_MS)) {
            throw BusinessException.builder()
                    .key("操作正在处理中，请稍后重试: " + lockKey)
                    .build();
        }
    }

    /**
     * 释放锁（仅持有者可释放）
     *
     * @param lockKey 锁键
     * @param ownerId 持有者标识
     */
    public void unlock(String lockKey, String ownerId) {
        String key = LOCK_PREFIX + lockKey;
        String currentValue = redisTemplate.opsForValue().get(key);
        if (ownerId.equals(currentValue)) {
            redisTemplate.delete(key);
            log.debug("[DistributedLockService] 释放锁: key={}, owner={}", lockKey, ownerId);
        }
    }

    /**
     * 生成目录树操作锁键
     */
    public static String folderLockKey(String nodeId) {
        return "folder:" + nodeId;
    }
}
