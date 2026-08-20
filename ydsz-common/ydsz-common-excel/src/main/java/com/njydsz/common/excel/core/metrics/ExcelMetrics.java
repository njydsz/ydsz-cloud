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
 * <p>当 MeterRegistry 不可用时（micrometer 未引入），所有方法为空操作， 不影响业务逻辑。
 *
 * @author ydsz-team
 * @since 1.0.0
 */
public class ExcelMetrics {

  private static final String TAG_RESULT = "result";
  private static final String TAG_ENGINE = "engine";

  private static volatile MeterRegistry registry;

  private static final ConcurrentHashMap<String, AtomicLong> gaugeMap = new ConcurrentHashMap<>();

  private ExcelMetrics() {}

  /**
   * 设置 MeterRegistry（由 AutoConfiguration 注入）
   *
   * @param meterRegistry Micrometer 注册表
   */
  public static void setRegistry(MeterRegistry meterRegistry) {
    registry = meterRegistry;
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
    if (registry == null) {
      return;
    }

    Timer.builder("excel.write.duration")
        .description("Excel write operation duration")
        .tags(
            Tags.of(
                Tag.of(TAG_ENGINE, engine), Tag.of(TAG_RESULT, success ? "success" : "failure")))
        .register(registry)
        .record(duration);

    if (success) {
      registry.counter("excel.rows.written", Tags.of(Tag.of(TAG_ENGINE, engine))).increment(rows);
    } else {
      registry.counter("excel.write.failures", Tags.of(Tag.of(TAG_ENGINE, engine))).increment();
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
    if (registry == null) {
      return;
    }

    Timer.builder("excel.read.duration")
        .description("Excel read operation duration")
        .tags(
            Tags.of(
                Tag.of(TAG_ENGINE, engine), Tag.of(TAG_RESULT, success ? "success" : "failure")))
        .register(registry)
        .record(duration);

    if (success) {
      registry.counter("excel.rows.read", Tags.of(Tag.of(TAG_ENGINE, engine))).increment(rows);
    } else {
      registry.counter("excel.read.failures", Tags.of(Tag.of(TAG_ENGINE, engine))).increment();
    }
  }

  /** 记录缓存命中 */
  public static void recordCacheHit(String cacheName) {
    if (registry == null) {
      return;
    }
    registry.counter("excel.cache.hits", Tags.of(Tag.of("cache", cacheName))).increment();
  }

  /** 记录缓存未命中 */
  public static void recordCacheMiss(String cacheName) {
    if (registry == null) {
      return;
    }
    registry.counter("excel.cache.misses", Tags.of(Tag.of("cache", cacheName))).increment();
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
    gaugeMap.put(name, value);
    registry.gauge("excel." + name, value);
  }
}
