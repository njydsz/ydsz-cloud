package com.njydsz.pmis.literule.impl;

import com.njydsz.pmis.literule.api.Rule;
import com.njydsz.pmis.literule.api.RuleContext;
import com.njydsz.pmis.literule.api.RuleResult;
import com.njydsz.pmis.literule.api.RuleSeverity;
import com.njydsz.pmis.literule.expr.ExpressionEvaluator;
import lombok.Builder;
import lombok.Data;
import lombok.Singular;
import lombok.extern.slf4j.Slf4j;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * 评分卡规则：基于多维度评分因子加权计算总分，按阈值区间决定严重度
 *
 * <p>典型应用场景：客户信用评级、供应商评级、项目风险评级。
 *
 * <p>每个评分因子包含：
 * <ul>
 *   <li>条件表达式（Aviator，返回 boolean）</li>
 *   <li>命中时的得分（正分或负分）</li>
 *   <li>因子描述（用于结果展示）</li>
 * </ul>
 *
 * <p>总分 = 所有命中因子的得分之和。按阈值区间映射严重度：
 * <ul>
 *   <li>score >= redThreshold → RED</li>
 *   <li>score >= yellowThreshold → YELLOW</li>
 *   <li>其他 → INFO</li>
 * </ul>
 *
 * <p>使用示例：
 * <pre>
 * ScorecardRule rule = ScorecardRule.builder()
 *     .code("CREDIT_SCORE")
 *     .name("客户信用评分")
 *     .category("RISK")
 *     .factor(ScoreFactor.of("overdueCount > 3", -30, "逾期次数过多"))
 *     .factor(ScoreFactor.of("paymentRatio < 0.5", -20, "付款比率过低"))
 *     .factor(ScoreFactor.of("contractAmount > 1000000", 10, "大额合同加分"))
 *     .redThreshold(60)   // 60分以上为高风险
 *     .yellowThreshold(80) // 80分以上为中等风险
 *     .build();
 * </pre>
 *
 * @author ydsz-pmis-team
 * @since 1.3.0
 */
@Slf4j
@Builder
public class ScorecardRule implements Rule {

    private final String code;
    private final String name;
    private final String category;
    private final int priority;
    private final String scope;
    @Singular
    private final List<ScoreFactor> factors;
    private final double redThreshold;
    private final double yellowThreshold;
    private final ExpressionEvaluator evaluator;

    @Override
    public String getCode() { return code; }

    @Override
    public String getName() { return name; }

    @Override
    public String getCategory() { return category; }

    @Override
    public int getPriority() { return priority > 0 ? priority : DEFAULT_PRIORITY; }

    @Override
    public String getScope() { return scope; }

    @Override
    public RuleResult evaluate(RuleContext context) {
        long start = System.nanoTime();
        try {
            double totalScore = 100; // 基础分
            List<String> hitFactors = new ArrayList<>();

            for (ScoreFactor factor : factors) {
                try {
                    boolean hit = evaluator.evalBoolean(factor.getConditionExpression(), context);
                    if (hit) {
                        totalScore += factor.getScore();
                        hitFactors.add(factor.getDescription() + " (" + factor.getScore() + ")");
                    }
                } catch (Exception e) {
                    log.warn("[LiteRule-Scorecard] 因子 {} 求值异常: {}", factor.getDescription(), e.getMessage());
                }
            }

            // 钳制到 0-100
            totalScore = Math.max(0, Math.min(100, totalScore));

            // 按阈值映射严重度（分数越低风险越高）
            RuleSeverity severity;
            if (totalScore < redThreshold) {
                severity = RuleSeverity.RED;
            } else if (totalScore < yellowThreshold) {
                severity = RuleSeverity.YELLOW;
            } else {
                severity = RuleSeverity.INFO;
            }

            String title = name + ": " + String.format("%.1f", totalScore) + "分";
            String description = "命中因子: " + String.join("; ", hitFactors);

            return RuleResult.builder()
                    .ruleCode(code)
                    .ruleName(name)
                    .category(category)
                    .triggered(true)
                    .severity(severity)
                    .title(title)
                    .description(description)
                    .currentValue(String.valueOf(totalScore))
                    .triggeredAt(LocalDateTime.now())
                    .elapsedMs((System.nanoTime() - start) / 1_000_000)
                    .build();
        } catch (Exception e) {
            log.warn("[LiteRule-Scorecard] 评分卡 {} 评估异常: {}", code, e.getMessage());
            return RuleResult.builder()
                    .ruleCode(code)
                    .triggered(false)
                    .triggeredAt(LocalDateTime.now())
                    .elapsedMs((System.nanoTime() - start) / 1_000_000)
                    .build();
        }
    }

    /**
     * 评分因子
     */
    @Data
    @Builder
    public static class ScoreFactor {
        /** 条件表达式（Aviator，返回 boolean） */
        private String conditionExpression;
        /** 命中时的得分（正分加分，负分扣分） */
        private double score;
        /** 因子描述 */
        private String description;

        public static ScoreFactor of(String conditionExpression, double score, String description) {
            return ScoreFactor.builder()
                    .conditionExpression(conditionExpression)
                    .score(score)
                    .description(description)
                    .build();
        }
    }
}
