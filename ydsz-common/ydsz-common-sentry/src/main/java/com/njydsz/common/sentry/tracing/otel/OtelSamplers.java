package com.njydsz.common.sentry.tracing.otel;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import lombok.Builder;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.context.Context;
import io.opentelemetry.sdk.trace.data.LinkData;
import io.opentelemetry.sdk.trace.samplers.Sampler;
import io.opentelemetry.sdk.trace.samplers.SamplingResult;

/**
 * YDSZ 采样器工厂
 *
 * <p>支持以下采样策略（按推荐优先级排序）：
 *
 * <ol>
 *   <li>{@link #parentBased(double)}：基于父 Span 决策（<b>生产环境首选</b>，保证分布式链路完整）
 *   <li>{@link #alwaysOn()}：100% 采集（开发/调试用）
 *   <li>{@link #alwaysOff()}：0% 采集（关闭追踪用）
 *   <li>{@link #ratio(double)}：按比例采样（无父 Span 场景）
 *   <li>{@link #composite(CompositeConfig)}：组合策略（按业务标签 / 服务名差异化，高级场景）
 * </ol>
 *
 * <p><b>OTel 标准对齐</b>：
 *
 * <ul>
 *   <li>{@link #parentBased(double)} 基于 OTel 官方 {@link Sampler#parentBased(Sampler)} 实现，
 *       是分布式追踪的推荐采样策略
 *   <li>健康检查路径过滤推荐通过 {@code SpanProcessor} 实现（而非采样器）， 避免在采样阶段访问 Span 属性
 * </ul>
 *
 * <p>对于错误 / 慢请求的标记与通知，可配合 {@link SpanEvaluationProcessor} 使用。
 *
 * @author ydsz-team
 * @since 2.0.0
 */
@Slf4j
public final class OtelSamplers {

  private OtelSamplers() {
    throw new UnsupportedOperationException("OtelSamplers is a utility class");
  }

  /**
   * 创建始终采样的采样器（100% 采集，适用于开发/调试环境）
   *
   * @return AlwaysOn Sampler
   */
  public static Sampler alwaysOn() {
    return Sampler.alwaysOn();
  }

  /**
   * 创建始终不采样的采样器（0% 采集，用于关闭追踪）
   *
   * @return AlwaysOff Sampler
   */
  public static Sampler alwaysOff() {
    return Sampler.alwaysOff();
  }

  /**
   * 创建按比例采样的采样器
   *
   * @param ratio 采样比例（0.0~1.0）
   * @return 比例采样器
   */
  public static Sampler ratio(double ratio) {
    if (ratio < 0.0 || ratio > 1.0) {
      throw new IllegalArgumentException("ratio must be in [0.0, 1.0], got: " + ratio);
    }
    return Sampler.traceIdRatioBased(ratio);
  }

  /**
   * 基于父 Span 决策的采样器。 父 Span 采样 → 子 Span 全部采样（保证分布式链路完整） 父 Span 不采样 → 子 Span 全部不采样 无父 Span → 走 ratio
   * 采样
   */
  public static Sampler parentBased(double ratio) {
    return Sampler.parentBased(Sampler.traceIdRatioBased(ratio));
  }

  /**
   * 组合采样器：根据服务名 / 灰度标签差异化采样。
   *
   * <p><b>适用场景</b>：需要按业务标签、服务名设置不同采样率的高级场景。
   *
   * <p><b>OTel 标准替代方案</b>：
   *
   * <ul>
   *   <li>推荐使用 {@link #parentBased(double)} 作为基础采样策略（父 Span 跟随 + 根 Span 比例采样）
   *   <li>健康检查路径过滤推荐通过 {@code SpanProcessor#onEnd} 实现（避免在采样阶段访问 Span 属性）
   *   <li>灰度标签差异化采样可通过自定义 {@code SpanProcessor} 在 Span 结束时调整
   * </ul>
   *
   * <p>本采样器保留用于需要"单一采样器内完成差异化决策"的场景。 新项目中建议优先使用标准 {@code parent-based} + {@code SpanProcessor} 组合。
   *
   * @param config 组合采样配置
   * @return 差异化采样器
   */
  public static Sampler composite(CompositeConfig config) {
    return new CompositeSampler(config);
  }

  // ============================================================================
  // 组合采样配置
  // ============================================================================

  /** 组合采样配置（高级场景使用）。 */
  @Data
  @Builder
  public static class CompositeConfig {
    /** 默认采样率 */
    @Builder.Default private double defaultRatio = 0.1;

    /** 服务名 → 采样率 */
    @Builder.Default private Map<String, Double> serviceRatios = new HashMap<>();

    /** 灰度标签 → 采样率（命中该 tag 的请求按此采样率） */
    @Builder.Default private Map<String, Double> grayTagRatios = new HashMap<>();

    /** 健康检查路径前缀（这些路径不采样） */
    @Builder.Default
    private List<String> healthCheckPaths = List.of("/actuator", "/health", "/metrics");
  }

  // ============================================================================
  // 组合采样器实现
  // ============================================================================

  /**
   * 组合采样器实现（内部类）。
   *
   * <p>按以下优先级决策：
   *
   * <ol>
   *   <li>健康检查路径 → DROP
   *   <li>父 Span 已采样 → RECORD（跟随父决策）
   *   <li>灰度标签命中 → 按灰度采样率
   *   <li>服务名命中 → 按服务采样率
   *   <li>默认采样率
   * </ol>
   */
  @Slf4j
  static class CompositeSampler implements Sampler {
    private final CompositeConfig config;

    CompositeSampler(CompositeConfig config) {
      this.config = config;
      log.info(
          "[Sentry] CompositeSampler 初始化，defaultRatio={}, 服务覆盖数={}, 灰度覆盖数={}",
          config.getDefaultRatio(),
          config.getServiceRatios().size(),
          config.getGrayTagRatios().size());
    }

    @Override
    public SamplingResult shouldSample(
        Context parentContext,
        String traceId,
        String name,
        SpanKind spanKind,
        Attributes attributes,
        List<LinkData> parentLinks) {
      // 1) 健康检查路径直接 DROP
      for (String prefix : config.getHealthCheckPaths()) {
        if (name != null && name.contains(prefix)) {
          return SamplingResult.drop();
        }
      }

      // 2) 父 Span 已决策：跟随父
      if (parentContext != null && Span.fromContext(parentContext).getSpanContext().isSampled()) {
        return SamplingResult.recordAndSample();
      }

      // 3) 灰度标签命中：按灰度采样率
      String grayTag = attributes.get(OtelSemConv.REMI_GRAY_TAG);
      if (grayTag != null) {
        Double ratio = config.getGrayTagRatios().get(grayTag);
        if (ratio != null) {
          return ratioBasedDecision(traceId, ratio);
        }
      }

      // 4) 服务名命中：按服务采样率
      String service = attributes.get(OtelSemConv.SERVICE_NAME);
      if (service != null) {
        Double ratio = config.getServiceRatios().get(service);
        if (ratio != null) {
          return ratioBasedDecision(traceId, ratio);
        }
      }

      // 5) 默认采样率
      return ratioBasedDecision(traceId, config.getDefaultRatio());
    }

    @Override
    public String getDescription() {
      return "YdszCompositeSampler{defaultRatio=" + config.getDefaultRatio() + "}";
    }

    /** 基于 traceId 哈希的固定比例采样（同一条 trace 的所有 Span 决策一致） */
    private SamplingResult ratioBasedDecision(String traceId, double ratio) {
      if (ratio >= 1.0) {
        return SamplingResult.recordAndSample();
      }
      if (ratio <= 0.0) {
        return SamplingResult.drop();
      }
      long threshold = (long) (ratio * Long.MAX_VALUE);
      long hash = Math.abs(traceId.hashCode());
      if (hash < threshold) {
        return SamplingResult.recordAndSample();
      }
      return SamplingResult.drop();
    }
  }
}
