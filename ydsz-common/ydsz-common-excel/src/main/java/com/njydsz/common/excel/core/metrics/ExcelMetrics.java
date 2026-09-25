package com.njydsz.common.excel.core.metrics;

import java.time.Duration;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Tag;
import io.micrometer.core.instrument.Tags;
import io.micrometer.core.instrument.Timer;

/**
 * Excel 模块可观测性指标
 *
 * <p>基于 Micrometer 的指标采集，覆盖 Excel 读写全流程关键路径。
 *
 * <h3>指标清单</h3>
 *
 * <ul>
 *   <li>{@code excel.write.duration} — 写入耗时（Timer, P50/P90/P99）
 *   <li>{@code excel.read.duration} — 读取耗时（Timer, P50/P90/P99）
 *   <li>{@code excel.rows.written} — 写入行数（Counter）
 *   <li>{@code excel.rows.read} — 读取行数（Counter）
 *   <li>{@code excel.write.failures} — 写入失败次数（Counter）
 *   <li>{@code excel.read.failures} — 读取失败次数（Counter）
 *   <li>{@code excel.cache.hits} — 缓存命中次数（Counter）
 *   <li>{@code excel.cache.misses} — 缓存未命中次数（Counter）
 * </ul>
 *
 * <p>Timer 实例按 Tags 组合缓存，避免高并发下 Meter 无限增长（此前每次 recordWrite/recordRead
 * 调用 {@code Timer.builder().register()} 隐式创建新 Meter）。Counter/Gauge 同理。
 *
 * <p>当 MeterRegistry 不可用时（micrometer 未引入），所有方法为空操作， 不影响业务逻辑。
 *
 * @author ydsz-team
 * @since 26.09.01
 */
public class ExcelMetrics {

  private static final String TAG_RESULT = "result";
  private static final String TAG_ENGINE = "engine";
  private static final String TAG_MODULE = "module";

  private static volatile MeterRegistry registry;

  private static final ConcurrentHashMap<String, AtomicLong> GAUGE_MAP = new ConcurrentHashMap<>();

  /** Timer 缓存：key = metricName + tags hash，避免重复注册 */
  private static final ConcurrentHashMap<String, Timer> TIMER_CACHE = new ConcurrentHashMap<>();

  /** 当前活跃的读写操作数（Gauge 用） */
  private static final AtomicLong ACTIVE_OPERATIONS = new AtomicLong(0);

  /** 累计错误数（Gauge 用） */
  private static final AtomicLong TOTAL_ERRORS = new AtomicLong(0);

  /** 最后一次错误的时间戳（epoch ms），无错误时为 0 */
  private static final AtomicLong LAST_ERROR_TIMESTAMP = new AtomicLong(0);

  private ExcelMetrics() {}

  static {
    GAUGE_MAP.put("active.operations", ACTIVE_OPERATIONS);
    GAUGE_MAP.put("total.errors", TOTAL_ERRORS);
    //LAST_ERROR_TIMESTAMP 不设 Gauge —— 异常值（epoch ms）不适合作为指标；由 HealthIndicator 直接读取
  }

  /**
   * 设置 MeterRegistry（由 AutoConfiguration 注入）
   *
   * <p>注册时将注册所有预定义的 Gauge 指标（{@code active.operations}, {@code total.errors}）。
   *
   * @param meterRegistry Micrometer 注册表
   */
  public static void setRegistry(MeterRegistry meterRegistry) {
    registry = meterRegistry;
    TIMER_CACHE.clear();
    // 注册预定义 Gauge
    GAUGE_MAP.forEach(
        (name, value) -> {
          if (registry != null) {
            registry.gauge("excel." + name, value);
          }
        });
  }

  /**
   * 标记一个读写操作开始（活跃操作数 +1）。
   */
  public static void incrementActiveOperations() {
    ACTIVE_OPERATIONS.incrementAndGet();
  }

  /**
   * 标记一个读写操作结束（活跃操作数 -1）。
   */
  public static void decrementActiveOperations() {
    ACTIVE_OPERATIONS.decrementAndGet();
  }

  /**
   * 记录一次错误：累计错误数 +1，并刷新最后错误时间戳。
   */
  public static void recordError() {
    TOTAL_ERRORS.incrementAndGet();
    LAST_ERROR_TIMESTAMP.set(System.currentTimeMillis());
  }

  /**
   * 返回当前活跃的读写操作数。
   *
   * @return 活跃操作计数
   */
  public static long getActiveOperations() {
    return ACTIVE_OPERATIONS.get();
  }

  /**
   * 返回累计错误总数。
   *
   * @return 错误计数
   */
  public static long getTotalErrors() {
    return TOTAL_ERRORS.get();
  }

  /**
   * 返回最后一次错误的时间戳（epoch ms），从未出错时为 0。
   *
   * @return 最后错误时间戳
   */
  public static long getLastErrorTimestamp() {
    return LAST_ERROR_TIMESTAMP.get();
  }

  /**
   * 记录写入操作耗时
   *
   * @param duration 耗时
   * @param rows 写入行数
   * @param engine 引擎类型（fast/poi）
   * @param success 是否成功
   */
  public static void recordWrite(Duration duration, int rows, String engine, boolean success) {
    recordWrite(duration, rows, engine, success, null);
  }

  /**
   * 记录写入操作耗时（含模块标识）。
   *
   * @param duration 耗时
   * @param rows 写入行数
   * @param engine 引擎类型（fast/poi）
   * @param success 是否成功
   * @param module 来源模块标识（如 "userinfo"、"workflow"）；为 {@code null} 时不输出该 tag
   */
  public static void recordWrite(
      Duration duration, int rows, String engine, boolean success, String module) {
    if (registry == null) {
      return;
    }

    Tags baseTags = Tags.of(
        Tag.of(TAG_ENGINE, engine), Tag.of(TAG_RESULT, success ? "success" : "failure"));
    Tags moduleTags = module != null ? baseTags.and(Tag.of(TAG_MODULE, module)) : baseTags;

    Timer timer = TIMER_CACHE.computeIfAbsent(
        cacheKey("excel.write.duration", moduleTags),
        k -> Timer.builder("excel.write.duration")
            .description("Excel write operation duration")
            .tags(moduleTags)
            .register(registry));
    timer.record(duration);

    if (success) {
      registry.counter("excel.rows.written", moduleTags).increment(rows);
    } else {
      registry.counter("excel.write.failures", moduleTags).increment();
    }
  }

  /**
   * 记录读取操作耗时
   *
   * @param duration 耗时
   * @param rows 读取行数
   * @param engine 引擎类型（fast/poi）
   * @param success 是否成功
   */
  public static void recordRead(Duration duration, int rows, String engine, boolean success) {
    recordRead(duration, rows, engine, success, null);
  }

  /**
   * 记录读取操作耗时（含模块标识）。
   *
   * @param duration 耗时
   * @param rows 读取行数
   * @param engine 引擎类型（fast/poi）
   * @param success 是否成功
   * @param module 来源模块标识（如 "userinfo"、"workflow"）；为 {@code null} 时不输出该 tag
   */
  public static void recordRead(
      Duration duration, int rows, String engine, boolean success, String module) {
    if (registry == null) {
      return;
    }

    Tags baseTags = Tags.of(
        Tag.of(TAG_ENGINE, engine), Tag.of(TAG_RESULT, success ? "success" : "failure"));
    Tags moduleTags = module != null ? baseTags.and(Tag.of(TAG_MODULE, module)) : baseTags;

    Timer timer = TIMER_CACHE.computeIfAbsent(
        cacheKey("excel.read.duration", moduleTags),
        k -> Timer.builder("excel.read.duration")
            .description("Excel read operation duration")
            .tags(moduleTags)
            .register(registry));
    timer.record(duration);

    if (success) {
      registry.counter("excel.rows.read", moduleTags).increment(rows);
    } else {
      registry.counter("excel.read.failures", moduleTags).increment();
    }
  }

  /**
   * 记录缓存命中
   *
   * @param cacheName 缓存名称
   */
  public static void recordCacheHit(String cacheName) {
    if (registry == null) {
      return;
    }
    registry.counter("excel.cache.hits", Tags.of(Tag.of("cache", cacheName))).increment();
  }

  /**
   * 记录缓存未命中
   *
   * @param cacheName 缓存名称
   */
  public static void recordCacheMiss(String cacheName) {
    if (registry == null) {
      return;
    }
    registry.counter("excel.cache.misses", Tags.of(Tag.of("cache", cacheName))).increment();
  }

  /**
   * 生成 Timer 缓存 key。
   *
   * <p>由指标名 + Tags 组合拼接而成，保证不同 engine/module/result 组合各有唯一缓存条目。
   *
   * @param metricName 指标名称
   * @param tags 标签集合
   * @return 缓存 key 字符串
   */
  private static String cacheKey(String metricName, Tags tags) {
    StringBuilder sb = new StringBuilder(metricName);
    tags.forEach(tag -> sb.append('|').append(tag.getKey()).append('=').append(tag.getValue()));
    return sb.toString();
  }

  /**
   * 注册 Gauge 指标
   *
   * @param name 指标名
   * @param value 数值提供者
   */
  public static void registerGauge(String name, AtomicLong value) {
    if (registry == null) {
      return;
    }
    GAUGE_MAP.put(name, value);
    registry.gauge("excel." + name, value);
  }
}
