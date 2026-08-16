package com.njydsz.common.sentry.sla;

import com.njydsz.common.sentry.domain.SlaDefinition;
import com.njydsz.common.sentry.spi.MetricsCollector;
import com.njydsz.common.sentry.spi.SlaCollector;
import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import lombok.extern.slf4j.Slf4j;

/**
 * SLA 指标采集器实现。
 *
 * <p>基于 {@link MetricsCollector} 采集 SLA（Service Level Agreement）指标， 包括步骤耗时、总耗时和违反次数。配合 {@link
 * SlaMetricAspect} AOP 切面使用， 在标注了 {@code @SlaMetric} 的方法上自动采集 SLA 数据。
 *
 * <h3>采集的指标</h3>
 *
 * <ul>
 *   <li>{@code sla.<name>.step.<step>.duration}：各步骤耗时
 *   <li>{@code sla.<name>.total.duration}：总耗时
 *   <li>{@code sla.<name>.violation.count}：SLA 违反次数（超时）
 * </ul>
 *
 * <h3>线程安全</h3>
 *
 * <p>使用 {@link ConcurrentHashMap} 存储运行中的 SLA 上下文，支持并发采集。
 *
 * @author ydsz-team
 * @since 1.0.0
 * @see SlaCollector
 * @see SlaMetricAspect
 * @see SlaDefinition
 */
@Slf4j
public class DefaultSlaCollector implements SlaCollector {

  private final MetricsCollector metricsCollector;
  private final Map<String, SlaDefinition> definitions = new ConcurrentHashMap<>();

  /**
   * 构造函数，初始化 SLA 指标采集器
   *
   * @param metricsCollector 指标采集器
   */
  public DefaultSlaCollector(MetricsCollector metricsCollector) {
    this.metricsCollector = metricsCollector;
    log.info("[Sentry] DefaultSlaCollector 初始化完成");
  }

  /**
   * 注册 SLA 定义
   *
   * @param definition SLA 定义
   */
  @Override
  public void register(SlaDefinition definition) {
    definitions.put(definition.getName(), definition);
    log.info(
        "[Sentry] SLA 注册: name={}, threshold={}ms, target={}",
        definition.getName(),
        definition.getThresholdMillis(),
        definition.getTarget());
  }

  /**
   * 记录 SLA 步骤执行结果
   *
   * @param name SLA 名称
   * @param stepName 步骤名
   * @param tookMillis 耗时（毫秒）
   * @param success 是否成功
   */
  @Override
  public void record(String name, String stepName, long tookMillis, boolean success) {
    if (metricsCollector == null || name == null) {
      return;
    }
    SlaDefinition def = definitions.get(name);
    if (def != null) {
      // 找到步骤定义
      SlaDefinition.SlaStep step =
          def.getSteps().stream()
              .filter(s -> s.getName() != null && s.getName().equals(stepName))
              .findFirst()
              .orElse(null);

      Map<String, String> tags = new HashMap<>(3);
      tags.put("sla", name);
      tags.put("step", stepName != null ? stepName : "unknown");
      tags.put("success", String.valueOf(success));

      metricsCollector.recordTimer(
          "ydsz.sla.step.duration", "SLA 步骤耗时", tags, Duration.ofMillis(tookMillis));
      metricsCollector.incrementCounter("ydsz.sla.step.total", "SLA 步骤总执行次数", tags, 1);

      if (!success) {
        metricsCollector.incrementCounter("ydsz.sla.step.failed", "SLA 步骤失败次数", tags, 1);
      }

      if (step != null && tookMillis > step.getTimeoutMillis()) {
        metricsCollector.incrementCounter("ydsz.sla.step.timeout", "SLA 步骤超时次数", tags, 1);
      }
    }
  }

  @Override
  public void recordTotal(String name, long tookMillis, boolean success) {
    if (metricsCollector == null || name == null) {
      return;
    }
    SlaDefinition def = definitions.get(name);
    if (def == null) {
      return;
    }

    Map<String, String> tags = new HashMap<>(2);
    tags.put("sla", name);
    tags.put("success", String.valueOf(success));

    metricsCollector.recordTimer(
        "ydsz.sla.total.duration", "SLA 总耗时", tags, Duration.ofMillis(tookMillis));
    metricsCollector.incrementCounter("ydsz.sla.total.count", "SLA 执行总次数", tags, 1);

    if (!success) {
      metricsCollector.incrementCounter("ydsz.sla.total.failed", "SLA 失败次数", tags, 1);
    }

    if (tookMillis > def.getThresholdMillis()) {
      metricsCollector.incrementCounter("ydsz.sla.violation", "SLA 违反次数", Map.of("sla", name), 1);
      log.warn(
          "[Sentry] SLA 违反: name={}, took={}ms, threshold={}ms",
          name,
          tookMillis,
          def.getThresholdMillis());
    }
  }

  @Override
  public boolean isAvailable() {
    return metricsCollector != null && metricsCollector.isAvailable();
  }
}
