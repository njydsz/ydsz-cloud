package com.njydsz.common.base.metrics;

import java.util.function.Supplier;

/**
 * 轻量指标桥接接口（L1 纯 JDK，无 Micrometer 依赖）。
 *
 * <p>为 common L3 模块（auth/cache/redis/safe 等）提供统一的指标注册/上报入口，解耦业务代码与 Micrometer API 的直接依赖。 符合 P0 规则
 * YDIZ-SENTRY-001「禁止业务代码直接 import Micrometer API」。
 *
 * <p><b>设计定位</b>：本接口仅承载 <b>增量上报</b>语义（increment/record/set）， <b>不</b>承载 Micrometer 的
 * FunctionCounter/FunctionTimer 等"轮询语义"（它们需要 CallbackRegistrar）。 对于需要实时暴露状态值的场景（如缓存命中率），推荐方案是：
 *
 * <ol>
 *   <li>注册一次 {@link #setGauge(String, double, String...)} + 定时任务刷新</li>
 *   <li>或继续使用 Micrometer CacheMeterBinder（需在具体使用处依赖 Micrometer）</li>
 * </ol>
 *
 * <p><b>实现</b>：{@link MicrometerFunctionMetricsBridge} 提供 Micrometer 后端实现； 业务单元测试中可注入
 * {@link NoopFunctionMetricsBridge} 关闭指标。
 *
 * <p><b>使用示例：</b>
 *
 * <pre>{@code
 * &#64;Component
 * public class SomeComponent {
 *     private final FunctionMetricsBridge metrics;
 *     public SomeComponent(FunctionMetricsBridge metrics) { this.metrics = metrics; }
 *     public void doWork(String type) {
 *         metrics.recordTimed("work_duration_ms", () -> heavyWork(type), "type", type);
 *     }
 * }
 * }</pre>
 *
 * @author ydsz-team
 * @since 26.10.01
 * @see MicrometerFunctionMetricsBridge
 * @see NoopFunctionMetricsBridge
 */
public interface FunctionMetricsBridge {

  /**
   * 递增计数器 1。
   *
   * <p>首次调用时自动注册 Micrometer Counter，后续调用直接递增缓存引用。
   *
   * @param name 指标名称（如 "auth.login.success.total"）
   * @param tags 标签键值对（k1, v1, k2, v2...），长度必须为偶数
   */
  void incrementCounter(String name, String... tags);

  /**
   * 递增计数器（指定增量）。
   *
   * @param name 指标名称
   * @param amount 增量（通常为正值，可为小数）
   * @param tags 标签键值对
   */
  void incrementCounter(String name, double amount, String... tags);

  /**
   * 记录耗时。
   *
   * <p>首次调用时自动注册 Micrometer Timer（含 SLO 百分位），后续调用直接记录到缓存引用。
   *
   * @param name 指标名称（如 "auth.login.duration.ms"）
   * @param durationMs 耗时（毫秒）
   * @param tags 标签键值对
   */
  void recordDuration(String name, long durationMs, String... tags);

  /**
   * 记录 Supplier 执行耗时（便捷方法）。
   *
   * <p>等价于 {@code long start = ...; try { return supplier.get(); } finally { recordDuration(name,
   * System.currentTimeMillis()-start, tags); }}。 Supplier 抛出的异常会上透，仍会记录耗时。
   *
   * @param name 指标名称
   * @param supplier 业务逻辑
   * @param tags 标签键值对
   * @param <T> 返回类型
   * @return supplier 返回值
   */
  <T> T recordTimed(String name, Supplier<T> supplier, String... tags);

  /**
   * 设置 Gauge 值（一次性）。
   *
   * <p>通过 AtomicReference 缓存引用，后续调用更新同一引用值。Meter 仅在首次调用时注册。
   *
   * @param name 指标名称（如 "cache.size"）
   * @param value 当前值
   * @param tags 标签键值对
   */
  void setGauge(String name, Double value, String... tags);

  /**
   * 判断底层实现是否可用（检查 MeterRegistry 非空等）。
   *
   * @return true 表示可正常上报；false 表示底层不可用，所有上报为 no-op
   */
  default boolean isAvailable() {
    return true;
  }
}
