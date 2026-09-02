package com.njydsz.cronjob.server.core.redis;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import com.njydsz.common.redis.service.ops.RedisStringOps;

/**
 * P0-8: 模块级 Redis 操作收敛入口。
 *
 * <p>统一封装 {@link RedisStringOps}，为 cronjob 模块所有 Redis 操作提供单一入口，达成以下目标：
 *
 * <ul>
 *   <li><b>Key 命名规范</b>：统一前缀 {@code ydzs:job:}，禁止散落在各处的裸字符串 key
 *   <li><b>异常降级</b>：所有操作封装 try-catch，Redis 异常时按业务语义降级（返回 null/0/false），不中断主流程
 *   <li><b>可观测性</b>：统一埋点/日志格式，便于监控 Redis 操作成功率与延迟
 *   <li><b>DDD 分层</b>：Domain 层不直接依赖 Redis，所有操作经 Server 层本入口完成
 * </ul>
 *
 * <p>使用方式：注入 {@link CronjobRedisOps} 替代直接注入 {@link RedisStringOps}。
 *
 * <p><b>例外</b>：{@code RedissonLeaderElector} 因选举语义需要 Redisson WatchDog + 线程持有判定，
 * 依据 §22.5.3 报备保留 {@code RLock} 直用，不通过本入口。
 *
 * @author ydsz-team
 * @since 26.09.01
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class CronjobRedisOps {

  /** 模块统一 key 前缀 */
  public static final String KEY_PREFIX = "ydsz:job:";

  private final RedisStringOps redisStringOps;

  // ======================== Key 构建 ========================

  /**
   * 构建带模块前缀的完整 Redis key。
   *
   * @param segments key segment（不含前缀）
   * @return 完整 key，如 {@code ydzs:job:running:count}
   */
  public static String buildKey(String... segments) {
    StringBuilder sb = new StringBuilder(KEY_PREFIX);
    for (int i = 0; i < segments.length; i++) {
      if (i > 0) {
        sb.append(':');
      }
      sb.append(segments[i]);
    }
    return sb.toString();
  }

  // ======================== String 操作 ========================

  /**
   * 原子递增。
   *
   * @param keySegment key segment（不含模块前缀）
   * @param delta      增量
   * @return 递增后的值；Redis 异常返回 null
   */
  public Long incr(String keySegment, long delta) {
    try {
      return redisStringOps.incr(buildKey(keySegment), delta);
    } catch (Exception e) {
      log.warn("[CronjobRedis] INCR 异常: key={} reason={}", keySegment, e.getMessage());
      return null;
    }
  }

  /**
   * 原子递减。
   *
   * @param keySegment key segment
   * @param delta      减量
   * @return 递减后的值；Redis 异常返回 0
   */
  public long decr(String keySegment, long delta) {
    try {
      return redisStringOps.decr(buildKey(keySegment), delta);
    } catch (Exception e) {
      log.warn("[CronjobRedis] DECR 异常: key={} reason={}", keySegment, e.getMessage());
      return 0;
    }
  }

  /**
   * 设置 key-value（带 TTL）。
   *
   * @param keySegment key segment
   * @param value      值
   * @param ttlSeconds TTL（秒）
   * @return true 设置成功
   */
  public boolean set(String keySegment, String value, long ttlSeconds) {
    try {
      redisStringOps.set(buildKey(keySegment), value, ttlSeconds);
      return true;
    } catch (Exception e) {
      log.warn("[CronjobRedis] SET 异常: key={} reason={}", keySegment, e.getMessage());
      return false;
    }
  }

  /**
   * 获取 key 的值。
   *
   * @param keySegment key segment
   * @param clazz      返回类型
   * @param <T>        返回值泛型类型
   * @return 值；不存在或异常返回 null
   */
  public <T> T get(String keySegment, Class<T> clazz) {
    try {
      return redisStringOps.get(buildKey(keySegment), clazz);
    } catch (Exception e) {
      log.debug("[CronjobRedis] GET 异常: key={} reason={}", keySegment, e.getMessage());
      return null;
    }
  }

  /**
   * 设置 key 过期时间。
   *
   * @param keySegment key segment
   * @param ttlSeconds TTL（秒）
   */
  public void expire(String keySegment, long ttlSeconds) {
    try {
      redisStringOps.expire(buildKey(keySegment), ttlSeconds);
    } catch (Exception e) {
      log.debug("[CronjobRedis] EXPIRE 异常: key={} reason={}", keySegment, e.getMessage());
    }
  }

  /**
   * 删除 key。
   *
   * @param keySegment key segment
   */
  public void delete(String keySegment) {
    try {
      redisStringOps.del(buildKey(keySegment));
    } catch (Exception e) {
      log.debug("[CronjobRedis] DEL 异常: key={} reason={}", keySegment, e.getMessage());
    }
  }

  /**
   * SETNX（key 不存在时设置）。
   *
   * @param keySegment key segment
   * @param value      值
   * @param ttlSeconds TTL（秒）
   * @return true 设置成功；false key 已存在或异常
   */
  public boolean setIfAbsent(String keySegment, String value, long ttlSeconds) {
    try {
      Boolean result = redisStringOps.setIfAbsent(buildKey(keySegment), value, ttlSeconds);
      return Boolean.TRUE.equals(result);
    } catch (Exception e) {
      log.debug("[CronjobRedis] SETNX 异常: key={} reason={}", keySegment, e.getMessage());
      return false;
    }
  }

  // ======================== 便捷方法 ========================

  /**
   * 获取 long 类型值（用于计数器读取）。
   *
   * @param keySegment key segment
   * @return 计数值；不存在或异常返回 0
   */
  public long getLong(String keySegment) {
    try {
      String value = redisStringOps.get(buildKey(keySegment), String.class);
      return value != null ? Long.parseLong(value) : 0;
    } catch (Exception e) {
      log.debug("[CronjobRedis] GET_LONG 异常: key={} reason={}", keySegment, e.getMessage());
      return 0;
    }
  }

  /**
   * 设置 long 类型值（用于计数器写入）。
   *
   * @param keySegment key segment
   * @param value      计数值
   */
  public void setLong(String keySegment, long value) {
    try {
      redisStringOps.set(buildKey(keySegment), String.valueOf(value));
    } catch (Exception e) {
      log.warn("[CronjobRedis] SET_LONG 异常: key={} reason={}", keySegment, e.getMessage());
    }
  }

  /**
   * 检查 key 是否存在。
   *
   * @param keySegment key segment
   * @return true 存在
   */
  public boolean exists(String keySegment) {
    try {
      return redisStringOps.hasKey(buildKey(keySegment));
    } catch (Exception e) {
      log.debug("[CronjobRedis] EXISTS 异常: key={} reason={}", keySegment, e.getMessage());
      return false;
    }
  }

  /**
   * 获取 key 的剩余 TTL（秒）。
   *
   * @param keySegment key segment
   * @return 剩余 TTL（秒）；key 不存在返回 -2；异常返回 -1
   */
  public long ttl(String keySegment) {
    try {
      return redisStringOps.getExpire(buildKey(keySegment));
    } catch (Exception e) {
      log.debug("[CronjobRedis] TTL 异常: key={} reason={}", keySegment, e.getMessage());
      return -1;
    }
  }
}
