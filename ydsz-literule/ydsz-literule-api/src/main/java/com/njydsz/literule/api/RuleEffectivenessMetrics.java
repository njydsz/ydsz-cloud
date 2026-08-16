package com.njydsz.literule.api;

import java.io.Serializable;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 规则效果评估指标（P2-2）
 *
 * <p>基于人工反馈或标注数据，计算规则触发的准确度指标：
 *
 * <ul>
 *   <li><b>Precision（精确率）</b>：TP / (TP + FP)，规则触发时有多少是真正应该触发的
 *   <li><b>Recall（召回率）</b>：TP / (TP + FN)，应该触发的场景中规则实际触发了多少
 *   <li><b>F1-Score</b>：2 * P * R / (P + R)，精确率和召回率的调和平均
 *   <li><b>Specificity（特异度）</b>：TN / (TN + FP)，不应触发的场景中规则正确未触发多少
 *   <li><b>Accuracy（准确率）</b>：(TP + TN) / (TP + FP + FN + TN)，总体判断正确率
 *   <li><b>False Positive Rate（误报率）</b>：FP / (FP + TN)，不应触发但规则触发了的比例
 *   <li><b>False Negative Rate（漏报率）</b>：FN / (FN + TP)，应该触发但规则未触发的比例
 * </ul>
 *
 * <h3>混淆矩阵</h3>
 *
 * <pre>
 *                    ┌──────────────────┬──────────────────┐
 *                    │  实际应该触发      │  实际不应触发     │
 * ┌──────────────────┼──────────────────┼──────────────────┤
 * │  规则触发         │  TP (真正例)      │  FP (假正例)      │
 * ├──────────────────┼──────────────────┼──────────────────┤
 * │  规则未触发       │  FN (假负例)      │  TN (真负例)      │
 * └──────────────────┴──────────────────┴──────────────────┘
 * </pre>
 *
 * <h3>使用场景</h3>
 *
 * <ul>
 *   <li>风控规则上线后，人工抽检规则触发结果，标注 TP/FP，评估精确率
 *   <li>对历史已知风险事件回放，评估规则召回率
 *   <li>灰度发布期间，对比新旧版本的 F1-Score 判断是否应全量切换
 *   <li>规则调参后，通过 before/after 指标对比验证优化效果
 * </ul>
 *
 * @author ydsz-team
 * @since 1.0.0
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RuleEffectivenessMetrics implements Serializable {

  private static final long serialVersionUID = 1L;

  /** 规则编码（全局指标时为 null） */
  private String ruleCode;

  /** 真正例数（规则触发且实际应该触发） */
  private long truePositives;

  /** 假正例数（规则触发但实际不应触发，即误报） */
  private long falsePositives;

  /** 假负例数（规则未触发但实际应该触发，即漏报） */
  private long falseNegatives;

  /** 真负例数（规则未触发且实际不应触发） */
  private long trueNegatives;

  /** 总反馈样本数 */
  private long totalSamples;

  // ==================== 派生指标 ====================

  /**
   * 精确率（Precision）：TP / (TP + FP)
   *
   * <p>规则触发时的正确率。1.0 表示每次触发都是对的。 分母为 0 时返回 0.0。
   *
   * @return 0.0 ~ 1.0
   */
  public double getPrecision() {
    long denom = truePositives + falsePositives;
    return denom > 0 ? (double) truePositives / denom : 0.0;
  }

  /**
   * 召回率（Recall / Sensitivity）：TP / (TP + FN)
   *
   * <p>应该触发的场景中规则实际触发的比例。1.0 表示没有漏报。 分母为 0 时返回 0.0。
   *
   * @return 0.0 ~ 1.0
   */
  public double getRecall() {
    long denom = truePositives + falseNegatives;
    return denom > 0 ? (double) truePositives / denom : 0.0;
  }

  /**
   * F1-Score：2 * Precision * Recall / (Precision + Recall)
   *
   * <p>精确率和召回率的调和平均数，综合衡量规则效果。 F1 = 1.0 为最佳，F1 = 0.0 为最差。
   *
   * @return 0.0 ~ 1.0
   */
  public double getF1Score() {
    double p = getPrecision();
    double r = getRecall();
    return (p + r) > 0 ? 2.0 * p * r / (p + r) : 0.0;
  }

  /**
   * 特异度（Specificity / True Negative Rate）：TN / (TN + FP)
   *
   * <p>不应触发的场景中规则正确未触发的比例。
   *
   * @return 0.0 ~ 1.0
   */
  public double getSpecificity() {
    long denom = trueNegatives + falsePositives;
    return denom > 0 ? (double) trueNegatives / denom : 0.0;
  }

  /**
   * 准确率（Accuracy）：(TP + TN) / (TP + FP + FN + TN)
   *
   * <p>总体判断正确率。
   *
   * @return 0.0 ~ 1.0
   */
  public double getAccuracy() {
    return totalSamples > 0 ? (double) (truePositives + trueNegatives) / totalSamples : 0.0;
  }

  /**
   * 误报率（False Positive Rate）：FP / (FP + TN)
   *
   * <p>不应触发的场景中规则误触发的比例。1 - Specificity。
   *
   * @return 0.0 ~ 1.0
   */
  public double getFalsePositiveRate() {
    return 1.0 - getSpecificity();
  }

  /**
   * 漏报率（False Negative Rate）：FN / (FN + TP)
   *
   * <p>应该触发的场景中规则未触发的比例。1 - Recall。
   *
   * @return 0.0 ~ 1.0
   */
  public double getFalseNegativeRate() {
    return 1.0 - getRecall();
  }

  /**
   * 效果等级
   *
   * <p>基于 F1-Score 评级：
   *
   * <ul>
   *   <li>EXCELLENT：F1 ≥ 0.90
   *   <li>GOOD：F1 ≥ 0.75
   *   <li>FAIR：F1 ≥ 0.60
   *   <li>POOR：F1 < 0.60
   *   <li>INSUFFICIENT_DATA：总样本数不足（< 30）
   * </ul>
   *
   * @return 效果等级
   */
  public EffectivenessLevel getLevel() {
    if (totalSamples < 30) {
      return EffectivenessLevel.INSUFFICIENT_DATA;
    }
    double f1 = getF1Score();
    if (f1 >= 0.90) return EffectivenessLevel.EXCELLENT;
    if (f1 >= 0.75) return EffectivenessLevel.GOOD;
    if (f1 >= 0.60) return EffectivenessLevel.FAIR;
    return EffectivenessLevel.POOR;
  }

  /**
   * 创建空指标
   *
   * @param ruleCode 规则编码（全局时传 null）
   * @return 空指标
   */
  public static RuleEffectivenessMetrics empty(String ruleCode) {
    return RuleEffectivenessMetrics.builder()
        .ruleCode(ruleCode)
        .truePositives(0)
        .falsePositives(0)
        .falseNegatives(0)
        .trueNegatives(0)
        .totalSamples(0)
        .build();
  }

  /** 效果等级 */
  public enum EffectivenessLevel {
    /** 优秀（F1 ≥ 0.90） */
    EXCELLENT,
    /** 良好（F1 ≥ 0.75） */
    GOOD,
    /** 一般（F1 ≥ 0.60） */
    FAIR,
    /** 较差（F1 < 0.60） */
    POOR,
    /** 样本不足（< 30） */
    INSUFFICIENT_DATA;

    /**
     * 中文描述
     *
     * @return 中文描述
     */
    public String getDescription() {
      return switch (this) {
        case EXCELLENT -> "优秀";
        case GOOD -> "良好";
        case FAIR -> "一般";
        case POOR -> "较差";
        case INSUFFICIENT_DATA -> "样本不足";
      };
    }
  }
}
