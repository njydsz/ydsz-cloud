package com.njydsz.literule.server.metrics;

import com.njydsz.common.base.metrics.AbstractMetricsHolder;

/**
 * 规则引擎运行态 Metrics 静态持有者。
 *
 * <p>为规则引擎核心路径提供 Micrometer 指标注册与累加能力， 通过静态方法方便业务代码（如 {@code DefaultRuleEngine}、{@code
 * ParallelRuleEvaluator}）埋点。
 *
 * <p>继承 {@link AbstractMetricsHolder}，仅保留本模块的业务语义方法， 注册表绑定与缓存去重由父类统一处理。
 *
 * <p>暴露的 Prometheus 指标：
 *
 * <ul>
 *   <li>{@code literule.hit_total{rule_id,tag}} — 规则命中计数
 *   <li>{@code literule.evaluation_duration{rule_id}} — 规则评估耗时分布
 *   <li>{@code literule.error_total{rule_id}} — 规则评估失败计数
 * </ul>
 *
 * @author ydsz-team
 * @since 1.0.0
 */
public final class LiteruleMetricsHolder extends AbstractMetricsHolder {

  /** 模块指标前缀 */
  private static final String METRIC_PREFIX = "literule.";

  private LiteruleMetricsHolder() {
    throw new UnsupportedOperationException("utility class");
  }

  // ======================== 规则命中计数 ========================

  /**
   * 递增规则命中计数（{@code literule.hit_total}）。
   *
   * @param ruleId 规则编码（rule_id 标签）
   * @param tag 场景/标签（tag 标签，如 "DEFAULT" / "APPROVE"）
   */
  public static void incrementHit(String ruleId, String tag) {
    registerCounter(METRIC_PREFIX, "hit_total", "rule_id", safe(ruleId), "tag", safe(tag))
        .increment();
  }

  // ======================== 规则评估耗时 ========================

  /**
   * 记录规则评估耗时（{@code literule.evaluation_duration}）。
   *
   * @param ruleId 规则编码
   * @param millis 评估耗时（毫秒）
   */
  public static void recordEvaluationDuration(String ruleId, long millis) {
    recordDuration(METRIC_PREFIX, "evaluation_duration", millis, "rule_id", safe(ruleId));
  }

  // ======================== 规则评估失败计数 ========================

  /**
   * 递增规则评估失败计数（{@code literule.error_total}）。
   *
   * @param ruleId 规则编码
   */
  public static void incrementError(String ruleId) {
    registerCounter(METRIC_PREFIX, "error_total", "rule_id", safe(ruleId)).increment();
  }
}
