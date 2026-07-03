package com.njydsz.pmis.literule.api;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.util.List;

/**
 * 评分卡规则定义（DTO）
 *
 * <p>由若干评分因子组成，每个因子包含 Aviator 条件表达式与命中得分。
 * 总分 = baseScore + Σ(命中因子 score)，按阈值区间映射严重度：
 * <ul>
 *   <li>totalScore &lt; redThreshold → RED</li>
 *   <li>totalScore &lt; yellowThreshold → YELLOW</li>
 *   <li>其他 → INFO</li>
 * </ul>
 *
 * <p>持久化于 {@code pmis_rule_scorecard}（见 V048），由 {@code ScorecardConfigProvider} SPI 加载，
 * 通过 {@link com.njydsz.pmis.literule.impl.ScorecardRule#from(ScorecardDefinition, com.njydsz.pmis.literule.expr.ExpressionEvaluator)}
 * 转换为可执行规则。
 *
 * <p>JSON 示例：
 * <pre>
 * {
 *   "ruleCode": "CREDIT_SCORE",
 *   "ruleName": "客户信用评分",
 *   "category": "RISK",
 *   "baseScore": 100,
 *   "redThreshold": 60,
 *   "yellowThreshold": 80,
 *   "factors": [
 *     {"conditionExpression": "overdueCount > 3", "score": -30, "description": "逾期次数过多"},
 *     {"conditionExpression": "paymentRatio < 0.5", "score": -20, "description": "付款比率过低"}
 *   ]
 * }
 * </pre>
 *
 * @author ydsz-pmis-team
 * @since 1.4.0
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ScorecardDefinition implements Serializable {

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
    @Builder.Default
    private double baseScore = 100;

    /** 红色阈值（总分低于此值为 RED） */
    private double redThreshold;

    /** 黄色阈值（总分低于此值为 YELLOW） */
    private double yellowThreshold;

    /** 评分因子列表 */
    private List<ScoreFactor> factors;

    /** 是否启用 */
    @Builder.Default
    private boolean enabled = true;

    /** 优先级（数值越小越先执行） */
    @Builder.Default
    private int priority = Rule.DEFAULT_PRIORITY;

    /** 影响范围（用于场景过滤） */
    private String scope;

    /** 当前版本号 */
    @Builder.Default
    private int version = 1;

    /**
     * 评分因子
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ScoreFactor implements Serializable {
        private static final long serialVersionUID = 1L;
        /** 条件表达式（Aviator，返回 boolean） */
        private String conditionExpression;
        /** 命中时的得分（正分加分，负分扣分） */
        private double score;
        /** 因子描述（用于结果展示） */
        private String description;
    }
}
