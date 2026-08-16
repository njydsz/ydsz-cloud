package com.njydsz.common.auth.metrics;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Tag;
import io.micrometer.core.instrument.Tags;
import io.micrometer.core.instrument.Timer;

/**
 * 认证授权模块 Micrometer 指标采集器。
 *
 * <p>同时实现 {@link AuthMetrics} 与 {@link PermissionMetrics} 两个契约接口， 统一采集认证与授权核心链路指标。
 *
 * <p>采集以下指标：
 *
 * <ul>
 *   <li>{@code auth.login.total} - 认证总次数 Counter（tag: result, userType, reason）
 *   <li>{@code auth.login.duration} - 认证耗时 Timer（tag: result, userType）
 *   <li>{@code auth.permission.check.time} - 权限校验耗时 Timer
 *   <li>{@code auth.permission.deny.count} - 权限拒绝次数 Counter
 *   <li>{@code auth.permission.allow.count} - 权限通过次数 Counter
 *   <li>{@code auth.cache.hit} - 缓存命中次数 Counter
 *   <li>{@code auth.cache.miss} - 缓存未命中次数 Counter
 *   <li>{@code auth.redis.available} - Redis 可用状态 Gauge
 * </ul>
 *
 * <p><b>性能要点：</b>动态标签组合的 Counter/Timer 通过 {@link ConcurrentHashMap} 缓存， 避免每次调用都创建新的 Builder
 * 对象。Micrometer 内部对同 name+tags 的注册做了幂等处理， 但缓存 Builder 仍可减少对象分配与 map 查询开销。
 *
 * @author ydsz-team
 * @since 1.0.0
 * @see AuthMetrics
 * @see PermissionMetrics
 */
@ConditionalOnClass(MeterRegistry.class)
public class AuthMetricsCollector implements AuthMetrics, PermissionMetrics {

  private static final Logger log = LoggerFactory.getLogger(AuthMetricsCollector.class);

  /** 默认降级值，避免 null/空串污染指标基数 */
  private static final String UNKNOWN = "unknown";

  private final MeterRegistry meterRegistry;

  /** 动态标签 Counter 缓存，避免重复创建 Builder */
  private final ConcurrentMap<String, Counter> counterCache = new ConcurrentHashMap<>();

  /** 动态标签 Timer 缓存，避免重复创建 Builder */
  private final ConcurrentMap<String, Timer> timerCache = new ConcurrentHashMap<>();

  /** 权限校验指标（无动态标签，预注册即可） */
  private Counter permissionDenyCounter;

  private Counter permissionAllowCounter;
  private Counter cacheHitCounter;
  private Counter cacheMissCounter;
  private Timer permissionCheckTimer;

  /** Redis 可用状态，通过 Gauge 暴露到监控系统。 */
  private final AtomicInteger redisAvailable = new AtomicInteger(1);

  /**
   * 构造指标采集器。
   *
   * @param meterRegistry Micrometer 注册器
   */
  public AuthMetricsCollector(MeterRegistry meterRegistry) {
    this.meterRegistry = meterRegistry;
    initMetrics();
  }

  private void initMetrics() {
    this.permissionDenyCounter =
        Counter.builder("auth.permission.deny").description("权限拒绝次数").register(meterRegistry);

    this.permissionAllowCounter =
        Counter.builder("auth.permission.allow").description("权限通过次数").register(meterRegistry);

    this.cacheHitCounter =
        Counter.builder("auth.cache.hit").description("权限缓存命中次数").register(meterRegistry);

    this.cacheMissCounter =
        Counter.builder("auth.cache.miss").description("权限缓存未命中次数").register(meterRegistry);

    this.permissionCheckTimer =
        Timer.builder("auth.permission.check.time").description("权限校验耗时").register(meterRegistry);

    // Redis 可用状态 Gauge（绑定实例字段，可通过 updateRedisAvailable 动态更新）
    meterRegistry.gauge("auth.redis.available", redisAvailable, AtomicInteger::get);
  }

  // ==================== AuthMetrics 接口实现 ====================

  @Override
  public void recordAuthSuccess(String userType, long durationNanos) {
    String type = normalize(userType);
    getCounter("auth.login.total", "result", "success", "userType", type).increment();
    getTimer("auth.login.duration", "result", "success", "userType", type)
        .record(durationNanos, TimeUnit.NANOSECONDS);
  }

  @Override
  public void recordAuthFailure(String userType, String reason, long durationNanos) {
    String type = normalize(userType);
    String r = normalize(reason);
    getCounter("auth.login.total", "result", "failure", "userType", type, "reason", r).increment();
    getTimer("auth.login.duration", "result", "failure", "userType", type)
        .record(durationNanos, TimeUnit.NANOSECONDS);
  }

  @Override
  public void recordAuthSkip(String reason) {
    getCounter("auth.login.total", "result", "skip", "reason", normalize(reason)).increment();
  }

  // ==================== PermissionMetrics 接口实现 ====================

  @Override
  public void recordPermissionAllow(String permissionType) {
    permissionAllowCounter.increment();
  }

  @Override
  public void recordPermissionDeny(
      String userId, String permissionType, String requiredPermissions, String resource) {
    permissionDenyCounter.increment();
  }

  @Override
  public void recordCacheHit() {
    cacheHitCounter.increment();
  }

  @Override
  public void recordCacheMiss() {
    cacheMissCounter.increment();
  }

  @Override
  public void recordCheckTime(long nanos) {
    permissionCheckTimer.record(nanos, TimeUnit.NANOSECONDS);
  }

  @Override
  public void updateRedisAvailable(boolean available) {
    redisAvailable.set(available ? 1 : 0);
  }

  // ==================== 私有方法 ====================

  // ==================== 工具方法 ====================

  /**
   * 规范化标签值，避免 null/空串污染指标基数。
   *
   * @param value 原始值
   * @return 规范化后的值
   */
  private static String normalize(String value) {
    return (value == null || value.isBlank()) ? UNKNOWN : value;
  }

  /**
   * 获取（或创建）带动态标签的 Counter，使用缓存避免重复创建 Builder。
   *
   * @param name 指标名
   * @param tags 标签键值对（交替排列：key1, val1, key2, val2, ...）
   * @return Counter 实例
   */
  private Counter getCounter(String name, String... tags) {
    String cacheKey = buildCacheKey(name, tags);
    return counterCache.computeIfAbsent(
        cacheKey, k -> Counter.builder(name).tags(tags).register(meterRegistry));
  }

  /**
   * 获取（或创建）带动态标签的 Timer，使用缓存避免重复创建 Builder。
   *
   * @param name 指标名
   * @param tags 标签键值对（交替排列：key1, val1, key2, val2, ...）
   * @return Timer 实例
   */
  private Timer getTimer(String name, String... tags) {
    String cacheKey = buildCacheKey(name, tags);
    return timerCache.computeIfAbsent(
        cacheKey, k -> Timer.builder(name).tags(tags).register(meterRegistry));
  }

  private static String buildCacheKey(String name, String... tags) {
    StringBuilder sb = new StringBuilder(name);
    for (int i = 0; i < tags.length; i++) {
      sb.append(':').append(tags[i]);
    }
    return sb.toString();
  }

  /**
   * 创建带标签的 Tag 集合。
   *
   * @param key 标签键
   * @param value 标签值
   * @return Tag 集合
   */
  public static Tags tags(String key, String value) {
    return Tags.of(Tag.of(key, value));
  }
}
