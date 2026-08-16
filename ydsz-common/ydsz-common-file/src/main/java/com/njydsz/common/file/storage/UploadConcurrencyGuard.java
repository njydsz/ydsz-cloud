package com.njydsz.common.file.storage;

import java.time.Duration;
import java.util.Collections;
import java.util.UUID;

import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;

import com.njydsz.common.exception.custom.BusinessException;
import com.njydsz.common.file.exception.FileExceptionCode;
import com.njydsz.common.lock.core.DistributedLocker;

/**
 * 上传并发保护器
 *
 * <p>防止对同一文件（objectKey）的并发上传，避免数据竞争和覆盖问题。 基于 Redis 实现，当已有上传正在进行时，直接拒绝新上传（快速失败）。
 *
 * <p><b>使用方式：</b>
 *
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
 * @author ydsz-team
 * @since 1.0.0
 */
@Slf4j
public class UploadConcurrencyGuard {

  /** Redis 锁键前缀 */
  private static final String LOCK_KEY_PREFIX = "ydsz:file:upload:lock:";

  /** 锁的过期时间（秒），防止业务异常导致锁无法释放 */
  private static final long LOCK_EXPIRE_SECONDS = 300;

  /** 分布式锁接口（优先使用，来自 ydsz-common-lock）；为 null 时降级为原生 Redis 操作 */
  private final DistributedLocker distributedLocker;

  private final StringRedisTemplate redisTemplate;

  /**
   * 创建并发保护器（使用 ydsz-common-lock 分布式锁）
   *
   * <p>当 {@link DistributedLocker} 可用时，底层使用 common-lock 的 RedisReentrantLock 实现加锁 / 释放，享有 Lua
   * 原子释放、WatchDog 续期等能力； 当 {@code distributedLocker == null} 时降级为原生 {@link StringRedisTemplate} 操作。
   *
   * @param distributedLocker 分布式锁接口（可为 null，null 时降级为原生 Redis 操作）
   * @param redisTemplate Redis 模板
   */
  public UploadConcurrencyGuard(
      DistributedLocker distributedLocker, StringRedisTemplate redisTemplate) {
    this.distributedLocker = distributedLocker;
    this.redisTemplate = redisTemplate;
  }

  /**
   * 获取上传锁
   *
   * @param objectKey 文件对象键
   * @return 锁令牌，用于释放锁时校验
   * @throws BusinessException 当已有上传正在进行时快速失败
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

    // 锁已被持有，直接拒绝（快速失败）
    log.warn("[UploadGuard] concurrent upload rejected, key={}", lockKey);
    throw new BusinessException(FileExceptionCode.MULTIPART_UPLOAD_FAILED);
  }

  /**
   * 释放上传锁
   *
   * <p>当 {@link DistributedLocker} 可用时，委托其 {@code unlock} 实现（Lua 原子释放）； 否则降级为原生 Lua 脚本释放。
   *
   * @param objectKey 文件对象键
   * @param lockToken 获取锁时返回的令牌
   */
  public void release(String objectKey, String lockToken) {
    if (objectKey == null || lockToken == null) {
      return;
    }

    String lockKey = LOCK_KEY_PREFIX + objectKey;
    if (distributedLocker != null) {
      boolean released = distributedLocker.unlock(lockKey, lockToken);
      if (released) {
        log.debug("[UploadGuard] lock released (common-lock), key={}", lockKey);
      }
      return;
    }
    // 降级：原生 Lua 脚本释放
    String script =
        "if redis.call('get', KEYS[1]) == ARGV[1] then return redis.call('del', KEYS[1]) else return 0 end";
    Object result =
        redisTemplate.execute(
            new DefaultRedisScript<>(script, Long.class),
            Collections.singletonList(lockKey),
            lockToken);
    if (Long.valueOf(1).equals(result)) {
      log.debug("[UploadGuard] lock released (redis-fallback), key={}", lockKey);
    }
  }

  /**
   * 非阻塞尝试获取锁
   *
   * <p>当 {@link DistributedLocker} 可用时，委托其 {@code tryLock} 实现（Lua 原子操作 + WatchDog）； 否则降级为原生 {@link
   * StringRedisTemplate#setIfAbsent} 操作。
   *
   * @param lockKey 锁键
   * @return 成功返回锁令牌，失败返回 null
   */
  private String tryAcquireNonBlocking(String lockKey) {
    if (distributedLocker != null) {
      String lockValue =
          distributedLocker.tryLock(
              lockKey, LOCK_EXPIRE_SECONDS, java.util.concurrent.TimeUnit.SECONDS);
      if (lockValue != null) {
        log.debug("[UploadGuard] lock acquired (common-lock), key={}", lockKey);
      }
      return lockValue;
    }
    // 降级：原生 Redis SET NX EX
    String lockValue = UUID.randomUUID().toString().replace("-", "");
    Boolean success =
        redisTemplate
            .opsForValue()
            .setIfAbsent(lockKey, lockValue, Duration.ofSeconds(LOCK_EXPIRE_SECONDS));
    if (Boolean.TRUE.equals(success)) {
      log.debug("[UploadGuard] lock acquired (redis-fallback), key={}", lockKey);
      return lockValue;
    }
    return null;
  }
}
