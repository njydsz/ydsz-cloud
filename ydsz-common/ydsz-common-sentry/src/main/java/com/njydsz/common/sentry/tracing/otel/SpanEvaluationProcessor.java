package com.njydsz.common.sentry.tracing.otel;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Predicate;

import io.opentelemetry.api.trace.StatusCode;
import io.opentelemetry.context.Context;
import io.opentelemetry.sdk.trace.ReadWriteSpan;
import io.opentelemetry.sdk.trace.ReadableSpan;
import io.opentelemetry.sdk.trace.SpanProcessor;
import lombok.Builder;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;

/**
 * Span 评估处理器（Span Evaluation Processor）
 *
 * <p>对已结束的 Span 进行事后评估，通过规则引擎判断其是否属于"高价值 Span" （如错误请求、慢请求、灰度流量等），并通过 {@link DecisionListener}
 * 通知下游消费。
 *
 * <p><b>设计说明</b>：
 *
 * <ul>
 *   <li>OTel SDK 的 {@link SpanProcessor#onStart} 一旦返回，Span 即被记录到内存， {@link SpanProcessor#onEnd}
 *       阶段无法真正"丢弃"已记录的 Span。 因此本处理器仅做评估与通知，不做物理丢弃。
 *   <li>如需真正的尾部采样（Tail-Based Sampling），应使用 OTel 官方的 {@code TailSamplingProcessor}（{@code
 *       opentelemetry-sdk-extension-incubator}） 或 {@code ParentBasedSampler} + {@code
 *       RateLimitingSampler} 组合。
 * </ul>
 *
 * <p><b>典型用法</b>：
 *
 * <ul>
 *   <li>统计错误率趋势（通过 {@link DecisionListener} 采集决策指标）
 *   <li>触发告警（错误 Span 通知 {@code AlertPublisher}）
 *   <li>记录审计日志（灰度流量 Span 写入审计表）
 * </ul>
 *
 * <p><b>规则示例</b>：
 *
 * <ul>
 *   <li>HTTP 5xx 错误 100% 标记为 RECORD
 *   <li>耗时 &gt; 3s 的慢请求 100% 标记为 RECORD
 *   <li>错误码属于 P0 级别 100% 标记为 RECORD
 *   <li>命中灰度标签的请求 100% 标记为 RECORD
 * </ul>
 *
 * @author ydsz-team
 * @since 26.09.01
 */
@Slf4j
public class SpanEvaluationProcessor implements SpanProcessor {

  /** 总采样率（0.0 ~ 1.0），防止配额耗尽 */
  private final double recordRatio;

  /** 总请求计数器（用于配额计算） */
  private final AtomicLong totalCount = new AtomicLong(0);

  /** 已记录计数器 */
  private final AtomicLong recordedCount = new AtomicLong(0);

  /** 自定义采样规则 */
  private final List<SamplingRule> rules;

  /** 决策回调（用于测试 / 指标采集） */
  private final List<DecisionListener> listeners = new CopyOnWriteArrayList<>();

  /**
   * span evaluation processor。
   * @param recordRatio 参数
   * @param rules 参数
 */
  public SpanEvaluationProcessor(double recordRatio, List<SamplingRule> rules) {
    if (recordRatio < 0.0 || recordRatio > 1.0) {
      throw new IllegalArgumentException("recordRatio must be in [0.0, 1.0], got: " + recordRatio);
    }
    this.recordRatio = recordRatio;
    this.rules = rules == null ? List.of() : List.copyOf(rules);
    log.info(
        "[Sentry] SpanEvaluationProcessor 初始化完成，recordRatio={}, rules={}",
        recordRatio,
        this.rules.size());
  }

  @Override
  /**
   * on start。
   * @param parentContext 参数
   * @param span 参数
   */
  public void onStart(Context parentContext, ReadWriteSpan span) {
    // 启动阶段不决策，仅初始化上下文标记
    totalCount.incrementAndGet();
  }

  @Override
  /**
   * is start required。
   * @return 结果
   */
  public boolean isStartRequired() {
    return false;
  }

  @Override
  /**
   * on end。
   * @param span 参数
   */
  public void onEnd(ReadableSpan span) {
    Decision decision = evaluate(span);
    notifyListeners(span, decision);

    if (decision == Decision.RECORD) {
      recordedCount.incrementAndGet();
    }
    // 注意：OTel SDK 当前不支持丢弃已结束的 Span（一旦 onStart 就会记录），
    // 此处仅做评估与通知，不做物理丢弃。
    // 如需真正的尾部采样，请使用 OTel 官方的 TailSamplingProcessor。
  }

  @Override
  /**
   * is end required。
   * @return 结果
   */
  public boolean isEndRequired() {
    return true;
  }

  /** 评估 Span 采样决策 */
  private Decision evaluate(ReadableSpan span) {
    // 1) 命中自定义规则 → 强制记录
    for (SamplingRule rule : rules) {
      try {
        if (rule.getPredicate().test(span)) {
          return Decision.RECORD;
        }
      } catch (Exception e) {
        log.debug("[Sentry] 采样规则 {} 评估失败: {}", rule.getName(), e.getMessage());
      }
    }

    // 2) 走全局采样率
    long current = totalCount.get();
    if (current == 0) {
      return Decision.DROP;
    }
    double ratio = (double) recordedCount.get() / current;
    if (ratio < recordRatio) {
      return Decision.RECORD;
    }
    return Decision.DROP;
  }

  /** 添加决策监听器 */
  /**
   * add listener。
   * @param listener 参数
   */
  public void addListener(DecisionListener listener) {
    if (listener != null) {
      listeners.add(listener);
    }
  }

  private void notifyListeners(ReadableSpan span, Decision decision) {
    for (DecisionListener l : listeners) {
      try {
        l.onDecision(span, decision);
      } catch (Exception e) {
        // 监听器异常不影响主流程
        log.debug("[SpanEvaluation] 监听器异常: {}", e.getMessage());
      }
    }
  }

  /** 获取当前记录数 */
  /**
   * get recorded count。
   * @return 结果
   */
  public long getRecordedCount() {
    return recordedCount.get();
  }

  /** 获取总评估数 */
  /**
   * get total count。
   * @return 结果
   */
  public long getTotalCount() {
    return totalCount.get();
  }

  /** 计算实际采样率 */
  /**
   * get actual ratio。
   * @return 结果
   */
  public double getActualRatio() {
    long t = totalCount.get();
    return t == 0 ? 0.0 : (double) recordedCount.get() / t;
  }

  @Override
  /**
   * close。
   */
  public void close() {
    // no-op
  }

  // ============================================================================
  // 决策
  // ============================================================================

  /** 采样决策 */
  public enum Decision {
    /** 记录并上报 */
    RECORD,
    /** 丢弃 */
    DROP
  }

  // ============================================================================
  // 采样规则
  // ============================================================================

  /** 采样规则 */
  @Data
  @Builder
  public static class SamplingRule {
    /** 规则名称（用于日志/监控） */
    private String name;

    /** 谓词：返回 true 表示命中此规则 */
    private Predicate<ReadableSpan> predicate;
  }

  // ============================================================================
  // 决策监听器
  // ============================================================================

  /** 决策监听器 */
  @FunctionalInterface
  public interface DecisionListener {
    void onDecision(ReadableSpan span, Decision decision);
  }

  // ============================================================================
  // 规则工厂
  // ============================================================================

  /** 规则工厂：常用规则预设 */
  public static class Rules {

    private Rules() {
      // 工具类，禁止实例化
    }

    /** 错误状态码规则（HTTP 5xx 或 Span 状态为 ERROR） */
    /**
     * error status。
     * @return 结果
     */
    public static SamplingRule errorStatus() {
      return SamplingRule.builder()
          .name("error-status")
          .predicate(
              span -> {
                // 1) OTel Span 自身状态
                if (span.toSpanData().getStatus().getStatusCode() == StatusCode.ERROR) {
                  return true;
                }
                // 2) HTTP 状态码 5xx
                Long status = span.getAttribute(OtelSemConv.HTTP_RESPONSE_STATUS_CODE);
                if (status != null && status >= 500 && status < 600) {
                  return true;
                }
                return false;
              })
          .build();
    }

    /** 慢请求规则（超过指定毫秒） */
    /**
     * slow request。
     * @param thresholdMillis 参数
     * @return 结果
     */
    public static SamplingRule slowRequest(long thresholdMillis) {
      return SamplingRule.builder()
          .name("slow-request-" + thresholdMillis + "ms")
          .predicate(
              span -> {
                long durationNanos = span.getLatencyNanos();
                return durationNanos > thresholdMillis * 1_000_000L;
              })
          .build();
    }

    /**
     * 错误码规则（YDSZ 自定义错误码命中指定前缀）。
     *
     * @param prefixes 错误码前缀列表
     * @return 错误码采样规则
     */
    public static SamplingRule errorCode(String... prefixes) {
      return SamplingRule.builder()
          .name("error-code")
          .predicate(
              span -> {
                String code = span.getAttribute(OtelSemConv.REMI_ERROR_CODE);
                if (code == null) {
                  return false;
                }
                for (String p : prefixes) {
                  if (code.startsWith(p)) {
                    return true;
                  }
                }
                return false;
              })
          .build();
    }

    /** 灰度标签规则（命中指定 tag） */
    /**
     * gray tag。
     * @param tagValue 参数
     * @return 结果
     */
    public static SamplingRule grayTag(String tagValue) {
      return SamplingRule.builder()
          .name("gray-tag-" + tagValue)
          .predicate(span -> tagValue.equals(span.getAttribute(OtelSemConv.REMI_GRAY_TAG)))
          .build();
    }

    /** 压测流量规则 */
    /**
     * pressure traffic。
     * @return 结果
     */
    public static SamplingRule pressureTraffic() {
      return SamplingRule.builder()
          .name("pressure-traffic")
          .predicate(
              span -> {
                String tag = span.getAttribute(OtelSemConv.REMI_PRESSURE_TAG);
                return tag != null && !tag.isEmpty();
              })
          .build();
    }
  }
}
