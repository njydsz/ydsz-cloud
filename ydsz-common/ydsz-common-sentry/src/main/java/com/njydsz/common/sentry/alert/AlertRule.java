package com.njydsz.common.sentry.alert;

import java.io.Serializable;

import lombok.Builder;
import lombok.Data;

/**
 * 告警规则（PO）。
 *
 * <p>定义一条阈值型告警规则的完整配置，包含指标名、阈值、操作符、告警级别和抑制参数。
 *
 * <p>使用示例：
 * <pre>{@code
 * AlertRule rule = AlertRule.builder()
 *     .name("llm_error_rate_high")
 *     .metric("agent_llm_calls_total")
 *     .operator(AlertRule.Operator.GTE)
 *     .threshold(0.1)
 *     .level(AlertLevel.CRITICAL)
 *     .cooldownSeconds(300)
 *     .build();
 * }</pre>
 *
 * @author ydsz-team
 * @since 26.10.14
 */
@Data
@Builder
public class AlertRule implements Serializable {

  private static final long serialVersionUID = 1L;

  /** 规则名称（唯一标识，用于去重和日志） */
  private String name;

  /** 关联的指标名（如 {@code "agent_llm_calls_total"}） */
  private String metric;

  /** 阈值（与当前指标值比较） */
  private double threshold;

  /** 比较操作符 */
  private Operator operator;

  /** 命中规则时的告警级别 */
  private AlertLevel level;

  /** 告警抑制时间窗口（秒），在此窗口内同规则不重复触发 */
  private long cooldownSeconds;

  /** 是否启用此规则 */
  @Builder.Default
  private boolean enabled = true;

  /**
   * 比较操作符枚举。
   */
  public enum Operator {
    /** 大于 */
    GT,
    /** 大于等于 */
    GTE,
    /** 小于 */
    LT,
    /** 小于等于 */
    LTE,
    /** 等于 */
    EQ
  }

  /**
   * 评估当前值是否命中规则。
   *
   * @param value 当前指标值
   * @return 命中规则返回 true
   */
  public boolean evaluate(double value) {
    if (!enabled) {
      return false;
    }
    return switch (operator) {
      case GT -> value > threshold;
      case GTE -> value >= threshold;
      case LT -> value < threshold;
      case LTE -> value <= threshold;
      case EQ -> Double.compare(value, threshold) == 0;
    };
  }
}
