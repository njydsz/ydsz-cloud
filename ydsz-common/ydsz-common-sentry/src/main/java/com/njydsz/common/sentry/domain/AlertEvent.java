package com.njydsz.common.sentry.domain;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Map;

import lombok.Builder;
import lombok.Data;

/**
 * 告警事件
 *
 * <p>统一的告警事件模型，支持告警收敛、去重和静默。
 *
 * @author ydsz-team
 * @since 26.09.01
 */
@Data
@Builder
public class AlertEvent {

  /** 告警名称 */
  private String name;

  /** 告警级别 */
  private AlertSeverity severity;

  /** 告警摘要 */
  private String summary;

  /** 告警详情 */
  private String description;

  /**
   * 告警分类。
   *
   * <p>26.09.20 变更：由自由字符串改为 {@link AlertCategory} 枚举，保证分类一致性， 便于 APM 后端按类别路由通知策略。
   * 为兼容历史数据，为 {@code null} 时序列化为 {@code UNKNOWN}。
   */
  private AlertCategory category;

  /** 告警标签 */
  private Map<String, String> labels;

  /** 触发时间 */
  @Builder.Default private Instant firedAt = Instant.now();

  /** 触发值 */
  private BigDecimal value;

  /** Runbook URL */
  private String runbookUrl;

  /** 生成去重 Key（用于告警收敛） */
  /**
   * dedup key。
   * @return 结果
   */
  public String dedupKey() {
    return name
        + "|"
        + (severity != null ? severity.name() : "")
        + "|"
        + (labels != null ? labels.getOrDefault("job", "") : "");
  }
}
