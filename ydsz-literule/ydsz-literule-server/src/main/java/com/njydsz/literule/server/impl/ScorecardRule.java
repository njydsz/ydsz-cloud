package com.njydsz.literule.server.impl;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import lombok.Builder;
import lombok.Data;
import lombok.Singular;
import lombok.extern.slf4j.Slf4j;

import com.njydsz.literule.domain.Rule;
import com.njydsz.literule.domain.dto.ScorecardDefinitionDTO;
import com.njydsz.literule.domain.enums.RuleSeverity;
import com.njydsz.literule.domain.expression.ExpressionEngine;
import com.njydsz.literule.domain.vo.RuleContextVO;
import com.njydsz.literule.domain.vo.RuleResultVO;

/**
 * 评分卡规则：基于多维度评分因子加权计算总分，按阈值区间或自定义评级映射决定严重度
 *
 * <p>典型应用场景：质量评级、健康度评级、风险评级等多维评分。
 *
 * <p><b>复杂评分卡增强（1.5.0）</b>：
 *
 * <ul>
 *   <li>动态分值表达式（scoreExpression）：分值可通过 LiteExpr 表达式动态计算
 *   <li>权重（weight）：实际得分 = 分值 × 权重，默认 1.0
 *   <li>评分方向（scoreDirection）：DESCENDING 分数越低风险越高 / ASCENDING 分数越高风险越高
 *   <li>自定义评级映射（grades）：按分数区间映射 A/B/C/D 等自定义评级
 *   <li>自定义钳制范围（minScore/maxScore）
 *   <li>详细评分明细输出（初始分、各项加减分、最终分、评级）
 * </ul>
 *
 * <p>使用示例（复杂评分卡）：
 *
 * <pre>
 * ScorecardRule rule = ScorecardRule.builder()
 *     .code("SCORECARD_DEMO")
 *     .name("评分卡示例")
 *     .category("DEMO")
 *     .baseScore(100)
 *     .scoreDirection(ScorecardDefinitionDTO.ScoreDirection.DESCENDING)
 *     .minScore(0).maxScore(100)
 *     .factor(ScoreFactor.of("metricA > 3", -30, "示例因子A 命中扣分"))
 *     .factor(ScoreFactor.ofExpression("metricB > 1000000", "metricB * 0.001", 0.5, "示例因子B 动态扣分"))
 *     .redThreshold(60)
 *     .yellowThreshold(80)
 *     .build();
 * </pre>
 *
 * @since 26.09.01
 * @author ydsz-team
 */
@Slf4j
@Builder
public class ScorecardRule implements Rule {

    /** 纳秒到毫秒的换算系数 */
  private static final long NANOS_PER_MILLI = 1_000_000L;

  private final String code;
  private final String name;
  private final String category;
  private final int priority;
  private final String scope;
  @Singular private final List<ScoreFactor> factors;

  /** 基础分（命中因子前的基础值，默认 100） */
  @Builder.Default private final double baseScore = 100;

  private final double redThreshold;
  private final double yellowThreshold;

  /** 评分方向（默认 DESCENDING：分数越低风险越高） */
  @Builder.Default
  private final ScorecardDefinitionDTO.ScoreDirection scoreDirection =
      ScorecardDefinitionDTO.ScoreDirection.DESCENDING;

  /** 最低分（钳制下界，默认 0） */
  @Builder.Default private final double minScore = 0;

  /** 最高分（钳制上界，默认 100） */
  @Builder.Default private final double maxScore = 100;

  /** 自定义评级映射（可选） */
  @Singular private final List<ScorecardDefinitionDTO.ScoreGrade> grades;

  private final ExpressionEngine evaluator;

  @Override
  public String getCode() {
    return code;
  }

  @Override
  public String getName() {
    return name;
  }

  @Override
  public String getCategory() {
    return category;
  }

  @Override
  public int getPriority() {
    return priority > 0 ? priority : DEFAULT_PRIORITY;
  }

  @Override
  public String getScope() {
    return scope;
  }

  /**
   * 从 ScorecardDefinitionDTO 构造评分卡规则
   *
   * @param def 评分卡定义
   * @param evaluator 表达式求值器
   * @return ScorecardRule 实例
   * @since 26.09.01
   */
  public static ScorecardRule from(ScorecardDefinitionDTO def, ExpressionEngine evaluator) {
    ScorecardRuleBuilder b =
        ScorecardRule.builder()
            .code(def.getRuleCode())
            .name(def.getRuleName())
            .category(def.getCategory())
            .priority(def.getPriority())
            .scope(def.getScope())
            .baseScore(def.getBaseScore())
            .redThreshold(def.getRedThreshold())
            .yellowThreshold(def.getYellowThreshold())
            .scoreDirection(
                def.getScoreDirection() != null
                    ? def.getScoreDirection()
                    : ScorecardDefinitionDTO.ScoreDirection.DESCENDING)
            .minScore(def.getMinScore())
            .maxScore(def.getMaxScore())
            .evaluator(evaluator);
    if (def.getFactors() != null) {
      for (ScorecardDefinitionDTO.ScoreFactor f : def.getFactors()) {
        b.factor(
            ScoreFactor.builder()
                .conditionExpression(f.getConditionExpression())
                .score(f.getScore())
                .scoreExpression(f.getScoreExpression())
                .weight(f.getWeight())
                .description(f.getDescription())
                .build());
      }
    }
    if (def.getGrades() != null) {
      for (ScorecardDefinitionDTO.ScoreGrade g : def.getGrades()) {
        b.grade(g);
      }
    }
    return b.build();
  }

  @Override
  public RuleResultVO evaluate(RuleContextVO context) {
    long start = System.nanoTime();
    try {
      double totalScore = baseScore;
      List<String> hitDetails = new ArrayList<>(16);

      for (ScoreFactor factor : factors) {
        try {
          boolean hit = evaluator.evalBoolean(factor.getConditionExpression(), context);
          if (hit) {
            double rawScore = resolveScore(factor, context);
            double actualScore = rawScore * factor.getWeight();
            totalScore += actualScore;
            String weightSuffix = factor.getWeight() != 1.0 ? " × " + factor.getWeight() : "";
            hitDetails.add(
                String.format(
                    "%s (%.2f%s=%.2f)",
                    factor.getDescription(), rawScore, weightSuffix, actualScore));
          }
        } catch (Exception e) {
          log.warn("[LiteRule-Scorecard] 因子 {} 求值异常: {}", factor.getDescription(), e.getMessage());
        }
      }

      // 钳制到 [minScore, maxScore]
      totalScore = Math.max(minScore, Math.min(maxScore, totalScore));

      // 映射严重度与评级
      RuleSeverity severity;
      String gradeLabel = null;
      if (grades != null && !grades.isEmpty()) {
        // 自定义评级映射优先
        ScorecardDefinitionDTO.ScoreGrade matched = resolveGrade(totalScore);
        if (matched != null) {
          gradeLabel = matched.getLabel();
          severity = parseSeverity(matched.getSeverity(), RuleSeverity.INFO);
        } else {
          severity = RuleSeverity.INFO;
        }
      } else {
        // 阈值映射（按评分方向）
        severity = resolveSeverityByThreshold(totalScore);
      }

      // 构建标题与描述
      String gradeSuffix = gradeLabel != null ? " [" + gradeLabel + "]" : "";
      String title = name + ": " + String.format("%.1f", totalScore) + "分" + gradeSuffix;
      StringBuilder desc = new StringBuilder();
      desc.append(String.format("基础分=%.1f, ", baseScore));
      if (hitDetails.isEmpty()) {
        desc.append("无命中因子, ");
      } else {
        desc.append("命中: ").append(String.join("; ", hitDetails)).append(", ");
      }
      desc.append(String.format("最终=%.1f", totalScore));

      return RuleResultVO.builder()
          .ruleCode(code)
          .ruleName(name)
          .category(category)
          .triggered(true)
          .severity(severity.getCode())
          .title(title)
          .description(desc.toString())
          .currentValue(String.valueOf(totalScore))
          .triggeredAt(LocalDateTime.now())
          .elapsedMs((System.nanoTime() - start) / NANOS_PER_MILLI)
          .build();
    } catch (Exception e) {
      log.warn("[LiteRule-Scorecard] 评分卡 {} 评估异常: {}", code, e.getMessage());
      return RuleResultVO.builder()
          .ruleCode(code)
          .triggered(false)
          .triggeredAt(LocalDateTime.now())
          .elapsedMs((System.nanoTime() - start) / NANOS_PER_MILLI)
          .build();
    }
  }

  /** 解析因子分值：优先使用 scoreExpression 动态计算，否则使用固定 score */
  private double resolveScore(ScoreFactor factor, RuleContextVO context) {
    if (factor.getScoreExpression() != null && !factor.getScoreExpression().isBlank()) {
      Object result = evaluator.eval(factor.getScoreExpression(), context);
      if (result instanceof Number n) {
        return n.doubleValue();
      }
      throw new IllegalStateException("scoreExpression 未返回 Number: " + factor.getScoreExpression());
    }
    return factor.getScore();
  }

  /** 按自定义评级映射查找命中区间 */
  private ScorecardDefinitionDTO.ScoreGrade resolveGrade(double totalScore) {
    for (ScorecardDefinitionDTO.ScoreGrade g : grades) {
      if (totalScore >= g.getMinScore() && totalScore < g.getMaxScore()) {
        return g;
      }
    }
    return null;
  }

  /**
   * 按阈值映射严重度（无自定义评级时使用）
   *
   * <p>DESCENDING 模式：分数越低风险越高（redThreshold &lt; yellowThreshold）
   *
   * <p>ASCENDING 模式：分数越高风险越高（redThreshold &gt; yellowThreshold）
   */
  private RuleSeverity resolveSeverityByThreshold(double totalScore) {
    if (scoreDirection == ScorecardDefinitionDTO.ScoreDirection.ASCENDING) {
      if (totalScore >= redThreshold) {
        return RuleSeverity.RED;
      }
      if (totalScore >= yellowThreshold) {
        return RuleSeverity.YELLOW;
      }
      return RuleSeverity.INFO;
    }
    // DESCENDING（默认）
    if (totalScore < redThreshold) {
      return RuleSeverity.RED;
    }
    if (totalScore < yellowThreshold) {
      return RuleSeverity.YELLOW;
    }
    return RuleSeverity.INFO;
  }

  /** 安全解析严重度编码 */
  private RuleSeverity parseSeverity(String code, RuleSeverity fallback) {
    if (code == null || code.isBlank()) {
      return fallback;
    }
    try {
      return RuleSeverity.valueOf(code.toUpperCase());
    } catch (IllegalArgumentException e) {
      return fallback;
    }
  }

  /** 评分因子 */
  @Data
  @Builder
  public static class ScoreFactor {
    /** 条件表达式（LiteExpr，返回 boolean） */
    private String conditionExpression;

    /** 命中时的固定得分（正分加分，负分扣分） */
    @Builder.Default private double score = 0;

    /** 动态分值表达式（LiteExpr，返回 Number；与 score 二选一，优先使用 scoreExpression） */
    private String scoreExpression;

    /** 权重（实际得分 = 分值 × 权重，默认 1.0） */
    @Builder.Default private double weight = 1.0;

    /** 因子描述 */
    private String description;

    /**
     * 创建静态分值因子。
     *
     * @param conditionExpression 触发条件表达式（满足时计分）
     * @param score 满足条件时的固定分值
     * @param description 因子描述（用于解释/审计）
     * @return ScoreFactor 实例
     */
    public static ScoreFactor of(String conditionExpression, double score, String description) {
      return ScoreFactor.builder()
          .conditionExpression(conditionExpression)
          .score(score)
          .description(description)
          .build();
    }

    /**
     * 创建动态分值因子
     *
     * @param conditionExpression 条件表达式
     * @param scoreExpression 动态分值表达式（返回 Number）
     * @param weight 权重
     * @param description 因子描述
     * @return ScoreFactor 实例
     */
    public static ScoreFactor ofExpression(
        String conditionExpression, String scoreExpression, double weight, String description) {
      return ScoreFactor.builder()
          .conditionExpression(conditionExpression)
          .scoreExpression(scoreExpression)
          .weight(weight)
          .description(description)
          .build();
    }
  }
}
