package com.njydsz.pmis.literule.config;

import com.njydsz.pmis.literule.api.RuleDefinition;
import com.njydsz.pmis.literule.api.RuleSeverity;
import com.njydsz.pmis.literule.expr.ExpressionEvaluator;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

/**
 * {@link ABTestService} 单元测试。
 *
 * <p>覆盖 A/B 测试对比能力，包括触发状态差异、严重度差异、标题/描述差异检测。
 *
 * @author ydsz-pmis-team
 */
@DisplayName("规则 A/B 测试服务测试")
@ExtendWith(MockitoExtension.class)
class ABTestServiceTest {

    @Mock
    private ExpressionEvaluator evaluator;

    @InjectMocks
    private ABTestService abTestService;

    private RuleDefinition buildRule(String code, String condition, int version) {
        return RuleDefinition.builder()
                .code(code)
                .name("规则-" + code)
                .conditionExpression(condition)
                .defaultSeverity(RuleSeverity.YELLOW)
                .status("PUBLISHED")
                .version(version)
                .enabled(true)
                .build();
    }

    @Nested
    @DisplayName("A/B 测试：test")
    class TestMethodTest {

        @Test
        @DisplayName("正常场景：两个版本均未触发，无差异")
        void shouldReturnNoDiffWhenBothNotTriggered() {
            RuleDefinition current = buildRule("R001", "amount > 1000", 1);
            RuleDefinition candidate = buildRule("R001", "amount > 2000", 2);
            when(evaluator.evalBoolean(eq("amount > 1000"), any())).thenReturn(false);
            when(evaluator.evalBoolean(eq("amount > 2000"), any())).thenReturn(false);

            ABTestService.ABTestReport report = abTestService.test(
                    current, candidate, Map.of("amount", 500));

            assertThat(report.ruleCode()).isEqualTo("R001");
            assertThat(report.currentVersion()).isEqualTo(1);
            assertThat(report.candidateVersion()).isEqualTo(2);
            assertThat(report.diff().get("hasDiff")).isEqualTo(false);
            assertThat(report.summary()).contains("无差异");
        }

        @Test
        @DisplayName("正常场景：触发状态发生变化")
        void shouldDetectTriggeredChanged() {
            RuleDefinition current = buildRule("R001", "amount > 1000", 1);
            RuleDefinition candidate = buildRule("R001", "amount > 500", 2);
            when(evaluator.evalBoolean(eq("amount > 1000"), any())).thenReturn(false);
            when(evaluator.evalBoolean(eq("amount > 500"), any())).thenReturn(true);

            ABTestService.ABTestReport report = abTestService.test(
                    current, candidate, Map.of("amount", 800));

            assertThat(report.diff().get("triggeredChanged")).isEqualTo(true);
            assertThat(report.diff().get("hasDiff")).isEqualTo(true);
            assertThat(report.diff().get("triggeredBefore")).isEqualTo(false);
            assertThat(report.diff().get("triggeredAfter")).isEqualTo(true);
            assertThat(report.summary()).contains("检测到差异");
        }

        @Test
        @DisplayName("正常场景：两版本均触发，无差异")
        void shouldReturnNoDiffWhenBothTriggered() {
            RuleDefinition current = buildRule("R001", "amount > 1000", 1);
            RuleDefinition candidate = buildRule("R001", "amount > 1000", 2);
            when(evaluator.evalBoolean(eq("amount > 1000"), any())).thenReturn(true);

            ABTestService.ABTestReport report = abTestService.test(
                    current, candidate, Map.of("amount", 2000));

            assertThat(report.diff().get("hasDiff")).isEqualTo(false);
            assertThat(report.currentResult().isTriggered()).isTrue();
            assertThat(report.candidateResult().isTriggered()).isTrue();
        }

        @Test
        @DisplayName("正常场景：评估异常时视为未触发")
        void shouldHandleEvaluationException() {
            RuleDefinition current = buildRule("R001", "amount > 1000", 1);
            RuleDefinition candidate = buildRule("R001", "amount > 2000", 2);
            when(evaluator.evalBoolean(eq("amount > 1000"), any()))
                    .thenThrow(new RuntimeException("评估异常"));
            when(evaluator.evalBoolean(eq("amount > 2000"), any())).thenReturn(false);

            ABTestService.ABTestReport report = abTestService.test(
                    current, candidate, Map.of("amount", 500));

            assertThat(report).isNotNull();
            assertThat(report.currentResult().isTriggered()).isFalse();
        }

        @Test
        @DisplayName("正常场景：报告包含规则编码和版本号")
        void shouldIncludeRuleCodeAndVersions() {
            RuleDefinition current = buildRule("R001", "amount > 1000", 1);
            RuleDefinition candidate = buildRule("R001", "amount > 1000", 3);
            when(evaluator.evalBoolean(eq("amount > 1000"), any())).thenReturn(true);

            ABTestService.ABTestReport report = abTestService.test(
                    current, candidate, Map.of("amount", 2000));

            assertThat(report.ruleCode()).isEqualTo("R001");
            assertThat(report.currentVersion()).isEqualTo(1);
            assertThat(report.candidateVersion()).isEqualTo(3);
        }

        @Test
        @DisplayName("正常场景：空 facts 不抛异常")
        void shouldHandleEmptyFacts() {
            RuleDefinition current = buildRule("R001", "amount > 1000", 1);
            RuleDefinition candidate = buildRule("R001", "amount > 1000", 2);
            when(evaluator.evalBoolean(eq("amount > 1000"), any())).thenReturn(false);

            ABTestService.ABTestReport report = abTestService.test(
                    current, candidate, Map.of());

            assertThat(report).isNotNull();
            assertThat(report.diff().get("hasDiff")).isEqualTo(false);
        }
    }
}
