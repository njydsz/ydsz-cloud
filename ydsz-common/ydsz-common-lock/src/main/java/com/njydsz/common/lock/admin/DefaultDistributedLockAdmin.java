package com.njydsz.common.lock.admin;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.concurrent.TimeUnit;

import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.Cursor;
import org.springframework.data.redis.core.ScanOptions;
import org.springframework.data.redis.core.StringRedisTemplate;

import com.njydsz.common.lock.scheduler.LockWatchDog;

/**
 * 分布式锁运维管理默认实现（ydsz-common-lock）
 *
 * <p>封装对 Redis 的直接操作（hasKey / delete / scan / keys / opsForValue），
 * 通过 {@link com.njydsz.common.lock.strategy.LockStrategy#getLockAdmin()} 暴露给业务端，
 * 避免 Controller 层直接持有 {@link StringRedisTemplate}。
 *
 * <h3>安全设计</h3>
 * <ul>
 *   <li>forceUnlock 前先取消看门狗续期，避免删锁后仍有续期线程复活锁键</li>
 *   <li>扫描类方法（searchKeys / scanKeys）对异常进行捕获并返回空集合，
 *       防止单次运维查询影响上层端点稳定性</li>
 *   <li>所有方法对 {@code StringRedisTemplate} 为 null 做了防护</li>
 * </ul>
 *
 * @author ydsz-team
 * @since 26.09.08
 */
@Slf4j
public class DefaultDistributedLockAdmin implements DistributedLockAdmin {

  /** HashMap / ArrayList 初始容量 */
  private static final int COLLECTION_INIT_CAPACITY = 16;

  /** Redis 连接不可用时的提示文案 */
  private static final String REDIS_UNAVAILABLE = "StringRedisTemplate 不可用";

  private final StringRedisTemplate stringRedisTemplate;
  private final LockWatchDog lockWatchDog;

  /**
   * 构造器注入
   *
   * @param stringRedisTemplate Redis 模板（不可为 null）
   * @param lockWatchDog       看门狗实例（不可为 null），用于 forceUnlock 前取消续期
   */
  public DefaultDistributedLockAdmin(
      StringRedisTemplate stringRedisTemplate, LockWatchDog lockWatchDog) {
    this.stringRedisTemplate = stringRedisTemplate;
    this.lockWatchDog = lockWatchDog;
  }

  /**
   * 查询锁是否存在
   *
   * <p>使用 {@code hasKey} 而非 {@code getRemainTime}，避免 Redis TTL 返回负值导致误判。
   *
   * @param lockKey 锁的 Redis 键
   * @return true 表示锁存在；Redis 模板不可用时返回 false
   */
  @Override
  public boolean exists(String lockKey) {
    if (stringRedisTemplate == null) {
      return false;
    }
    try {
      return Boolean.TRUE.equals(stringRedisTemplate.hasKey(lockKey));
    } catch (Exception e) {
      log.warn("[ydsz-lock] [admin] 查询锁是否存在异常 key={} cause={}", lockKey, e.getMessage());
      return false;
    }
  }

  /**
   * 获取锁的剩余过期时间（TTL）
   *
   * @param lockKey 锁的 Redis 键
   * @return 剩余时间（毫秒）；锁不存在或不可用时返回 -1
   */
  @Override
  public long getTtl(String lockKey) {
    if (stringRedisTemplate == null) {
      return -1L;
    }
    try {
      Long ttl = stringRedisTemplate.getExpire(lockKey, TimeUnit.MILLISECONDS);
      return ttl != null ? ttl : -1L;
    } catch (Exception e) {
      log.warn("[ydsz-lock] [admin] 获取 TTL 异常 key={} cause={}", lockKey, e.getMessage());
      return -1L;
    }
  }

  /**
   * 获取锁的原始值（运维诊断）
   *
   * @param lockKey 锁的 Redis 键
   * @return 锁的原始字符串值；不存在或不可用时返回 null
   */
  @Override
  public String getLockValue(String lockKey) {
    if (stringRedisTemplate == null) {
      return null;
    }
    try {
      return stringRedisTemplate.opsForValue().get(lockKey);
    } catch (Exception e) {
      log.warn("[ydsz-lock] [admin] 获取锁原始值异常 key={} cause={}", lockKey, e.getMessage());
      return null;
    }
  }

  /**
   * 强制释放锁
   *
   * <p>步骤：先取消看门狗续期 → 再删除 Redis 键，避免续期线程复活锁。
   *
   * @param lockKey 锁的 Redis 键
   * @return true 表示键存在并被删除；false 表示键不存在
   */
  @Override
  public boolean forceUnlock(String lockKey) {
    if (stringRedisTemplate == null) {
      return false;
    }
    try {
      // 先取消看门狗续期，避免续期线程复活锁
      if (lockWatchDog != null) {
        lockWatchDog.cancelRenewal(lockKey);
      }
      return Boolean.TRUE.equals(stringRedisTemplate.delete(lockKey));
    } catch (Exception e) {
      log.warn("[ydsz-lock] [admin] 强制释放锁异常 key={} cause={}", lockKey, e.getMessage());
      return false;
    }
  }

  /**
   * 按模式搜索锁键（KEYS 命令，生产慎用）
   *
   * @param pattern 搜索模式（如 "app:lock:*"）
   * @return 匹配的锁键集合；异常或无匹配返回空集合
   */
  @Override
  public Set<String> searchKeys(String pattern) {
    if (stringRedisTemplate == null) {
      return Collections.emptySet();
    }
    if (pattern == null || pattern.isEmpty()) {
      return Collections.emptySet();
    }
    try {
      Set<String> keys = stringRedisTemplate.keys(pattern);
      return keys == null ? Collections.emptySet() : keys;
    } catch (Exception e) {
      log.warn("[ydsz-lock] [admin] 搜索锁键异常 pattern={} cause={}", pattern, e.getMessage());
      return Collections.emptySet();
    }
  }

  /**
   * 按模式渐进式扫描锁键（SCAN 命令，生产安全）
   *
   * @param pattern   搜索模式（如 "app:lock:*"）
   * @param batchSize 单次扫描的期望数量
   * @return 本次扫描得到的键列表；异常或无匹配返回空列表
   */
  @Override
  public List<String> scanKeys(String pattern, int batchSize) {
    if (stringRedisTemplate == null) {
      return Collections.emptyList();
    }
    if (pattern == null || pattern.isEmpty() || batchSize <= 0) {
      return Collections.emptyList();
    }
    List<String> keys = new ArrayList<>(COLLECTION_INIT_CAPACITY);
    ScanOptions options = ScanOptions.scanOptions().match(pattern).count(batchSize).build();
    try (Cursor<String> cursor = stringRedisTemplate.scan(options)) {
      while (cursor.hasNext() && keys.size() < batchSize) {
        keys.add(cursor.next());
      }
    } catch (Exception e) {
      log.warn("[ydsz-lock] [admin] 扫描锁键异常 pattern={} cause={}", pattern, e.getMessage());
    }
    return keys;
  }
}
