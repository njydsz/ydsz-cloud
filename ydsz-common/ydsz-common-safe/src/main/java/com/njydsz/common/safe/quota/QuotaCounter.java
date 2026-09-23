package com.njydsz.common.safe.quota;

import java.time.Duration;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicLong;

import lombok.extern.slf4j.Slf4j;

import com.njydsz.common.redis.service.ops.RedisStringOps;

/**
 * 配额计数器（基于 RedisStringOps 原子操作 + 本地降级缓存）。
 *
 * <p>提供租户级用量统计的通用抽象，适用于 Token 计数、成本计数、并发计数、日执行量等场景。
 *
 * <p>核心能力：
 *
 * <ul>
 *   <li>INCR/DECR：Redis 原子增减，首次写入自动设 TTL，DECR 带下限保护（{@code floor=0} 时不为负）
 *   <li>GET：Redis 优先读，不可用时降级到本地内存缓存
 *   <li>RESET：显式重置计数器（用于运维清零）
 *   <li>降级：Redis 异常时自动降级到本地 {@link AtomicLong}，保证服务可用性
 * </ul>
 *
 * <p><b>线程安全：</b>本地降级缓存使用 {@link ConcurrentHashMap} + {@link AtomicLong}，天然线程安全。
 *
 * <p><b>注意事项：</b>TTL 仅在首次 INCR（返回值为 delta）时设置，后续增量操作不刷新 TTL。
 *
 * @author ydsz-team
 * @since 26.09.23
 */
@Slf4j
public class QuotaCounter {

  /** Redis String 操作组件 */
  private final RedisStringOps redisStringOps;

  /** Key 前缀（如 "agent:quota:daily:"） */
  private final String keyPrefix;

  /** 首次 INCR 时自动设置的 TTL（null 表示不自动设置） */
  private final Duration autoExpire;

  /** DECR 下限值（默认 0，表示不会减为负数） */
  private final long floor;

  /** 本地降级缓存（Redis 不可用时使用） */
  private final ConcurrentMap<String, AtomicLong> localFallback = new ConcurrentHashMap<>();

  /**
   * 构造配额计数器（默认配置：首次 INCR 设 TTL，DECR 下限为 0）。
   *
   * @param redisStringOps Redis String 操作组件
   * @param keyPrefix Key 前缀
   * @param autoExpire 首次 INCR 自动 TTL（需非 null）
   */
  public QuotaCounter(RedisStringOps redisStringOps, String keyPrefix, Duration autoExpire) {
    this(redisStringOps, keyPrefix, autoExpire, 0L);
  }

  /**
   * 构造配额计数器（全参数）。
   *
   * @param redisStringOps Redis String 操作组件
   * @param keyPrefix Key 前缀
   * @param autoExpire 首次 INCR 自动 TTL
   * @param floor DECR 下限值（DECR 后不会低于此值）
   */
  public QuotaCounter(RedisStringOps redisStringOps, String keyPrefix, Duration autoExpire, long floor) {
    this.redisStringOps = redisStringOps;
    this.keyPrefix = keyPrefix != null ? keyPrefix : "";
    this.autoExpire = autoExpire;
    this.floor = Math.max(0, floor);
  }

  // ==================== 核心操作 ====================

  /**
   * 原子增加。
   *
   * <p>首次增加（返回值等于 delta）时自动设置 TTL。Redis 异常时降级到本地缓存。
   *
   * @param suffix Key 后缀（通常为 tenantId）
   * @param delta 增量（非负）
   * @return 增加后的总用量；Redis 异常且降级成功时返回降级值；操作失败时返回 -1
   */
  public long incr(String suffix, long delta) {
    if (delta < 0) {
      log.warn("[QuotaCounter] incr 增量不能为负: suffix={}, delta={}", suffix, delta);
      return get(suffix);
    }
    String key = buildKey(suffix);
    try {
      Long result = redisStringOps.incr(key, delta);
      if (result != null && result == delta && autoExpire != null) {
        redisStringOps.expire(key, autoExpire);
      }
      return result != null ? result : getLocal(key);
    } catch (Exception e) {
      log.warn("[QuotaCounter] Redis INCR 失败，降级本地缓存: key={}, delta={}, err={}", key, delta, e.getMessage());
      return getLocalAfterIncr(key, delta);
    }
  }

  /**
   * 原子减少（带下限保护）。
   *
   * <p>当前值已处于下限时直接返回，不执行 DECR。
   *
   * @param suffix Key 后缀
   * @param delta 减量（非负）
   * @return 减少后的值；Redis 异常且降级成功时返回降级值；操作失败时返回 -1
   */
  public long decr(String suffix, long delta) {
    if (delta < 0) {
      log.warn("[QuotaCounter] decr 减量不能为负: suffix={}, delta={}", suffix, delta);
      return get(suffix);
    }
    String key = buildKey(suffix);
    try {
      long current = get(suffix);
      if (current <= floor) {
        return current;
      }
      Long result = redisStringOps.decr(key, delta);
      if (result != null && result < floor) {
        log.warn("[QuotaCounter] DECR 后低于下限, 重置: key={}, result={}, floor={}", key, result, floor);
        redisStringOps.set(key, String.valueOf(floor));
        return floor;
      }
      return result != null ? result : getLocal(key);
    } catch (Exception e) {
      log.warn("[QuotaCounter] Redis DECR 失败，降级本地缓存: key={}, delta={}, err={}", key, delta, e.getMessage());
      return getLocalAfterDecr(key, delta);
    }
  }

  /**
   * 获取当前计数值。
   *
   * <p>优先从 Redis 读取，失败时降级到本地缓存。
   *
   * @param suffix Key 后缀
   * @return 当前计数值；无法获取时返回 0
   */
  public long get(String suffix) {
    String key = buildKey(suffix);
    try {
      Object val = redisStringOps.get(key);
      if (val instanceof Number) {
        return ((Number) val).longValue();
      }
      if (val instanceof String s && !s.isBlank()) {
        return Long.parseLong(s);
      }
      return 0L;
    } catch (Exception e) {
      log.debug("[QuotaCounter] Redis GET 失败，降级本地缓存: key={}, err={}", key, e.getMessage());
      AtomicLong local = localFallback.get(key);
      return local != null ? local.get() : 0L;
    }
  }

  /**
   * 重置计数器（清除 Redis key 和本地缓存）。
   *
   * @param suffix Key 后缀
   */
  public void reset(String suffix) {
    String key = buildKey(suffix);
    try {
      redisStringOps.del(key);
    } catch (Exception e) {
      log.warn("[QuotaCounter] Redis DEL 失败: key={}, err={}", key, e.getMessage());
    }
    localFallback.remove(key);
  }

  /**
   * 显式设置 key TTL（用于定期续期场景）。
   *
   * @param suffix Key 后缀
   * @param duration TTL（非 null）
   */
  public void expire(String suffix, Duration duration) {
    if (duration == null) {
      return;
    }
    try {
      redisStringOps.expire(buildKey(suffix), duration);
    } catch (Exception e) {
      log.warn("[QuotaCounter] Redis EXPIRE 失败: suffix={}, err={}", suffix, e.getMessage());
    }
  }

  // ==================== 内部降级方法 ====================

  private long getLocalAfterIncr(String key, long delta) {
    return localFallback.computeIfAbsent(key, k -> new AtomicLong(0)).addAndGet(delta);
  }

  private long getLocalAfterDecr(String key, long delta) {
    AtomicLong local = localFallback.computeIfAbsent(key, k -> new AtomicLong(0));
    long current = local.get();
    if (current <= floor) {
      return current;
    }
    long next = Math.max(floor, current - delta);
    local.set(next);
    return next;
  }

  private long getLocal(String key) {
    AtomicLong local = localFallback.get(key);
    return local != null ? local.get() : 0L;
  }

  private String buildKey(String suffix) {
    return keyPrefix + (suffix != null ? suffix : "");
  }
}
