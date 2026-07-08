package com.njydsz.pmis.literule.core;

import com.njydsz.pmis.literule.api.Rule;
import com.njydsz.pmis.literule.api.RuleContext;
import com.njydsz.pmis.literule.api.RuleResult;
import com.njydsz.pmis.literule.api.RuleSeverity;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

/**
 * ParallelRuleEvaluator 单元测试（P2-3 高性能优化）
 *
 * @author ydsz-pmis-team
 */
@DisplayName("ParallelRuleEvaluator 单元测试")
class ParallelRuleEvaluatorTest {

    private ParallelRuleEvaluator evaluator;

    @BeforeEach
    void setUp() {
        evaluator = new ParallelRuleEvaluator(4);
    }

    @AfterEach
    void tearDown() {
        evaluator.shutdown();
    }

    @Nested
    @DisplayName("基础评估")
    class BasicEvaluationTest {

        @Test
        @DisplayName("空候选规则 - 返回空列表")
        void shouldReturnEmptyForNoRules() {
            RuleContext context = RuleContext.of(new HashMap<>());
            List<RuleResult> results = evaluator.evaluateParallel(
                    new ArrayList<>(), context, (rule, ctx) -> null);

            assertThat(results).isEmpty();
        }

        @Test
        @DisplayName("少量规则（≤3）- 串行快速路径")
        void shouldUseSequentialForFewRules() {
            Rule rule1 = mockRule("R1", 100, null);
            Rule rule2 = mockRule("R2", 200, null);
            RuleContext context = RuleContext.of(new HashMap<>());

            List<RuleResult> results = evaluator.evaluateParallel(
                    List.of(rule1, rule2), context,
                    (rule, ctx) -> RuleResult.triggered(rule.getCode(), rule.getName(),
                            "TEST", RuleSeverity.RED, "T", "D"));

            assertThat(results).hasSize(2);
            // 不应触发并行评估计数（≤3 规则走串行路径）
            assertThat(evaluator.getParallelEvalCount()).isEqualTo(0);
        }

        @Test
        @DisplayName("多规则并行评估 - 全部触发")
        void shouldEvaluateAllRulesInParallel() {
            List<Rule> rules = new ArrayList<>();
            for (int i = 0; i < 10; i++) {
                rules.add(mockRule("R" + i, 100 + i, null));
            }
            RuleContext context = RuleContext.of(new HashMap<>());

            List<RuleResult> results = evaluator.evaluateParallel(
                    rules, context,
                    (rule, ctx) -> RuleResult.triggered(rule.getCode(), rule.getName(),
                            "TEST", RuleSeverity.YELLOW, "T", "D"));

            assertThat(results).hasSize(10);
            assertThat(evaluator.getParallelEvalCount()).isEqualTo(1);
        }

        @Test
        @DisplayName("部分触发 - 仅返回触发的结果")
        void shouldReturnOnlyTriggeredResults() {
            List<Rule> rules = new ArrayList<>();
            for (int i = 0; i < 10; i++) {
                rules.add(mockRule("R" + i, 100 + i, null));
            }
            RuleContext context = RuleContext.of(new HashMap<>());

            List<RuleResult> results = evaluator.evaluateParallel(
                    rules, context,
                    (rule, ctx) -> {
                        // 只有偶数规则触发
                        if (Integer.parseInt(rule.getCode().substring(1)) % 2 == 0) {
                            return RuleResult.triggered(rule.getCode(), rule.getName(),
                                    "TEST", RuleSeverity.RED, "T", "D");
                        }
                        return RuleResult.notTriggered(rule.getCode());
                    });

            assertThat(results).hasSize(5);
            for (RuleResult r : results) {
                int idx = Integer.parseInt(r.getRuleCode().substring(1));
                assertThat(idx % 2).isEqualTo(0);
            }
        }
    }

    @Nested
    @DisplayName("互斥组语义")
    class MutexGroupTest {

        @Test
        @DisplayName("同组规则 - 首条命中后跳过同组后续")
        void shouldSkipSameGroupAfterFirstHit() {
            List<Rule> rules = new ArrayList<>();
            // 同一互斥组 GROUP_A，3 条规则
            rules.add(mockRule("R1", 100, "GROUP_A"));
            rules.add(mockRule("R2", 200, "GROUP_A"));
            rules.add(mockRule("R3", 300, "GROUP_A"));
            // 独立规则
            rules.add(mockRule("R4", 400, null));
            rules.add(mockRule("R5", 500, null));

            RuleContext context = RuleContext.of(new HashMap<>());

            List<RuleResult> results = evaluator.evaluateParallel(
                    rules, context,
                    (rule, ctx) -> {
                        // R1 和 R4 触发
                        if ("R1".equals(rule.getCode()) || "R4".equals(rule.getCode())) {
                            return RuleResult.triggered(rule.getCode(), rule.getName(),
                                    "TEST", RuleSeverity.RED, "T", "D");
                        }
                        return RuleResult.notTriggered(rule.getCode());
                    });

            // R1 命中后 R2/R3 跳过，R4 也触发
            assertThat(results).hasSize(2);
            List<String> codes = results.stream().map(r -> r.getRuleCode()).toList();
            assertThat(codes).contains("R1", "R4");
            assertThat(codes).doesNotContain("R2", "R3");
        }

        @Test
        @DisplayName("不同互斥组 - 各自独立评估")
        void shouldEvaluateDifferentGroupsIndependently() {
            List<Rule> rules = new ArrayList<>();
            rules.add(mockRule("R1", 100, "GROUP_A"));
            rules.add(mockRule("R2", 200, "GROUP_A"));
            rules.add(mockRule("R3", 300, "GROUP_B"));
            rules.add(mockRule("R4", 400, "GROUP_B"));
            rules.add(mockRule("R5", 500, null));

            RuleContext context = RuleContext.of(new HashMap<>());

            List<RuleResult> results = evaluator.evaluateParallel(
                    rules, context,
                    (rule, ctx) -> RuleResult.triggered(rule.getCode(), rule.getName(),
                            "TEST", RuleSeverity.YELLOW, "T", "D"));

            // 每组只保留首条命中
            assertThat(results).hasSize(3); // GROUP_A: R1, GROUP_B: R3, INDEP: R5
            List<String> codes = results.stream().map(r -> r.getRuleCode()).toList();
            assertThat(codes).contains("R1", "R3", "R5");
        }
    }

    @Nested
    @DisplayName("异常处理")
    class ExceptionHandlingTest {

        @Test
        @DisplayName("单规则评估异常 - 不影响其他规则")
        void shouldNotBreakOnSingleRuleException() {
            List<Rule> rules = new ArrayList<>();
            for (int i = 0; i < 10; i++) {
                rules.add(mockRule("R" + i, 100 + i, null));
            }
            RuleContext context = RuleContext.of(new HashMap<>());

            List<RuleResult> results = evaluator.evaluateParallel(
                    rules, context,
                    (rule, ctx) -> {
                        if ("R3".equals(rule.getCode())) {
                            throw new RuntimeException("R3 故障");
                        }
                        return RuleResult.triggered(rule.getCode(), rule.getName(),
                                "TEST", RuleSeverity.RED, "T", "D");
                    });

            // R3 异常不影响其他 9 条规则
            assertThat(results).hasSize(9);
            List<String> codes = results.stream().map(r -> r.getRuleCode()).toList();
            assertThat(codes).doesNotContain("R3");
        }
    }

    @Nested
    @DisplayName("结果排序")
    class ResultSortingTest {

        @Test
        @DisplayName("结果按严重度倒序排列")
        void shouldSortBySeverityDescending() {
            List<Rule> rules = new ArrayList<>();
            for (int i = 0; i < 10; i++) {
                rules.add(mockRule("R" + i, 100 + i, null));
            }
            RuleContext context = RuleContext.of(new HashMap<>());

            List<RuleResult> results = evaluator.evaluateParallel(
                    rules, context,
                    (rule, ctx) -> {
                        int idx = Integer.parseInt(rule.getCode().substring(1));
                        RuleSeverity severity = idx % 3 == 0 ? RuleSeverity.RED
                                : idx % 3 == 1 ? RuleSeverity.YELLOW
                                : RuleSeverity.INFO;
                        return RuleResult.triggered(rule.getCode(), rule.getName(),
                                "TEST", severity, "T", "D");
                    });

            assertThat(results).hasSize(10);
            // RED 应在 YELLOW 之前，YELLOW 在 INFO 之前
            int lastWeight = Integer.MAX_VALUE;
            for (RuleResult r : results) {
                int weight = r.getSeverity().getWeight();
                assertThat(weight).isLessThanOrEqualTo(lastWeight);
                lastWeight = weight;
            }
        }
    }

    @Nested
    @DisplayName("统计指标")
    class StatisticsTest {

        @Test
        @DisplayName("并行评估次数正确")
        void shouldCountParallelEvaluations() {
            List<Rule> rules = new ArrayList<>();
            for (int i = 0; i < 10; i++) {
                rules.add(mockRule("R" + i, 100 + i, null));
            }
            RuleContext context = RuleContext.of(new HashMap<>());

            evaluator.evaluateParallel(rules, context, (rule, ctx) -> null);
            evaluator.evaluateParallel(rules, context, (rule, ctx) -> null);

            assertThat(evaluator.getParallelEvalCount()).isEqualTo(2);
        }

        @Test
        @DisplayName("累计分组数正确")
        void shouldCountTotalGroups() {
            List<Rule> rules = new ArrayList<>();
            for (int i = 0; i < 10; i++) {
                rules.add(mockRule("R" + i, 100 + i, i < 5 ? "GROUP_A" : null));
            }
            RuleContext context = RuleContext.of(new HashMap<>());

            evaluator.evaluateParallel(rules, context, (rule, ctx) -> null);

            // GROUP_A + 5 个独立规则 = 6 组
            assertThat(evaluator.getTotalGroups()).isEqualTo(6);
        }
    }

    // ==================== 辅助方法 ====================

    /**
     * 创建 mock 规则
     */
    private Rule mockRule(String code, int priority, String mutexGroup) {
        Rule rule = Mockito.mock(Rule.class);
        when(rule.getCode()).thenReturn(code);
        when(rule.getName()).thenReturn("Rule-" + code);
        when(rule.getPriority()).thenReturn(priority);
        when(rule.getMutexGroup()).thenReturn(mutexGroup);
        when(rule.getTenantId()).thenReturn("1");
        when(rule.getEnvironment()).thenReturn("default");
        when(rule.getScope()).thenReturn("ALL");
        when(rule.evaluate(any())).thenReturn(RuleResult.notTriggered(code));
        return rule;
    }
}
