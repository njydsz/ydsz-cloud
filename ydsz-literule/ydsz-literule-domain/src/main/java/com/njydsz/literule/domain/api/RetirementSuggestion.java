package com.njydsz.literule.domain.api;

import java.io.Serializable;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 规则退役建议（P3-1）
 *
 * <p>基于规则执行统计和生命周期状态，自动识别应退役的规则并生成建议。
 *
 * <h3>退役原因类型</h3>
 *
 * <ul>
 *   <li>{@link Reason#DORMANT}：休眠规则 — 大量评估但零触发，规则可能已失效
 *   <li>{@link Reason#HIGH_ERROR_RATE}：高错误率 — 规则频繁异常，影响系统稳定性
 *   <li>{@link Reason#STALE_DISABLED}：长期停用 — 规则已停用超过阈值时间
 *   <li>{@link Reason#LOW_IMPACT}：低影响 — 触发率极低，投入产出比不合理
 * </ul>
 *
 * @author ydsz-team
 * @since 1.0.0
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RetirementSuggestion implements Serializable {

  private static final long serialVersionUID = 1L;

  /** 规则编码 */
  private String ruleCode;

  /** 规则名称 */
  private String ruleName;

  /** 规则类别 */
  private String category;

  /** 当前状态 */
  private String status;

  /** 退役原因 */
  private Reason reason;

  /** 退役原因描述 */
  private String reasonDesc;

  /** 总评估次数 */
  private long totalEvaluations;

  /** 总触发次数 */
  private long totalTriggered;

  /** 总异常次数 */
  private long totalErrors;

  /** 触发率（0~1.0） */
  private double triggerRate;

  /** 错误率（0~1.0） */
  private double errorRate;

  /** 建议操作 */
  @Builder.Default private List<String> recommendedActions = new ArrayList<>();

  /** 建议生成时间 */
  private LocalDateTime suggestedAt;

  /** 置信度（0~1.0，基于样本量计算） */
  private double confidence;

  /** 退役原因类型 */
  public enum Reason {
    /** 休眠规则：大量评估但零触发 */
    DORMANT("休眠规则"),
    /** 高错误率：规则频繁异常 */
    HIGH_ERROR_RATE("高错误率"),
    /** 长期停用：已停用超过阈值时间 */
    STALE_DISABLED("长期停用"),
    /** 低影响：触发率极低 */
    LOW_IMPACT("低影响");

    private final String desc;

    Reason(String desc) {
      this.desc = desc;
    }

    public String getDesc() {
      return desc;
    }
  }
}
