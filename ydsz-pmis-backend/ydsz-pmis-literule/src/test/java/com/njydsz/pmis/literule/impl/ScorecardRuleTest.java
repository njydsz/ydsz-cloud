package com.njydsz.pmis.literule.impl;

import com.njydsz.pmis.literule.api.RuleContext;
import com.njydsz.pmis.literule.api.RuleResult;
import com.njydsz.pmis.literule.api.RuleSeverity;
import com.njydsz.pmis.literule.api.ScorecardDefinition;
import com.njydsz.pmis.literule.expr.AviatorExpressionEvaluator;
import com.njydsz.pmis.literule.expr.ExpressionEvaluator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * ScorecardRule 单元测试
 *
 * <p>覆盖复杂评分卡增强能力：动态分值表达式、权重、评分方向、自定义评级映射、钳制范围。
 *
 * @author ydsz-pmis-team
 * @since 1.5.0
 */
@DisplayName("复杂评分卡规则测试")
class ScorecardRuleTest {

    private ExpressionEvaluator evaluator;

    @BeforeEach
    void setUp() {
        evaluator = new AviatorExpressionEvaluator(false);
    }

    // ============ 基础评分卡（向后兼容） ============

    @Test
    @DisplayName("固定分值评分卡 - 扣分后按阈值映射 RED")
    void fixedScoreShouldMapToRedWhenBelowRedThreshold() {
        ScorecardRule rule = ScorecardRule.builder()
                .code("CREDIT_SCORE")
                .name("客户信用评分")
                .category("RISK")
                .baseScore(100)
                .redThreshold(60)
                .yellowThreshold(80)
                .evaluator(evaluator)
                .factor(ScorecardRule.ScoreFactor.of("overdueCount > 3", -30, "逾期次数过多"))
                .factor(ScorecardRule.ScoreFactor.of("paymentRatio < 0.5", -20, "付款比率过低"))
                .build();

        // overdueCount=5 命中第一因子（-30），paymentRatio=0.3 命中第二因子（-20），总分=50 < 60 → RED
        RuleResult result = rule.evaluate(RuleContext.of(Map.of(
                "overdueCount", 5,
                "paymentRatio", 0.3)));

        assertThat(result.isTriggered()).isTrue();
        assertThat(result.getSeverity()).isEqualTo(RuleSeverity.RED);
        assertThat(result.getCurrentValue()).isEqualTo("50.0");
    }

    @Test
    @DisplayName("固定分值评分卡 - 部分命中映射 YELLOW")
    void fixedScoreShouldMapToYellowWhenBetweenThresholds() {
        ScorecardRule rule = ScorecardRule.builder()
                .code("CREDIT_SCORE")
                .name("客户信用评分")
                .category("RISK")
                .baseScore(100)
                .redThreshold(60)
                .yellowThreshold(80)
                .evaluator(evaluator)
                .factor(ScorecardRule.ScoreFactor.of("overdueCount > 3", -30, "逾期次数过多"))
                .build();

        // overdueCount=5 命中（-30），总分=70，60 ≤ 70 < 80 → YELLOW
        RuleResult result = rule.evaluate(RuleContext.of(Map.of("overdueCount", 5)));

        assertThat(result.getSeverity()).isEqualTo(RuleSeverity.YELLOW);
        assertThat(result.getCurrentValue()).isEqualTo("70.0");
    }

    @Test
    @DisplayName("无命中因子 - 基础分即最终分，映射 INFO")
    void noHitFactorsShouldReturnBaseScoreAsInfo() {
        ScorecardRule rule = ScorecardRule.builder()
                .code("CREDIT_SCORE")
                .name("客户信用评分")
                .category("RISK")
                .baseScore(100)
                .redThreshold(60)
                .yellowThreshold(80)
                .evaluator(evaluator)
                .factor(ScorecardRule.ScoreFactor.of("overdueCount > 3", -30, "逾期次数过多"))
                .build();

        RuleResult result = rule.evaluate(RuleContext.of(Map.of("overdueCount", 0)));

        assertThat(result.getSeverity()).isEqualTo(RuleSeverity.INFO);
        assertThat(result.getCurrentValue()).isEqualTo("100.0");
        assertThat(result.getDescription()).contains("无命中因子");
    }

    // ============ 动态分值表达式 ============

    @Test
    @DisplayName("动态分值表达式 - scoreExpression 计算分值")
    void dynamicScoreExpressionShouldCalculateScore() {
        ScorecardRule rule = ScorecardRule.builder()
                .code("CONTRACT_RISK")
                .name("合同风险评分")
                .category("RISK")
                .baseScore(100)
                .redThreshold(60)
                .yellowThreshold(80)
                .evaluator(evaluator)
                .factor(ScorecardRule.ScoreFactor.ofExpression(
                        "contractAmount > 1000000", "-contractAmount * 0.001", 1.0, "大额合同动态扣分"))
                .build();

        // contractAmount=2000000 命中，动态分值 = -2000000 * 0.001 = -2000（扣分），总分 = 100 - 2000 = -1900
        // 钳制到 [0, 100] → 0 → RED
        RuleResult result = rule.evaluate(RuleContext.of(Map.of("contractAmount", 2000000L)));

        assertThat(result.isTriggered()).isTrue();
        assertThat(result.getSeverity()).isEqualTo(RuleSeverity.RED);
        assertThat(result.getCurrentValue()).isEqualTo("0.0");
        assertThat(result.getDescription()).contains("大额合同动态扣分");
    }

    @Test
    @DisplayName("动态分值优先于固定分值")
    void scoreExpressionShouldTakePrecedenceOverFixedScore() {
        ScorecardRule rule = ScorecardRule.builder()
                .code("TEST")
                .name("测试")
                .category("TEST")
                .baseScore(100)
                .redThreshold(50)
                .yellowThreshold(80)
                .evaluator(evaluator)
                .factor(ScorecardRule.ScoreFactor.builder()
                        .conditionExpression("amount > 100")
                        .score(-999)  // 固定分值应被忽略
                        .scoreExpression("-amount * 0.1")
                        .weight(1.0)
                        .description("动态分值")
                        .build())
                .build();

        // amount=200 命中，动态分值 = -200 * 0.1 = -20（扣分），总分 = 100 - 20 = 80
        RuleResult result = rule.evaluate(RuleContext.of(Map.of("amount", 200)));

        assertThat(result.getCurrentValue()).isEqualTo("80.0");
    }

    // ============ 权重 ============

    @Test
    @DisplayName("权重 - 实际得分 = 分值 × 权重")
    void weightShouldScaleScore() {
        ScorecardRule rule = ScorecardRule.builder()
                .code("TEST")
                .name("测试")
                .category("TEST")
                .baseScore(100)
                .redThreshold(85)
                .yellowThreshold(95)
                .evaluator(evaluator)
                .factor(ScorecardRule.ScoreFactor.builder()
                        .conditionExpression("amount > 100")
                        .score(-20)
                        .weight(0.5)  // 实际扣分 = -20 * 0.5 = -10
                        .description("半权扣分")
                        .build())
                .build();

        // amount=200 命中，实际扣分 = -20 * 0.5 = -10，总分 = 90
        RuleResult result = rule.evaluate(RuleContext.of(Map.of("amount", 200)));

        assertThat(result.getCurrentValue()).isEqualTo("90.0");
        assertThat(result.getDescription()).contains("× 0.5");
    }

    // ============ 自定义评级映射 ============

    @Test
    @DisplayName("自定义评级映射 - 按 A/B/C/D 分级")
    void customGradesShouldMapToCustomLabel() {
        ScorecardRule rule = ScorecardRule.builder()
                .code("CREDIT")
                .name("信用评级")
                .category("RISK")
                .baseScore(100)
                .minScore(0)
                .maxScore(100)
                .evaluator(evaluator)
                .factor(ScorecardRule.ScoreFactor.of("overdueCount > 3", -30, "逾期过多"))
                .grade(ScorecardDefinition.ScoreGrade.builder().label("A").minScore(90).maxScore(200).severity("INFO").build())
                .grade(ScorecardDefinition.ScoreGrade.builder().label("B").minScore(80).maxScore(90).severity("INFO").build())
                .grade(ScorecardDefinition.ScoreGrade.builder().label("C").minScore(60).maxScore(80).severity("YELLOW").build())
                .grade(ScorecardDefinition.ScoreGrade.builder().label("D").minScore(0).maxScore(60).severity("RED").build())
                .build();

        // overdueCount=5 命中（-30），总分=70 → C 级 → YELLOW
        RuleResult result = rule.evaluate(RuleContext.of(Map.of("overdueCount", 5)));

        assertThat(result.getSeverity()).isEqualTo(RuleSeverity.YELLOW);
        assertThat(result.getTitle()).contains("[C]");
    }

    @Test
    @DisplayName("自定义评级映射 - 无命中时 A 级")
    void customGradesShouldReturnTopGradeWhenNoHit() {
        ScorecardRule rule = ScorecardRule.builder()
                .code("CREDIT")
                .name("信用评级")
                .category("RISK")
                .baseScore(100)
                .minScore(0)
                .maxScore(100)
                .evaluator(evaluator)
                .factor(ScorecardRule.ScoreFactor.of("overdueCount > 3", -30, "逾期过多"))
                .grade(ScorecardDefinition.ScoreGrade.builder().label("A").minScore(90).maxScore(200).severity("INFO").build())
                .grade(ScorecardDefinition.ScoreGrade.builder().label("D").minScore(0).maxScore(60).severity("RED").build())
                .build();

        RuleResult result = rule.evaluate(RuleContext.of(Map.of("overdueCount", 0)));

        assertThat(result.getTitle()).contains("[A]");
        assertThat(result.getSeverity()).isEqualTo(RuleSeverity.INFO);
    }

    // ============ ASCENDING 评分方向 ============

    @Test
    @DisplayName("ASCENDING 模式 - 分数越高风险越高")
    void ascendingDirectionShouldMapHigherScoreToRed() {
        ScorecardRule rule = ScorecardRule.builder()
                .code("DEBT_RATIO")
                .name("负债率评分")
                .category("RISK")
                .baseScore(0)
                .minScore(0)
                .maxScore(200)
                .scoreDirection(ScorecardDefinition.ScoreDirection.ASCENDING)
                .redThreshold(80)
                .yellowThreshold(50)
                .evaluator(evaluator)
                .factor(ScorecardRule.ScoreFactor.of("debtRatio > 0.5", 30, "负债率过高"))
                .factor(ScorecardRule.ScoreFactor.of("debtRatio > 0.8", 50, "负债率严重过高"))
                .build();

        // debtRatio=0.9 命中两个因子，总分 = 0 + 30 + 50 = 80 ≥ 80 → RED
        RuleResult result = rule.evaluate(RuleContext.of(Map.of("debtRatio", 0.9)));

        assertThat(result.getSeverity()).isEqualTo(RuleSeverity.RED);
        assertThat(result.getCurrentValue()).isEqualTo("80.0");
    }

    @Test
    @DisplayName("ASCENDING 模式 - 中等分数映射 YELLOW")
    void ascendingDirectionShouldMapMediumScoreToYellow() {
        // debtRatio=0.6 命中一个因子，总分 = 30，50 ≤ 30 < 80？不对，30 < 50 → INFO
        // 让我重新算：debtRatio=0.6 > 0.5 命中（+30），总分=30 < 50 → INFO
        // 要测 YELLOW，需要 50 ≤ 总分 < 80，所以需要 debtRatio>0.5（+30）和某个+20的因子
        // 简化：改 yellowThreshold=30
        ScorecardRule rule2 = ScorecardRule.builder()
                .code("DEBT_RATIO")
                .name("负债率评分")
                .category("RISK")
                .baseScore(0)
                .minScore(0)
                .maxScore(200)
                .scoreDirection(ScorecardDefinition.ScoreDirection.ASCENDING)
                .redThreshold(80)
                .yellowThreshold(30)
                .evaluator(evaluator)
                .factor(ScorecardRule.ScoreFactor.of("debtRatio > 0.5", 30, "负债率过高"))
                .build();

        // debtRatio=0.6 命中（+30），总分=30 ≥ 30 → YELLOW
        RuleResult result = rule2.evaluate(RuleContext.of(Map.of("debtRatio", 0.6)));

        assertThat(result.getSeverity()).isEqualTo(RuleSeverity.YELLOW);
    }

    // ============ 钳制范围 ============

    @Test
    @DisplayName("钳制范围 - 总分不低于 minScore")
    void scoreShouldBeClampedToMinScore() {
        ScorecardRule rule = ScorecardRule.builder()
                .code("TEST")
                .name("测试")
                .category("TEST")
                .baseScore(50)
                .minScore(30)
                .maxScore(100)
                .redThreshold(40)
                .yellowThreshold(45)
                .evaluator(evaluator)
                .factor(ScorecardRule.ScoreFactor.of("amount > 100", -100, "大额扣分"))
                .build();

        // amount=200 命中（-100），总分 = 50 - 100 = -50，钳制到 30
        RuleResult result = rule.evaluate(RuleContext.of(Map.of("amount", 200)));

        assertThat(result.getCurrentValue()).isEqualTo("30.0");
    }

    @Test
    @DisplayName("钳制范围 - 总分不超过 maxScore")
    void scoreShouldBeClampedToMaxScore() {
        ScorecardRule rule = ScorecardRule.builder()
                .code("TEST")
                .name("测试")
                .category("TEST")
                .baseScore(80)
                .minScore(0)
                .maxScore(100)
                .redThreshold(50)
                .yellowThreshold(70)
                .evaluator(evaluator)
                .factor(ScorecardRule.ScoreFactor.of("amount > 100", 50, "加分因子"))
                .build();

        // amount=200 命中（+50），总分 = 80 + 50 = 130，钳制到 100
        RuleResult result = rule.evaluate(RuleContext.of(Map.of("amount", 200)));

        assertThat(result.getCurrentValue()).isEqualTo("100.0");
    }

    // ============ from(ScorecardDefinition) ============

    @Test
    @DisplayName("from(Definition) - 应完整转换复杂评分卡定义")
    void fromDefinitionShouldPreserveAllFields() {
        ScorecardDefinition def = ScorecardDefinition.builder()
                .ruleCode("CREDIT_SCORE")
                .ruleName("客户信用评分")
                .category("RISK")
                .baseScore(100)
                .scoreDirection(ScorecardDefinition.ScoreDirection.DESCENDING)
                .minScore(0)
                .maxScore(100)
                .redThreshold(60)
                .yellowThreshold(80)
                .factors(List.of(
                        ScorecardDefinition.ScoreFactor.builder()
                                .conditionExpression("overdueCount > 3")
                                .score(-30)
                                .weight(1.0)
                                .description("逾期过多")
                                .build(),
                        ScorecardDefinition.ScoreFactor.builder()
                                .conditionExpression("contractAmount > 1000000")
                                .scoreExpression("-contractAmount * 0.001")
                                .weight(0.5)
                                .description("大额动态扣分")
                                .build()))
                .grades(List.of(
                        ScorecardDefinition.ScoreGrade.builder().label("A").minScore(90).maxScore(200).severity("INFO").build(),
                        ScorecardDefinition.ScoreGrade.builder().label("D").minScore(0).maxScore(60).severity("RED").build()))
                .build();

        ScorecardRule rule = ScorecardRule.from(def, evaluator);

        // overdueCount=5, contractAmount=2000000
        // 因子1命中：-30 × 1.0 = -30
        // 因子2命中：-2000000 * 0.001 = -2000 × 0.5 = -1000（扣分），总分 = 100 - 30 - 1000 = -930
        // 钳制到 0 → D 级 → RED
        RuleResult result = rule.evaluate(RuleContext.of(Map.of(
                "overdueCount", 5,
                "contractAmount", 2000000L)));

        assertThat(result.isTriggered()).isTrue();
        assertThat(result.getSeverity()).isEqualTo(RuleSeverity.RED);
        assertThat(result.getTitle()).contains("[D]");
        assertThat(result.getDescription()).contains("逾期过多");
        assertThat(result.getDescription()).contains("大额动态扣分");
    }

    @Test
    @DisplayName("from(Definition) - scoreDirection 为 null 时默认 DESCENDING")
    void fromDefinitionShouldDefaultToDescendingWhenNull() {
        ScorecardDefinition def = ScorecardDefinition.builder()
                .ruleCode("TEST")
                .ruleName("测试")
                .category("TEST")
                .baseScore(100)
                .redThreshold(60)
                .yellowThreshold(80)
                .scoreDirection(null)  // 显式 null
                .factors(List.of())
                .build();

        ScorecardRule rule = ScorecardRule.from(def, evaluator);

        // 总分=100（无因子），100 ≥ 80 → INFO
        RuleResult result = rule.evaluate(RuleContext.of(Map.of()));

        assertThat(result.getSeverity()).isEqualTo(RuleSeverity.INFO);
    }
}
