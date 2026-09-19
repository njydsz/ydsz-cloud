package com.njydsz.common.redis.service.ops;

import java.nio.charset.StandardCharsets;
import java.util.Optional;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.data.redis.connection.RedisCallback;
import org.springframework.data.redis.core.RedisTemplate;

import com.njydsz.common.redis.config.RedisProperties;
import com.njydsz.common.redis.metrics.RedisMetricsCollector;
import com.njydsz.common.redis.tenant.TenantRedisKeyPrefixer;

/**
 * Redis Bitmap 位图操作封装
 *
 * <p>提供 SETBIT、GETBIT、BITCOUNT 等位图操作，支持基于位偏移的布尔标记和统计。
 * 从 {@link RedisStringOps} 中拆分出来以遵循单一职责原则。
 *
 * <p><b>位图典型使用场景：</b>
 *
 * <ul>
 *   <li>用户签到/打卡记录（offset = 日期偏移）
 *   <li>布隆过滤器的底层存储
 *   <li>资源分配标记（如座位锁定）
 *   <li>计数型 BITCOUNT（统计活跃用户数等）
 * </ul>
 *
 * <p>所有操作均使用全租户前缀 + 应用前缀的组合，保证多租户隔离。
 *
 * @author ydsz-team
 * @since 26.09.01
 */
@Slf4j
@RequiredArgsConstructor
public class RedisBitmapOps {

  private final RedisTemplate<String, Object> redisTemplate;
  private final RedisProperties redisProperties;
  private final RedisMetricsCollector metricsCollector;
  private final TenantRedisKeyPrefixer tenantKeyPrefixer;

  /**
   * 构造 Redis Bitmap 操作组件
   *
   * @param redisTemplate Redis 模板（必须）
   * @param redisProperties Redis 配置属性（必须）
   * @param metricsProvider 指标采集器提供者（可选）
   * @param tenantPrefixerProvider 租户 Key 前缀器提供者（可选）
   */
  public RedisBitmapOps(
      RedisTemplate<String, Object> redisTemplate,
      RedisProperties redisProperties,
      ObjectProvider<RedisMetricsCollector> metricsProvider,
      ObjectProvider<TenantRedisKeyPrefixer> tenantPrefixerProvider) {
    this.redisTemplate = redisTemplate;
    this.redisProperties = redisProperties;
    this.metricsCollector = metricsProvider.getIfAvailable();
    this.tenantKeyPrefixer = tenantPrefixerProvider.getIfAvailable();
  }

  /**
   * 设置位图值
   *
   * @param key 键（自动添加租户+应用前缀）
   * @param offset 位偏移量（从 0 开始）
   * @param value 位值（true-1，false-0）
   * @return 设置前的原值；失败时返回 false
   */
  public boolean setBit(String key, long offset, boolean value) {
    if (key == null) {
      return false;
    }
    String formattedKey = formatKey(key);
    try {
      return metricsCollector != null
          ? metricsCollector.recordOperation(
              "setBit",
              () ->
                  Boolean.TRUE.equals(
                      redisTemplate.opsForValue().setBit(formattedKey, offset, value)))
          : Boolean.TRUE.equals(redisTemplate.opsForValue().setBit(formattedKey, offset, value));
    } catch (Exception e) {
      recordError("setBit", e);
      log.error("【Redis】SETBIT 操作失败 | key={} | offset={} | error={}", key, offset, e);
      return false;
    }
  }

  /**
   * 获取位图值
   *
   * @param key 键
   * @param offset 位偏移量
   * @return true-该位为 1；false-该位为 0 或 key 不存在
   */
  public boolean getBit(String key, long offset) {
    if (key == null) {
      return false;
    }
    String formattedKey = formatKey(key);
    try {
      return metricsCollector != null
          ? metricsCollector.recordOperation(
              "getBit",
              () -> Boolean.TRUE.equals(redisTemplate.opsForValue().getBit(formattedKey, offset)))
          : Boolean.TRUE.equals(redisTemplate.opsForValue().getBit(formattedKey, offset));
    } catch (Exception e) {
      recordError("getBit", e);
      log.error("【Redis】GETBIT 操作失败 | key={} | offset={} | error={}", key, offset, e);
      return false;
    }
  }

  /**
   * 统计位图中值为 1 的位数（BITCOUNT）。
   *
   * <p>适用于统计活跃用户数、符合某条件的记录总数等场景。
   *
   * @param key 键
   * @return 1 的位数；失败或 key 不存在时返回 0
   */
  public long bitCount(String key) {
    if (key == null) {
      return 0;
    }
    String formattedKey = formatKey(key);
    try {
      return metricsCollector != null
          ? metricsCollector.recordOperation(
              "bitCount",
              () -> {
                Long count =
                    redisTemplate.execute(
                        (RedisCallback<Long>)
                            connection ->
                                connection
                                    .stringCommands()
                                    .bitCount(formattedKey.getBytes(StandardCharsets.UTF_8)));
                return count != null ? count : 0L;
              })
          : Optional.ofNullable(
                  redisTemplate.execute(
                      (RedisCallback<Long>)
                          connection ->
                              connection
                                  .stringCommands()
                                  .bitCount(formattedKey.getBytes(StandardCharsets.UTF_8))))
              .orElse(0L);
    } catch (Exception e) {
      recordError("bitCount", e);
      log.error("【Redis】BITCOUNT 操作失败 | key={} | error={}", key, e);
      return 0;
    }
  }

  // ---------------------------------------------------------------------------
  // 私有辅助方法
  // ---------------------------------------------------------------------------

  /**
   * 格式化 Key，添加统一前缀（租户前缀 + 应用前缀）
   *
   * @param key 原始键
   * @return 带前缀的键
   */
  private String formatKey(String key) {
    if (key == null) {
      return null;
    }
    String result = key;
    if (tenantKeyPrefixer != null) {
      result = tenantKeyPrefixer.prefixKey(result);
    }
    String prefix = redisProperties != null ? redisProperties.getKeyPrefix() : null;
    if (prefix != null && !prefix.isEmpty()) {
      result = prefix + ":" + result;
    }
    return result;
  }

  /**
   * 记录指标错误
   *
   * @param operationType 操作类型
   * @param e 异常信息
   */
  private void recordError(String operationType, Throwable e) {
    if (metricsCollector != null) {
      metricsCollector.recordError(operationType, e);
    }
  }
}
