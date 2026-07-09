package com.njydsz.pmis.literule.ai;

import com.njydsz.pmis.literule.api.RuleDefinition;
import com.njydsz.pmis.literule.api.RuleExecutionTrace;
import com.njydsz.pmis.literule.api.RuleSeverity;
import com.njydsz.pmis.literule.config.RuleAdminService;
import com.njydsz.pmis.literule.expr.ExpressionEvaluator;
import com.njydsz.pmis.literule.expr.ExpressionTraceNode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * {@link RuleAttributionService} 单元测试。
 *
 * <p>覆盖规则归因分析能力，包括按规则编码归因、基于追踪结果归因、批量归因，
 * 含 LLM 降级、错误节点处理、短路因子提取等场景。
 *
 * @author ydsz-pmis-team
 */
@DisplayName("规则归因分析服务测试")
@ExtendWith(MockitoExtension.class)
class RuleAttributionServiceTest {

    @Mock
    private RuleAdminService ruleAdminService;

    @Mock
    private LLMClient llmClient;

    private RuleAttributionService service;
    private RuleAttributionService serviceWithoutLLM;

    @BeforeEach
    void setUp() {
        service = new RuleAttributionService(ruleAdminService, llmClient);
        serviceWithoutLLM = new RuleAttributionService(ruleAdminService, null);
    }

    private RuleDefinition buildRule(String code, String condition, RuleSeverity severity) {
        return RuleDefinition.builder()
                .code(code)
                .name("规则-" + code)
                .conditionExpression(condition)
                .defaultSeverity(severity)
                .build();
    }

    private ExpressionEvaluator.TraceResult buildTraceResult(ExpressionTraceNode root, boolean triggered) {
        return new ExpressionEvaluator.TraceResult(triggered, root);
    }

    // ==================== analyze(ruleCode, facts) ====================

    @Nested
    @DisplayName("按规则编码归因：analyze(ruleCode, facts)")
    class AnalyzeByRuleCodeTest {

        @Test
        @DisplayName("边界条件：ruleCode 为 null 返回错误报告")
        void shouldReturnErrorReportWhenRuleCodeNull() {
            AttributionReport report = service.analyze(null, Map.of());

            assertThat(report.getSummary()).contains("规则编码不能为空");
            assertThat(report.isTriggered()).isFalse();
            assertThat(report.getFactors()).isEmpty();
        }

        @Test
        @DisplayName("边界条件：ruleCode 为空白返回错误报告")
        void shouldReturnErrorReportWhenRuleCodeBlank() {
            AttributionReport report = service.analyze("  ", Map.of());

            assertThat(report.getSummary()).contains("规则编码不能为空");
        }

        @Test
        @DisplayName("异常场景：规则不存在返回错误报告")
        void shouldReturnErrorReportWhenRuleNotFound() {
            when(ruleAdminService.getByCode("R001")).thenReturn(null);

            AttributionReport report = service.analyze("R001", Map.of());

            assertThat(report.getSummary()).contains("规则不存在");
            assertThat(report.getRuleCode()).isEqualTo("R001");
        }

        @Test
        @DisplayName("正常场景：规则存在时调用 traceExpression 并生成归因")
        void shouldGenerateAttributionWhenRuleExists() {
            RuleDefinition rule = buildRule("R001", "amount > 1000", RuleSeverity.YELLOW);
            when(ruleAdminService.getByCode("R001")).thenReturn(rule);
            ExpressionTraceNode root = ExpressionTraceNode.comparison(">", "amount", "amount", "1000", 1000, true);
            ExpressionEvaluator.TraceResult traceResult = buildTraceResult(root, true);
            when(ruleAdminService.traceExpression(eq("amount > 1000"), anyMap())).thenReturn(traceResult);

            AttributionReport report = service.analyze("R001", Map.of("amount", 1500));

            assertThat(report.getRuleCode()).isEqualTo("R001");
            assertThat(report.getRuleName()).isEqualTo("规则-R001");
            assertThat(report.isTriggered()).isTrue();
            assertThat(report.getSeverity()).isEqualTo("YELLOW");
            assertThat(report.getFactors()).hasSize(1);
            assertThat(report.getFactors().get(0).getVariable()).isEqualTo("amount");
            assertThat(report.getFactors().get(0).getOperator()).isEqualTo(">");
        }

        @Test
        @DisplayName("正常场景：null facts 视为空 Map")
        void shouldHandleNullFacts() {
            RuleDefinition rule = buildRule("R001", "amount > 1000", RuleSeverity.RED);
            when(ruleAdminService.getByCode("R001")).thenReturn(rule);
            ExpressionTraceNode root = ExpressionTraceNode.comparison(">", "amount", "amount", "1000", 1000, false);
            ExpressionEvaluator.TraceResult traceResult = buildTraceResult(root, false);
            when(ruleAdminService.traceExpression(eq("amount > 1000"), anyMap())).thenReturn(traceResult);

            AttributionReport report = service.analyze("R001", null);

            assertThat(report).isNotNull();
            assertThat(report.isTriggered()).isFalse();
        }
    }

    // ==================== analyze(traceResult, ruleCode, ruleName) ====================

    @Nested
    @DisplayName("基于追踪结果归因：analyze(traceResult, ruleCode, ruleName)")
    class AnalyzeByTraceResultTest {

        @Test
        @DisplayName("边界条件：traceResult 为 null 返回错误报告")
        void shouldReturnErrorReportWhenTraceResultNull() {
            AttributionReport report = service.analyze(null, "R001", "规则-R001");

            assertThat(report.getSummary()).contains("追踪结果为空");
        }

        @Test
        @DisplayName("正常场景：单条件触发时生成归因因子")
        void shouldGenerateFactorForSingleCondition() {
            ExpressionTraceNode root = ExpressionTraceNode.comparison(">", "amount", "amount", "1000", 1000, true);
            ExpressionEvaluator.TraceResult traceResult = buildTraceResult(root, true);

            AttributionReport report = serviceWithoutLLM.analyze(traceResult, "R001", "规则-R001");

            assertThat(report.isTriggered()).isTrue();
            assertThat(report.getFactors()).hasSize(1);
            AttributionReport.AttributionFactor factor = report.getFactors().get(0);
            assertThat(factor.getVariable()).isEqualTo("amount");
            assertThat(factor.getCurrentValue()).isEqualTo("amount");
            assertThat(factor.getThreshold()).isEqualTo(1000);
            assertThat(factor.getOperator()).isEqualTo(">");
            assertThat(factor.isSatisfied()).isTrue();
            assertThat(factor.getImpact()).contains("amount");
        }

        @Test
        @DisplayName("正常场景：AND 条件未触发时生成多个因子")
        void shouldGenerateMultipleFactorsForAndCondition() {
            ExpressionTraceNode left = ExpressionTraceNode.comparison(">", "amount", "amount", "1000", 1000, true);
            ExpressionTraceNode right = ExpressionTraceNode.comparison("<", "score", "score", "800", 800, false);
            ExpressionTraceNode root = ExpressionTraceNode.logical("&&", false, left, right);
            ExpressionEvaluator.TraceResult traceResult = buildTraceResult(root, false);

            AttributionReport report = serviceWithoutLLM.analyze(traceResult, "R001", "规则-R001");

            assertThat(report.isTriggered()).isFalse();
            assertThat(report.getFactors()).hasSize(2);
            assertThat(report.getSummary()).contains("未触发");
        }

        @Test
        @DisplayName("正常场景：错误节点不调用 LLM")
        void shouldNotCallLLMWhenErrorNode() {
            ExpressionTraceNode root = ExpressionTraceNode.builder()
                    .nodeType(ExpressionTraceNode.NodeType.ROOT)
                    .expression("invalid")
                    .result(false)
                    .error("语法错误")
                    .build();
            ExpressionEvaluator.TraceResult traceResult = buildTraceResult(root, false);

            AttributionReport report = service.analyze(traceResult, "R001", "规则-R001");

            assertThat(report.getSummary()).contains("规则评估异常");
            verify(llmClient, never()).chat(anyString(), anyString(), any());
        }

        @Test
        @DisplayName("正常场景：LLM 可用时增强归因报告")
        void shouldEnrichWithLLMWhenAvailable() {
            ExpressionTraceNode root = ExpressionTraceNode.comparison(">", "amount", "amount", "1000", 1000, true);
            ExpressionEvaluator.TraceResult traceResult = buildTraceResult(root, true);
            String llmResponse = "{\"analysis\": \"金额超标触发\", \"recommendation\": \"建议提高阈值\"}";
            when(llmClient.chat(anyString(), anyString(), any())).thenReturn(llmResponse);

            AttributionReport report = service.analyze(traceResult, "R001", "规则-R001");

            assertThat(report.getLlmAnalysis()).isEqualTo("金额超标触发");
            assertThat(report.getRecommendation()).isEqualTo("建议提高阈值");
        }

        @Test
        @DisplayName("异常场景：LLM 调用失败时降级返回基础归因")
        void shouldFallbackWhenLLMThrowsException() {
            ExpressionTraceNode root = ExpressionTraceNode.comparison(">", "amount", "amount", "1000", 1000, true);
            ExpressionEvaluator.TraceResult traceResult = buildTraceResult(root, true);
            when(llmClient.chat(anyString(), anyString(), any()))
                    .thenThrow(new LLMException("MOCK", "LLM 不可用"));

            AttributionReport report = service.analyze(traceResult, "R001", "规则-R001");

            assertThat(report.getLlmAnalysis()).isNull();
            assertThat(report.getRecommendation()).isNull();
            assertThat(report.getSummary()).isNotBlank();
        }

        @Test
        @DisplayName("正常场景：LLM 返回空响应时不增强")
        void shouldNotEnrichWhenLLMReturnsBlank() {
            ExpressionTraceNode root = ExpressionTraceNode.comparison(">", "amount", "amount", "1000", 1000, true);
            ExpressionEvaluator.TraceResult traceResult = buildTraceResult(root, true);
            when(llmClient.chat(anyString(), anyString(), any())).thenReturn("  ");

            AttributionReport report = service.analyze(traceResult, "R001", "规则-R001");

            assertThat(report.getLlmAnalysis()).isNull();
        }

        @Test
        @DisplayName("正常场景：LLM 返回纯文本时降级为 llmAnalysis")
        void shouldFallbackToPlainTextWhenLLMReturnsNonJson() {
            ExpressionTraceNode root = ExpressionTraceNode.comparison(">", "amount", "amount", "1000", 1000, true);
            ExpressionEvaluator.TraceResult traceResult = buildTraceResult(root, true);
            when(llmClient.chat(anyString(), anyString(), any())).thenReturn("这是纯文本分析");

            AttributionReport report = service.analyze(traceResult, "R001", "规则-R001");

            assertThat(report.getLlmAnalysis()).isEqualTo("这是纯文本分析");
            assertThat(report.getRecommendation()).isNull();
        }
    }

    // ==================== analyzeBatch ====================

    @Nested
    @DisplayName("批量归因：analyzeBatch")
    class AnalyzeBatchTest {

        @Test
        @DisplayName("边界条件：null 入参返回空列表")
        void shouldReturnEmptyWhenTracesNull() {
            List<AttributionReport> reports = service.analyzeBatch(null);

            assertThat(reports).isEmpty();
        }

        @Test
        @DisplayName("边界条件：空列表返回空列表")
        void shouldReturnEmptyWhenTracesEmpty() {
            List<AttributionReport> reports = service.analyzeBatch(List.of());

            assertThat(reports).isEmpty();
        }

        @Test
        @DisplayName("正常场景：批量归因多条轨迹")
        void shouldAnalyzeBatchTraces() {
            RuleExecutionTrace trace1 = new RuleExecutionTrace();
            trace1.setRuleCode("R001");
            trace1.setRuleName("规则-R001");
            trace1.setTriggered(true);
            trace1.setSeverity("YELLOW");
            trace1.setFactsSnapshot(Map.of("amount", 1500));

            RuleExecutionTrace trace2 = new RuleExecutionTrace();
            trace2.setRuleCode("R002");
            trace2.setRuleName("规则-R002");
            trace2.setTriggered(false);
            trace2.setSeverity("INFO");
            trace2.setFactsSnapshot(Map.of());

            RuleDefinition rule1 = buildRule("R001", "amount > 1000", RuleSeverity.YELLOW);
            RuleDefinition rule2 = buildRule("R002", "score < 800", RuleSeverity.INFO);
            when(ruleAdminService.getByCode("R001")).thenReturn(rule1);
            when(ruleAdminService.getByCode("R002")).thenReturn(rule2);
            ExpressionTraceNode root1 = ExpressionTraceNode.comparison(">", "amount", "amount", "1000", 1000, true);
            ExpressionTraceNode root2 = ExpressionTraceNode.comparison("<", "score", "score", "800", 800, false);
            when(ruleAdminService.traceExpression(eq("amount > 1000"), anyMap()))
                    .thenReturn(buildTraceResult(root1, true));
            when(ruleAdminService.traceExpression(eq("score < 800"), anyMap()))
                    .thenReturn(buildTraceResult(root2, false));

            List<AttributionReport> reports = serviceWithoutLLM.analyzeBatch(List.of(trace1, trace2));

            assertThat(reports).hasSize(2);
            assertThat(reports.get(0).getRuleCode()).isEqualTo("R001");
            assertThat(reports.get(0).isTriggered()).isTrue();
            assertThat(reports.get(1).getRuleCode()).isEqualTo("R002");
            assertThat(reports.get(1).getRuleName()).isEqualTo("规则-R002");
        }

        @Test
        @DisplayName("边界条件：factsSnapshot 为 null 时视为空 Map")
        void shouldHandleNullFactsSnapshot() {
            RuleExecutionTrace trace = new RuleExecutionTrace();
            trace.setRuleCode("R001");
            trace.setRuleName("规则-R001");
            trace.setTriggered(false);
            trace.setSeverity("INFO");
            trace.setFactsSnapshot(null);

            RuleDefinition rule = buildRule("R001", "amount > 1000", RuleSeverity.YELLOW);
            when(ruleAdminService.getByCode("R001")).thenReturn(rule);
            ExpressionTraceNode root = ExpressionTraceNode.comparison(">", "amount", "amount", "1000", 1000, false);
            when(ruleAdminService.traceExpression(eq("amount > 1000"), anyMap()))
                    .thenReturn(buildTraceResult(root, false));

            List<AttributionReport> reports = serviceWithoutLLM.analyzeBatch(List.of(trace));

            assertThat(reports).hasSize(1);
        }
    }
}
