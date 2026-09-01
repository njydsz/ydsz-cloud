package com.njydsz.literule.domain.dto;

import java.io.Serializable;
import java.util.List;

import com.njydsz.literule.domain.Rule;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 评分卡规则定义（DTO）
 *
 * <p>由若干评分因子组成，每个因子包含 LiteExpr 条件表达式与命中得分。 总分 = baseScore + Σ(命中因子 score ×
 * weight)，按阈值区间或自定义评级映射决定严重度。
 *
 * <p><b>评分方向（scoreDirection）</b>：
 *
 * <ul>
 *   <li>{@code DESCENDING}（默认）：分数越低风险越高，redThreshold &lt; yellowThreshold
 *   <li>{@code ASCENDING}：分数越高风险越高（如负债率评分），redThreshold &gt; yellowThreshold
 * </ul>
 *
 * <p><b>动态分值（scoreExpression）</b>：因子可指定 LiteExpr 表达式动态计算分值 （如 {@code contractAmount * 0.01}），与固定
 * {@code score} 二选一，优先使用 scoreExpression。
 *
 * <p><b>权重（weight）</b>：命中因子的实际得分 = 分值 × 权重，默认 1.0。
 *
 * <p><b>评级映射（grades）</b>：可选，按分数区间映射自定义评级（如 A/B/C/D）， 若配置则覆盖 redThreshold/yellowThreshold 的三级映射。
 *
 * <p>持久化于 {@code ydsz_rule_scorecard}（见 V048），由 {@code ScorecardConfigProvider} SPI 加载， 通过 {@link
 * com.njydsz.literule.server.impl.ScorecardRule#from(ScorecardDefinitionDTO,
 * com.njydsz.literule.domain.expression.ExpressionEngine)} 转换为可执行规则。
 *
 * <p>JSON 示例（复杂评分卡）：
 *
 * <pre>
 * {
 *   "ruleCode": "SCORECARD_DEMO",
 *   "ruleName": "评分卡示例",
 *   "category": "DEMO",
 *   "baseScore": 100,
 *   "scoreDirection": "DESCENDING",
 *   "minScore": 0,
 *   "maxScore": 100,
 *   "factors": [
 *     {"conditionExpression": "metricA > 3", "score": -30, "weight": 1.0, "description": "示例因子A 命中扣分"},
 *     {"conditionExpression": "metricB > 1000000",
 *      "scoreExpression": "metricB * 0.001", "weight": 0.5,
 *      "description": "示例因子B 动态扣分"}
 *   ],
 *   "grades": [
 *     {"label": "A", "minScore": 90, "maxScore": 200, "severity": "INFO"},
 *     {"label": "B", "minScore": 80, "maxScore": 90, "severity": "INFO"},
 *     {"label": "C", "minScore": 60, "maxScore": 80, "severity": "YELLOW"},
 *     {"label": "D", "minScore": 0, "maxScore": 60, "severity": "RED"}
 *   ]
 * }
 * </pre>
 *
 * @author ydsz-team
 * @since 1.0.0
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ScorecardDefinitionDTO implements Serializable {

  private static final long serialVersionUID = 1L;

  /** 规则编码（唯一） */
  private String ruleCode;

  /** 规则名称 */
  private String ruleName;

  /** 类别（如 RISK / COST / EVM） */
  private String category;

  /** 描述 */
  private String description;

  /** 基础分（命中因子前的基础值，默认 100） */
  @Builder.Default private double baseScore = 100;

  /** 红色阈值（DESCENDING 模式下总分低于此值为 RED；ASCENDING 模式下总分高于此值为 RED） */
  private double redThreshold;

  /** 黄色阈值（DESCENDING 模式下总分低于此值为 YELLOW；ASCENDING 模式下总分高于此值为 YELLOW） */
  private double yellowThreshold;

  /** 评分方向（默认 DESCENDING：分数越低风险越高） */
  @Builder.Default private ScoreDirection scoreDirection = ScoreDirection.DESCENDING;

  /** 最低分（钳制下界，默认 0） */
  @Builder.Default private double minScore = 0;

  /** 最高分（钳制上界，默认 100） */
  @Builder.Default private double maxScore = 100;

  /** 评分因子列表 */
  private List<ScoreFactor> factors;

  /** 自定义评级映射（可选；配置后覆盖 redThreshold/yellowThreshold 的三级映射） */
  private List<ScoreGrade> grades;

  /** 是否启用 */
  @Builder.Default private boolean enabled = true;

  /** 优先级（数值越小越先执行） */
  @Builder.Default private int priority = Rule.DEFAULT_PRIORITY;

  /** 影响范围（用于场景过滤） */
  private String scope;

  /** 当前版本号 */
  @Builder.Default private int version = 1;

  /** 评分因子 */
  @Data
  @Builder
  @NoArgsConstructor
  @AllArgsConstructor
  public static class ScoreFactor implements Serializable {
    private static final long serialVersionUID = 1L;

    /** 条件表达式（LiteExpr，返回 boolean） */
    private String conditionExpression;

    /** 命中时的固定得分（正分加分，负分扣分） */
    @Builder.Default private double score = 0;

    /** 动态分值表达式（LiteExpr，返回 Number；与 score 二选一，优先使用 scoreExpression） */
    private String scoreExpression;

    /** 权重（实际得分 = 分值 × 权重，默认 1.0） */
    @Builder.Default private double weight = 1.0;

    /** 因子描述（用于结果展示） */
    private String description;
  }

  /**
   * 评分评级映射
   *
   * <p>按分数区间 [minScore, maxScore) 映射到评级文本与严重度。
   */
  @Data
  @Builder
  @NoArgsConstructor
  @AllArgsConstructor
  public static class ScoreGrade implements Serializable {
    private static final long serialVersionUID = 1L;

    /** 评级名称（如 "A"、"优"、"高风险"） */
    private String label;

    /** 区间下界（含） */
    private double minScore;

    /** 区间上界（不含；最高评级可设为 Double.MAX_VALUE） */
    private double maxScore;

    /** 对应的严重度编码（RED/YELLOW/INFO，可选） */
    private String severity;
  }

  /**
   * 评分方向
   *
   * <ul>
   *   <li>{@code DESCENDING}：分数越低风险越高（默认，如评分卡：100 分起评，扣分制）
   *   <li>{@code ASCENDING}：分数越高风险越高（如负债率评分：0 分起评，加分制）
   * </ul>
   */
  public enum ScoreDirection {
    /** 分数越低风险越高 */
    DESCENDING,
    /** 分数越高风险越高 */
    ASCENDING
  }
}
