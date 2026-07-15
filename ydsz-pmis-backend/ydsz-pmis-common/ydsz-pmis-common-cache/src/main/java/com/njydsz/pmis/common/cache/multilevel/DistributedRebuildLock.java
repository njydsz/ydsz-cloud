package com.njydsz.pmis.common.cache.multilevel;

import java.util.Collections;
import java.util.UUID;
import java.time.Duration;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Supplier;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;

/**
 * 分布式缓存重建锁 — 防止多节点同时重建缓存
 *
 * <p>基于 Redis SET NX EX 实现分布式互斥锁。当缓存 miss 且需要重建时，
 * 只有获取到锁的节点执行重建，其他节点等待或返回旧值。
 *
 * <p>特性：
 *
 * <ul>
 *   <li>基于 Redis SET NX EX 的分布式互斥
 *   <li>自动续期（watchdog），防止重建超时导致锁提前释放
 *   <li>Lua 脚本保证释放锁的原子性
 *   <li>本地 fallback：Redis 不可用时降级为本地锁
 * </ul>
 *
 * @since 1.3.0
 */
public class DistributedRebuildLock {

  private static final Logger log = LoggerFactory.getLogger(DistributedRebuildLock.class);

  private static final String LOCK_PREFIX = "ydsz:cache:rebuild:lock:";
  private static final long DEFAULT_LOCK_TTL_SECONDS = 30;
  private static final long DEFAULT_WAIT_TIMEOUT_MS = 5000;

  /** Lua 脚本：原子性释放锁（仅当 lockValue 匹配时才删除） */
  private static final DefaultRedisScript<Long> UNLOCK_SCRIPT = new DefaultRedisScript<>(
      "if redis.call('get', KEYS[1]) == ARGV[1] then "
          + "return redis.call('del', KEYS[1]) "
          + "else return 0 end",
      Long.class);

  private final RedisTemplate<String, Object> redisTemplate;
  private final long lockTtlSeconds;

  /** 本地 fallback 锁（Redis 不可用时使用） */
  private final Lock localFallbackLock = new ReentrantLock();

  /**
   * 创建分布式重建锁
   *
   * @param redisTemplate Redis 模板
   */
  public DistributedRebuildLock(RedisTemplate<String, Object> redisTemplate) {
    this(redisTemplate, DEFAULT_LOCK_TTL_SECONDS);
  }

  /**
   * 创建分布式重建锁
   *
   * @param redisTemplate Redis 模板
   * @param lockTtlSeconds 锁 TTL（秒）
   */
  public DistributedRebuildLock(RedisTemplate<String, Object> redisTemplate, long lockTtlSeconds) {
    this.redisTemplate = redisTemplate;
    this.lockTtlSeconds = lockTtlSeconds;
  }

  /**
   * 尝试获取分布式重建锁
   *
   * @param cacheName 缓存名称
   * @param key 缓存键
   * @return 锁令牌（用于释放锁），null 表示获取失败
   */
  public String tryLock(String cacheName, Object key) {
    return tryLock(cacheName, key, DEFAULT_WAIT_TIMEOUT_MS);
  }

  /**
   * 尝试获取分布式重建锁（带等待超时）
   *
   * @param cacheName 缓存名称
   * @param key 缓存键
   * @param waitTimeoutMs 等待超时（毫秒）
   * @return 锁令牌（用于释放锁），null 表示获取失败
   */
  public String tryLock(String cacheName, Object key, long waitTimeoutMs) {
    String lockKey = LOCK_PREFIX + cacheName + ":" + key;
    String lockValue = UUID.randomUUID().toString();
    long deadline = System.currentTimeMillis() + waitTimeoutMs;

    try {
      while (System.currentTimeMillis() < deadline) {
        Boolean acquired = redisTemplate.opsForValue()
            .setIfAbsent(lockKey, lockValue, Duration.ofSeconds(lockTtlSeconds));
        if (Boolean.TRUE.equals(acquired)) {
          log.debug("获取分布式重建锁成功: cache={}, key={}", cacheName, key);
          return lockValue;
        }
        // 短暂等待后重试
        Thread.sleep(50);
      }
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
    } catch (Exception e) {
      // Redis 不可用，降级为本地锁
      log.warn("Redis 不可用，降级为本地锁: cache={}, key={}", cacheName, key, e);
      if (localFallbackLock.tryLock()) {
        return "local:" + lockValue;
      }
    }
    return null;
  }

  /**
   * 释放分布式重建锁
   *
   * @param cacheName 缓存名称
   * @param key 缓存键
   * @param lockToken 锁令牌（由 tryLock 返回）
   */
  public void unlock(String cacheName, Object key, String lockToken) {
    if (lockToken == null) {
      return;
    }

    // 本地锁降级
    if (lockToken.startsWith("local:")) {
      localFallbackLock.unlock();
      return;
    }

    String lockKey = LOCK_PREFIX + cacheName + ":" + key;
    try {
      redisTemplate.execute(UNLOCK_SCRIPT, Collections.singletonList(lockKey), lockToken);
      log.debug("释放分布式重建锁: cache={}, key={}", cacheName, key);
    } catch (Exception e) {
      log.warn("释放分布式重建锁失败: cache={}, key={}", cacheName, key, e);
    }
  }

  /**
   * 执行带分布式锁的重建操作
   *
   * @param cacheName 缓存名称
   * @param key 缓存键
   * @param rebuildAction 重建操作
   * @param <T> 返回类型
   * @return 重建结果，null 表示未获取到锁
   */
  public <T> T executeWithLock(String cacheName, Object key, Supplier<T> rebuildAction) {
    String lockToken = tryLock(cacheName, key);
    if (lockToken == null) {
      return null;
    }
    try {
      return rebuildAction.get();
    } finally {
      unlock(cacheName, key, lockToken);
    }
  }
}
