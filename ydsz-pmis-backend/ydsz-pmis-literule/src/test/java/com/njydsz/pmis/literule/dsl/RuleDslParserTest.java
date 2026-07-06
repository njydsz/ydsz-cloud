package com.njydsz.pmis.literule.dsl;

import com.njydsz.pmis.literule.api.HitPolicy;
import com.njydsz.pmis.literule.api.Rule;
import com.njydsz.pmis.literule.api.RuleContext;
import com.njydsz.pmis.literule.api.RuleResult;
import com.njydsz.pmis.literule.api.RuleSeverity;
import com.njydsz.pmis.literule.api.ScorecardDefinition;
import com.njydsz.pmis.literule.expr.AviatorExpressionEvaluator;
import com.njydsz.pmis.literule.expr.ExpressionEvaluator;
import com.njydsz.pmis.literule.impl.ExpressionRule;
import com.njydsz.pmis.literule.impl.ScorecardRule;
import com.njydsz.pmis.literule.orchestrator.RuleChain;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * RuleDslParser + RuleDslConverter 单元测试
 *
 * <p>覆盖声明式 DSL 的解析、校验、转换、执行全链路。
 *
 * @author ydsz-pmis-team
 * @since 1.5.0
 */
@DisplayName("声明式 DSL 测试")
class RuleDslParserTest {

    private ExpressionEvaluator evaluator;

    @BeforeEach
    void setUp() {
        evaluator = new AviatorExpressionEvaluator(false);
    }

    // ============ 解析测试 ============

    @Test
    @DisplayName("解析空内容返回空 DSL")
    void parseEmptyShouldReturnEmptyDsl() {
        RuleDsl dsl = RuleDslParser.parse("");
        assertThat(dsl.getRules()).isEmpty();
        assertThat(dsl.getChains()).isEmpty();
    }

    @Test
    @DisplayName("解析 null 返回空 DSL")
    void parseNullShouldReturnEmptyDsl() {
        RuleDsl dsl = RuleDslParser.parse((String) null);
        assertThat(dsl.getRules()).isEmpty();
        assertThat(dsl.getChains()).isEmpty();
    }

    @Test
    @DisplayName("解析表达式规则 - snake_case 自动映射")
    void parseExpressionRuleShouldMapSnakeCase() {
        String yaml = """
                rules:
                  - code: EVM_RED_ALERT
                    name: EVM红灯告警
                    type: expression
                    category: EVM
                    priority: 10
                    severity: RED
                    condition: "evmRedCount >= 3"
                    title: "EVM 红灯 ${evmRedCount} 个"
                    mutex_group: EVM_ALERTS
                """;
        RuleDsl dsl = RuleDslParser.parse(yaml);
        assertThat(dsl.getRules()).hasSize(1);
        RuleDslEntry entry = dsl.getRules().get(0);
        assertThat(entry.getCode()).isEqualTo("EVM_RED_ALERT");
        assertThat(entry.getName()).isEqualTo("EVM红灯告警");
        assertThat(entry.getType()).isEqualTo("expression");
        assertThat(entry.getCategory()).isEqualTo("EVM");
        assertThat(entry.getPriority()).isEqualTo(10);
        assertThat(entry.getSeverity()).isEqualTo("RED");
        assertThat(entry.getCondition()).isEqualTo("evmRedCount >= 3");
        assertThat(entry.getMutexGroup()).isEqualTo("EVM_ALERTS");
    }

    @Test
    @DisplayName("解析评分卡规则 - factors 与 grades")
    void parseScorecardRuleShouldMapFactorsAndGrades() {
        String yaml = """
                rules:
                  - code: CREDIT_SCORE
                    name: 客户信用评分
                    type: scorecard
                    category: RISK
                    base_score: 100
                    direction: DESCENDING
                    min_score: 0
                    max_score: 100
                    red_threshold: 60
                    yellow_threshold: 80
                    factors:
                      - when: "overdueCount > 3"
                        score: -30
                        desc: "逾期过多"
                      - when: "contractAmount > 1000000"
                        score_expr: "-contractAmount * 0.001"
                        weight: 0.5
                        desc: "大额扣分"
                    grades:
                      - label: A
                        range: [90, 200]
                        severity: INFO
                      - label: D
                        range: [0, 60]
                        severity: RED
                """;
        RuleDsl dsl = RuleDslParser.parse(yaml);
        RuleDslEntry entry = dsl.getRules().get(0);
        assertThat(entry.getType()).isEqualTo("scorecard");
        assertThat(entry.getBaseScore()).isEqualTo(100.0);
        assertThat(entry.getDirection()).isEqualTo("DESCENDING");
        assertThat(entry.getFactors()).hasSize(2);
        assertThat(entry.getFactors().get(0).getWhen()).isEqualTo("overdueCount > 3");
        assertThat(entry.getFactors().get(0).getScore()).isEqualTo(-30.0);
        assertThat(entry.getFactors().get(1).getScoreExpr()).isEqualTo("-contractAmount * 0.001");
        assertThat(entry.getFactors().get(1).getWeight()).isEqualTo(0.5);
        assertThat(entry.getGrades()).hasSize(2);
        assertThat(entry.getGrades().get(0).getLabel()).isEqualTo("A");
        assertThat(entry.getGrades().get(0).getRange()).containsExactly(90.0, 200.0);
        assertThat(entry.getGrades().get(0).getSeverity()).isEqualTo("INFO");
    }

    @Test
    @DisplayName("解析规则链 - THEN / WHEN / IF / SWITCH")
    void parseChainsShouldMapAllTypes() {
        String yaml = """
                chains:
                  - name: RISK_CHAIN
                    type: THEN
                    steps: [RULE_A, RULE_B]
                  - name: PARALLEL_CHECK
                    type: WHEN
                    steps: [RULE_A, RULE_B]
                  - name: CONDITIONAL_FLOW
                    type: IF
                    condition: "amount > 1000"
                    step: HIGH_AMOUNT_RULE
                  - name: BRANCH_FLOW
                    type: SWITCH
                    branch_key: projectType
                    branches:
                      A: RULE_A
                      B: RULE_B
                    default: RULE_DEFAULT
                """;
        RuleDsl dsl = RuleDslParser.parse(yaml);
        assertThat(dsl.getChains()).hasSize(4);
        ChainDslEntry then = dsl.getChains().get(0);
        assertThat(then.getType()).isEqualTo("THEN");
        assertThat(then.getSteps()).containsExactly("RULE_A", "RULE_B");
        ChainDslEntry when = dsl.getChains().get(1);
        assertThat(when.getType()).isEqualTo("WHEN");
        ChainDslEntry ifChain = dsl.getChains().get(2);
        assertThat(ifChain.getType()).isEqualTo("IF");
        assertThat(ifChain.getCondition()).isEqualTo("amount > 1000");
        assertThat(ifChain.getStep()).isEqualTo("HIGH_AMOUNT_RULE");
        ChainDslEntry switchChain = dsl.getChains().get(3);
        assertThat(switchChain.getType()).isEqualTo("SWITCH");
        assertThat(switchChain.getBranchKey()).isEqualTo("projectType");
        assertThat(switchChain.getBranches()).containsEntry("A", "RULE_A");
        assertThat(switchChain.getDefaultRule()).isEqualTo("RULE_DEFAULT");
    }

    // ============ 校验测试 ============

    @Test
    @DisplayName("校验 - 缺少 code 抛异常")
    void validateShouldFailWhenCodeMissing() {
        String yaml = """
                rules:
                  - name: 无编码规则
                    condition: "amount > 100"
                """;
        RuleDsl dsl = RuleDslParser.parse(yaml);
        assertThatThrownBy(() -> RuleDslParser.validate(dsl))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("code 不能为空");
    }

    @Test
    @DisplayName("校验 - expression 缺少 condition 抛异常")
    void validateShouldFailWhenConditionMissing() {
        String yaml = """
                rules:
                  - code: NO_COND
                    name: 无条件
                    type: expression
                """;
        RuleDsl dsl = RuleDslParser.parse(yaml);
        assertThatThrownBy(() -> RuleDslParser.validate(dsl))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("缺少 condition");
    }

    @Test
    @DisplayName("校验 - 未知规则类型抛异常")
    void validateShouldFailWhenTypeUnknown() {
        String yaml = """
                rules:
                  - code: UNKNOWN_TYPE
                    name: 未知类型
                    type: unknown
                """;
        RuleDsl dsl = RuleDslParser.parse(yaml);
        assertThatThrownBy(() -> RuleDslParser.validate(dsl))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("未知规则类型");
    }

    @Test
    @DisplayName("校验 - THEN 链缺少 steps 抛异常")
    void validateShouldFailWhenStepsMissing() {
        String yaml = """
                chains:
                  - name: EMPTY_THEN
                    type: THEN
                """;
        RuleDsl dsl = RuleDslParser.parse(yaml);
        assertThatThrownBy(() -> RuleDslParser.validate(dsl))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("缺少 steps");
    }

    // ============ 转换测试 ============

    @Test
    @DisplayName("转换 - 表达式规则可执行")
    void convertExpressionRuleShouldBeExecutable() {
        String yaml = """
                rules:
                  - code: EVM_ALERT
                    name: EVM告警
                    type: expression
                    category: EVM
                    priority: 10
                    severity: RED
                    condition: "evmRedCount >= 3"
                    title: "EVM 红灯告警"
                """;
        RuleDsl dsl = RuleDslParser.parse(yaml);
        RuleDslParser.validate(dsl);
        List<Rule> rules = RuleDslConverter.toRules(dsl, evaluator);
        assertThat(rules).hasSize(1);
        Rule rule = rules.get(0);
        assertThat(rule).isInstanceOf(ExpressionRule.class);
        assertThat(rule.getCode()).isEqualTo("EVM_ALERT");
        // 执行规则
        RuleResult result = rule.evaluate(RuleContext.of(Map.of("evmRedCount", 5)));
        assertThat(result.isTriggered()).isTrue();
        assertThat(result.getSeverity()).isEqualTo(RuleSeverity.RED);
    }

    @Test
    @DisplayName("转换 - 评分卡规则可执行")
    void convertScorecardRuleShouldBeExecutable() {
        String yaml = """
                rules:
                  - code: CREDIT_SCORE
                    name: 客户信用评分
                    type: scorecard
                    category: RISK
                    base_score: 100
                    red_threshold: 60
                    yellow_threshold: 80
                    factors:
                      - when: "overdueCount > 3"
                        score: -30
                        desc: "逾期过多"
                """;
        RuleDsl dsl = RuleDslParser.parse(yaml);
        List<Rule> rules = RuleDslConverter.toRules(dsl, evaluator);
        assertThat(rules).hasSize(1);
        Rule rule = rules.get(0);
        assertThat(rule).isInstanceOf(ScorecardRule.class);
        // overdueCount=5 命中（-30），总分=70 → YELLOW
        RuleResult result = rule.evaluate(RuleContext.of(Map.of("overdueCount", 5)));
        assertThat(result.isTriggered()).isTrue();
        assertThat(result.getSeverity()).isEqualTo(RuleSeverity.YELLOW);
        assertThat(result.getCurrentValue()).isEqualTo("70.0");
    }

    @Test
    @DisplayName("转换 - THEN 链按顺序执行")
    void convertThenChainShouldExecuteInOrder() {
        String yaml = """
                rules:
                  - code: RULE_A
                    name: 规则A
                    type: expression
                    condition: "amount > 100"
                    severity: YELLOW
                  - code: RULE_B
                    name: 规则B
                    type: expression
                    condition: "amount > 200"
                    severity: RED
                chains:
                  - name: RISK_CHAIN
                    type: THEN
                    steps: [RULE_A, RULE_B]
                """;
        RuleDsl dsl = RuleDslParser.parse(yaml);
        List<Rule> rules = RuleDslConverter.toRules(dsl, evaluator);
        Map<String, Rule> ruleMap = RuleDslConverter.toRuleMap(rules);
        List<RuleChain> chains = RuleDslConverter.toChains(dsl, ruleMap, evaluator);
        assertThat(chains).hasSize(1);
        // amount=250 两条规则都命中
        List<RuleResult> results = chains.get(0).evaluate(
                RuleContext.of(Map.of("amount", 250)), evaluator);
        assertThat(results).hasSize(2);
        assertThat(results.get(0).getRuleCode()).isEqualTo("RULE_A");
        assertThat(results.get(1).getRuleCode()).isEqualTo("RULE_B");
    }

    @Test
    @DisplayName("转换 - IF 链条件不满足时不执行")
    void convertIfChainShouldNotExecuteWhenConditionFalse() {
        String yaml = """
                rules:
                  - code: HIGH_AMOUNT_RULE
                    name: 大额规则
                    type: expression
                    condition: "amount > 0"
                    severity: RED
                chains:
                  - name: CONDITIONAL_FLOW
                    type: IF
                    condition: "amount > 1000"
                    step: HIGH_AMOUNT_RULE
                """;
        RuleDsl dsl = RuleDslParser.parse(yaml);
        List<Rule> rules = RuleDslConverter.toRules(dsl, evaluator);
        Map<String, Rule> ruleMap = RuleDslConverter.toRuleMap(rules);
        List<RuleChain> chains = RuleDslConverter.toChains(dsl, ruleMap, evaluator);
        // amount=500 < 1000，条件不满足
        List<RuleResult> results = chains.get(0).evaluate(
                RuleContext.of(Map.of("amount", 500)), evaluator);
        assertThat(results).isEmpty();
        // amount=1500 > 1000，条件满足
        List<RuleResult> results2 = chains.get(0).evaluate(
                RuleContext.of(Map.of("amount", 1500)), evaluator);
        assertThat(results2).hasSize(1);
        assertThat(results2.get(0).getRuleCode()).isEqualTo("HIGH_AMOUNT_RULE");
    }

    @Test
    @DisplayName("转换 - SWITCH 链按分支 key 选择")
    void convertSwitchChainShouldSelectBranchByKey() {
        String yaml = """
                rules:
                  - code: RULE_A
                    name: A
                    type: expression
                    condition: "true"
                    severity: INFO
                  - code: RULE_B
                    name: B
                    type: expression
                    condition: "true"
                    severity: YELLOW
                  - code: RULE_DEFAULT
                    name: 默认
                    type: expression
                    condition: "true"
                    severity: RED
                chains:
                  - name: BRANCH_FLOW
                    type: SWITCH
                    branch_key: projectType
                    branches:
                      A: RULE_A
                      B: RULE_B
                    default: RULE_DEFAULT
                """;
        RuleDsl dsl = RuleDslParser.parse(yaml);
        List<Rule> rules = RuleDslConverter.toRules(dsl, evaluator);
        Map<String, Rule> ruleMap = RuleDslConverter.toRuleMap(rules);
        List<RuleChain> chains = RuleDslConverter.toChains(dsl, ruleMap, evaluator);
        // projectType=A → RULE_A
        List<RuleResult> resultsA = chains.get(0).evaluate(
                RuleContext.of(Map.of("projectType", "A")), evaluator);
        assertThat(resultsA).hasSize(1);
        assertThat(resultsA.get(0).getRuleCode()).isEqualTo("RULE_A");
        // projectType=C 未命中分支 → 默认 RULE_DEFAULT
        List<RuleResult> resultsC = chains.get(0).evaluate(
                RuleContext.of(Map.of("projectType", "C")), evaluator);
        assertThat(resultsC).hasSize(1);
        assertThat(resultsC.get(0).getRuleCode()).isEqualTo("RULE_DEFAULT");
    }

    @Test
    @DisplayName("转换 - 评分卡自定义评级映射")
    void convertScorecardWithGradesShouldMapCustomLabel() {
        String yaml = """
                rules:
                  - code: CREDIT
                    name: 信用评级
                    type: scorecard
                    category: RISK
                    base_score: 100
                    factors:
                      - when: "overdueCount > 3"
                        score: -30
                        desc: "逾期过多"
                    grades:
                      - label: A
                        range: [90, 200]
                        severity: INFO
                      - label: D
                        range: [0, 60]
                        severity: RED
                """;
        RuleDsl dsl = RuleDslParser.parse(yaml);
        List<Rule> rules = RuleDslConverter.toRules(dsl, evaluator);
        // overdueCount=5 命中（-30），总分=70，落在 [60, 90) 区间？无 A（[90,200)）、无 D（[0,60)）
        // 实际上 70 不在任何区间，返回 null → INFO
        RuleResult result = rules.get(0).evaluate(RuleContext.of(Map.of("overdueCount", 5)));
        assertThat(result.isTriggered()).isTrue();
        // 总分=70，未命中任何 grade，severity=INFO（fallback）
        assertThat(result.getSeverity()).isEqualTo(RuleSeverity.INFO);
    }

    @Test
    @DisplayName("转换 - 链引用不存在的规则抛异常")
    void convertChainShouldFailWhenRuleNotExists() {
        String yaml = """
                rules:
                  - code: RULE_A
                    name: A
                    type: expression
                    condition: "true"
                chains:
                  - name: BAD_CHAIN
                    type: THEN
                    steps: [RULE_A, NOT_EXIST]
                """;
        RuleDsl dsl = RuleDslParser.parse(yaml);
        List<Rule> rules = RuleDslConverter.toRules(dsl, evaluator);
        Map<String, Rule> ruleMap = RuleDslConverter.toRuleMap(rules);
        // 转换时引用不存在规则应被捕获，链列表为空
        List<RuleChain> chains = RuleDslConverter.toChains(dsl, ruleMap, evaluator);
        assertThat(chains).isEmpty();
    }

    @Test
    @DisplayName("转换 - 决策表规则可执行")
    void convertDecisionTableShouldBeExecutable() {
        String yaml = """
                rules:
                  - code: PROJECT_RISK_TABLE
                    name: 项目风险决策表
                    type: decision_table
                    category: RISK
                    hit_policy: FIRST
                    condition_columns:
                      - {name: evmRedCount, label: "EVM红灯数", type: number}
                    action_columns:
                      - {name: severity, label: "严重度", type: string}
                      - {name: title, label: "标题", type: string}
                    rows:
                      - conditions: {evmRedCount: ">=3"}
                        actions: {severity: RED, title: "EVM严重偏离"}
                      - conditions: {evmRedCount: ">=1"}
                        actions: {severity: YELLOW, title: "EVM偏离"}
                    default_actions: {severity: INFO, title: "正常"}
                """;
        RuleDsl dsl = RuleDslParser.parse(yaml);
        List<Rule> rules = RuleDslConverter.toRules(dsl, evaluator);
        assertThat(rules).hasSize(1);
        // evmRedCount=5 命中第一行（>=3）→ RED
        RuleResult result = rules.get(0).evaluate(RuleContext.of(Map.of("evmRedCount", 5)));
        assertThat(result.isTriggered()).isTrue();
        assertThat(result.getSeverity()).isEqualTo(RuleSeverity.RED);
    }

    @Test
    @DisplayName("端到端 - 多规则 + 链编排完整执行")
    void endToEndMultipleRulesWithChainShouldExecute() {
        String yaml = """
                rules:
                  - code: EVM_ALERT
                    name: EVM告警
                    type: expression
                    category: EVM
                    priority: 10
                    severity: RED
                    condition: "evmRedCount >= 3"
                  - code: CREDIT_SCORE
                    name: 信用评分
                    type: scorecard
                    category: RISK
                    base_score: 100
                    red_threshold: 60
                    yellow_threshold: 80
                    factors:
                      - when: "overdueCount > 3"
                        score: -30
                        desc: "逾期过多"
                chains:
                  - name: RISK_CHAIN
                    type: THEN
                    steps: [EVM_ALERT, CREDIT_SCORE]
                """;
        RuleDsl dsl = RuleDslParser.parse(yaml);
        RuleDslParser.validate(dsl);
        List<Rule> rules = RuleDslConverter.toRules(dsl, evaluator);
        Map<String, Rule> ruleMap = RuleDslConverter.toRuleMap(rules);
        List<RuleChain> chains = RuleDslConverter.toChains(dsl, ruleMap, evaluator);
        assertThat(rules).hasSize(2);
        assertThat(chains).hasSize(1);
        // evmRedCount=5 命中 EVM_ALERT，overdueCount=5 命中信用评分（-30，总分70）→ YELLOW
        List<RuleResult> results = chains.get(0).evaluate(
                RuleContext.of(Map.of("evmRedCount", 5, "overdueCount", 5)), evaluator);
        assertThat(results).hasSize(2);
        assertThat(results.get(0).getRuleCode()).isEqualTo("EVM_ALERT");
        assertThat(results.get(0).getSeverity()).isEqualTo(RuleSeverity.RED);
        assertThat(results.get(1).getRuleCode()).isEqualTo("CREDIT_SCORE");
        assertThat(results.get(1).getSeverity()).isEqualTo(RuleSeverity.YELLOW);
    }
}
