package com.njydsz.common.base.metrics;

import java.util.function.Supplier;

/**
 * 空实现 {@link FunctionMetricsBridge}（no-op）。
 *
 * <p>用于以下场景：
 *
 * <ul>
 *   <li>单元测试中避免依赖 Micrometer 或 Spring 容器</li>
 *   <li>未装配 MeterRegistry 时的降级（如开发环境、本地 profile）</li>
 *   <li>利用 {@code @ConditionalOnBean(MeterRegistry.class)} 不装配 Micrometer 实现时，注入本空实现</li>
 * </ul>
 *
 * <p>所有方法均为 no-op，无副作用，无线程安全问题。
 *
 * @author ydsz-team
 * @since 26.10.01
 * @see FunctionMetricsBridge
 * @see MicrometerFunctionMetricsBridge
 */
public final class NoopFunctionMetricsBridge implements FunctionMetricsBridge {

  /** 单例 */
  public static final NoopFunctionMetricsBridge INSTANCE = new NoopFunctionMetricsBridge();

  private NoopFunctionMetricsBridge() {}

  /**
   * 获取单例。
   *
   * @return 单例实例
   */
  public static NoopFunctionMetricsBridge getInstance() {
    return INSTANCE;
  }

  @Override
  public void incrementCounter(String name, String... tags) {
    // no-op
  }

  @Override
  public void incrementCounter(String name, double amount, String... tags) {
    // no-op
  }

  @Override
  public void recordDuration(String name, long durationMs, String... tags) {
    // no-op
  }

  @Override
  public <T> T recordTimed(String name, Supplier<T> supplier, String... tags) {
    return supplier.get();
  }

  @Override
  public void setGauge(String name, Double value, String... tags) {
    // no-op
  }

  /** 空实现始终标记为不可用。 */
  @Override
  public boolean isAvailable() {
    return false;
  }
}
