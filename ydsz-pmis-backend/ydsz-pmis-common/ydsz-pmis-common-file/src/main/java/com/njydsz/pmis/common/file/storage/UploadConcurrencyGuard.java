package com.njydsz.pmis.common.file.storage;

import java.time.Duration;
import java.util.Collections;
import java.util.UUID;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;

import com.njydsz.pmis.common.exception.custom.BusinessException;
import com.njydsz.pmis.common.file.config.FileProperties.ConcurrencyControl;
import com.njydsz.pmis.common.file.exception.FileExceptionCode;

import lombok.extern.slf4j.Slf4j;

/**
 * 上传并发保护器
 *
 * <p>防止对同一文件（objectKey）的并发上传，避免数据竞争和覆盖问题。
 * 基于 Redis 实现，支持两种策略：
 * <ul>
 *   <li>{@code REJECT} - 已有上传正在进行时，直接拒绝新上传（默认）</li>
 *   <li>{@code WAIT} - 等待旧上传完成后，再执行新上传</li>
 * </ul>
 *
 * <p><b>使用方式：</b>
 * <pre>{@code
 * UploadConcurrencyGuard guard = ...;
 * String lockToken = guard.acquire(objectKey);
 * try {
 *     // 执行上传
 * } finally {
 *     guard.release(objectKey, lockToken);
 * }
 * }</pre>
 *
 * @author Marvin Lee
 * @email limw1888@126.com
 * @version 3.5.0
 */
@Slf4j
public class UploadConcurrencyGuard {

    /**
     * Redis 锁键前缀
     */
    private static final String LOCK_KEY_PREFIX = "ydsz:file:upload:lock:";

    /**
     * 锁的过期时间（秒），防止业务异常导致锁无法释放
     */
    private static final long LOCK_EXPIRE_SECONDS = 300;

    /**
     * WAIT 策略下每次等待的间隔（毫秒）
     */
    private static final long WAIT_INTERVAL_MILLIS = 100;

    /**
     * WAIT 策略下最大等待时间（秒）
     */
    private static final long MAX_WAIT_SECONDS = 60;

    private final StringRedisTemplate redisTemplate;
    private final ConcurrencyControl config;

    /**
     * 创建并发保护器
     *
     * @param redisTemplate Redis 模板
     * @param config        并发控制配置
     */
    public UploadConcurrencyGuard(StringRedisTemplate redisTemplate, ConcurrencyControl config) {
        this.redisTemplate = redisTemplate;
        this.config = config;
    }

    /**
     * 获取上传锁
     *
     * @param objectKey 文件对象键
     * @return 锁令牌，用于释放锁时校验
     * @throws BusinessException 当配置为 REJECT 策略且已有上传正在进行时
     */
    public String acquire(String objectKey) {
        if (objectKey == null || objectKey.isEmpty()) {
            throw new IllegalArgumentException("objectKey must not be null or empty");
        }

        String lockKey = LOCK_KEY_PREFIX + objectKey;

        // 尝试非阻塞获取锁
        String lockValue = tryAcquireNonBlocking(lockKey);
        if (lockValue != null) {
            return lockValue;
        }

        // 锁已被持有，根据策略处理
        return handleLockHeld(lockKey, lockValue);
    }

    /**
     * 释放上传锁
     *
     * <p>使用 Lua 脚本保证原子性：仅当锁值匹配时才删除。
     *
     * @param objectKey  文件对象键
     * @param lockToken  获取锁时返回的令牌
     */
    public void release(String objectKey, String lockToken) {
        if (objectKey == null || lockToken == null) {
            return;
        }

        String lockKey = LOCK_KEY_PREFIX + objectKey;
        // Lua: if redis.call('get', KEYS[1]) == ARGV[1] then return redis.call('del', KEYS[1]) else return 0 end
        String script = "if redis.call('get', KEYS[1]) == ARGV[1] then return redis.call('del', KEYS[1]) else return 0 end";
        Object result = redisTemplate.execute(
                new DefaultRedisScript<>(script, Long.class),
                Collections.singletonList(lockKey), lockToken);
        if (Long.valueOf(1).equals(result)) {
            log.debug("[UploadGuard] lock released, key={}", lockKey);
        }
    }

    /**
     * 非阻塞尝试获取锁（SETNX）
     *
     * @param lockKey 锁键
     * @return 成功返回锁令牌，失败返回 null
     */
    private String tryAcquireNonBlocking(String lockKey) {
        String lockValue = UUID.randomUUID().toString().replace("-", "");
        Boolean success = redisTemplate.opsForValue()
                .setIfAbsent(lockKey, lockValue, Duration.ofSeconds(LOCK_EXPIRE_SECONDS));
        if (Boolean.TRUE.equals(success)) {
            log.debug("[UploadGuard] lock acquired, key={}", lockKey);
            return lockValue;
        }
        return null;
    }

    /**
     * 处理锁已被持有的情况
     *
     * @param lockKey 锁键
     * @param existingValue 已存在的锁值（用于日志）
     * @return 获取锁后返回新令牌
     * @throws BusinessException 当 REJECT 策略时直接拒绝
     */
    private String handleLockHeld(String lockKey, String existingValue) {
        switch (config.getStrategy()) {
            case REJECT:
                log.warn("[UploadGuard] concurrent upload rejected, key={}", lockKey);
                throw new BusinessException(FileExceptionCode.UPLOAD_CONCURRENT_CONFLICT);
            case WAIT:
                return waitForLock(lockKey);
            default:
                // 未知策略，默认拒绝
                log.warn("[UploadGuard] unknown strategy, rejecting concurrent upload, key={}", lockKey);
                throw new BusinessException(FileExceptionCode.UPLOAD_CONCURRENT_CONFLICT);
        }
    }

    /**
     * WAIT 策略：等待锁释放后重新获取
     *
     * @param lockKey 锁键
     * @return 获取锁后返回新令牌
     */
    private String waitForLock(String lockKey) {
        long elapsedMillis = 0;
        long maxWaitMillis = MAX_WAIT_SECONDS * 1000;

        log.info("[UploadGuard] waiting for lock release, key={}", lockKey);

        while (elapsedMillis < maxWaitMillis) {
            try {
                // 短暂休眠后重试
                Thread.sleep(WAIT_INTERVAL_MILLIS);
                elapsedMillis += WAIT_INTERVAL_MILLIS;

                String lockValue = tryAcquireNonBlocking(lockKey);
                if (lockValue != null) {
                    log.info("[UploadGuard] lock acquired after waiting, key={}, waited={}ms",
                            lockKey, elapsedMillis);
                    return lockValue;
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                log.warn("[UploadGuard] interrupted while waiting for lock, key={}", lockKey);
                throw new BusinessException(FileExceptionCode.UPLOAD_CONCURRENT_CONFLICT);
            }
        }

        log.warn("[UploadGuard] wait timeout for lock, key={}, timeout={}s", lockKey, MAX_WAIT_SECONDS);
        throw new BusinessException(FileExceptionCode.UPLOAD_CONCURRENT_CONFLICT);
    }
}
