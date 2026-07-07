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
import org.mockito.Mockito;

import java.util.HashMap;
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
 * RuleAttributionService 单元测试（P3-3 LLM 辅助归因分析）
 *
 * <p>测试归因分析服务在各种表达式追踪场景下的归因因子提取、摘要生成、
 * LLM 增强（可用/不可用降级）、批量归因等能力。
 *
 * <p>测试风格参考 {@code DefaultRuleEngineTest}：Mockito.mock 手动创建，
 * JUnit 5 @Nested 分组，AssertJ 断言。
 *
 * @author ydsz-pmis-team
 */
@DisplayName("RuleAttributionService 单元测试")
class RuleAttributionServiceTest {

    private RuleAdminService mockAdminService;
    private LLMClient mockLlmClient;

    @BeforeEach
    void setUp() {
        mockAdminService = Mockito.mock(RuleAdminService.class);
        mockLlmClient = Mockito.mock(LLMClient.class);
    }

    // ==================== 辅助方法 ====================

    /**
     * 构建简单比较追踪结果
     */
    private ExpressionEvaluator.TraceResult simpleComparison(String varName, Object varValue,
                                                               String operator, Object threshold,
                                                               boolean result) {
        ExpressionTraceNode node = ExpressionTraceNode.comparison(
                operator, varName, varValue, String.valueOf(threshold), threshold, result);
        return new ExpressionEvaluator.TraceResult(result, node);
    }

    /**
     * 构建 AND/OR 逻辑追踪结果
     */
    private ExpressionEvaluator.TraceResult logicalTrace(String operator, boolean finalResult,
                                                           boolean shortCircuited,
                                                           ExpressionTraceNode left,
                                                           ExpressionTraceNode right) {
        ExpressionTraceNode node = ExpressionTraceNode.builder()
                .nodeType(ExpressionTraceNode.NodeType.LOGICAL)
                .operator(operator)
                .expression(left.getExpression() + " " + operator + " " + right.getExpression())
                .result(finalResult)
                .shortCircuited(shortCircuited)
                .children(List.of(left, right))
                .build();
        return new ExpressionEvaluator.TraceResult(finalResult, node);
    }

    /**
     * 构建短路跳过的右节点
     */
    private ExpressionTraceNode shortCircuitedNode(String expression) {
        return ExpressionTraceNode.builder()
                .nodeType(ExpressionTraceNode.NodeType.ROOT)
                .expression(expression)
                .shortCircuited(true)
                .error("短路跳过")
                .build();
    }

    /**
     * 构建规则定义
     */
    private RuleDefinition buildRule(String code, String name, String expr) {
        return RuleDefinition.builder()
                .code(code)
                .name(name)
                .conditionExpression(expr)
                .defaultSeverity(RuleSeverity.YELLOW)
                .build();
    }

    // ==================== 简单比较表达式归因 ====================

    @Nested
    @DisplayName("简单比较表达式归因")
    class SimpleComparisonTest {

        @Test
        @DisplayName("amount > 1000，amount=1500 → 满足，规则触发")
        void shouldAttributeSatisfiedComparison() {
            ExpressionEvaluator.TraceResult trace = simpleComparison(
                    "amount", 1500, ">", 1000, true);

            RuleAttributionService service = new RuleAttributionService(mockAdminService, null);
            AttributionReport report = service.analyze(trace, "R1", "金额阈值规则");

            assertThat(report.isTriggered()).isTrue();
            assertThat(report.getRuleCode()).isEqualTo("R1");
            assertThat(report.getRuleName()).isEqualTo("金额阈值规则");
            assertThat(report.getFactors()).hasSize(1);
            AttributionReport.AttributionFactor f = report.getFactors().get(0);
            assertThat(f.getVariable()).isEqualTo("amount");
            assertThat(f.getCurrentValue()).isEqualTo(1500);
            assertThat(f.getOperator()).isEqualTo(">");
            assertThat(f.getThreshold()).isEqualTo(1000);
            assertThat(f.isSatisfied()).isTrue();
            assertThat(f.isShortCircuited()).isFalse();
            assertThat(report.getSummary()).contains("amount=1500");
            assertThat(report.getSummary()).contains("满足");
            assertThat(report.getSummary()).contains("触发");
            assertThat(report.getLlmAnalysis()).isNull();
            assertThat(report.getRecommendation()).isNull();
        }

        @Test
        @DisplayName("amount > 1000，amount=500 → 不满足，规则未触发")
        void shouldAttributeUnsatisfiedComparison() {
            ExpressionEvaluator.TraceResult trace = simpleComparison(
                    "amount", 500, ">", 1000, false);

            RuleAttributionService service = new RuleAttributionService(mockAdminService, null);
            AttributionReport report = service.analyze(trace, "R1", "金额阈值规则");

            assertThat(report.isTriggered()).isFalse();
            assertThat(report.getFactors()).hasSize(1);
            AttributionReport.AttributionFactor f = report.getFactors().get(0);
            assertThat(f.getVariable()).isEqualTo("amount");
            assertThat(f.getCurrentValue()).isEqualTo(500);
            assertThat(f.isSatisfied()).isFalse();
            assertThat(report.getSummary()).contains("不满足");
            assertThat(report.getSummary()).contains("未触发");
        }
    }

    // ==================== AND 逻辑归因 ====================

    @Nested
    @DisplayName("AND 逻辑归因")
    class AndLogicTest {

        @Test
        @DisplayName("左满足右不满足 → 不触发，归因到右条件")
        void shouldAttributeAndToUnsatisfiedRight() {
            ExpressionTraceNode left = ExpressionTraceNode.comparison(
                    ">", "amount", 1500, "1000", 1000, true);
            ExpressionTraceNode right = ExpressionTraceNode.comparison(
                    ">", "score", 750, "800", 800, false);

            ExpressionEvaluator.TraceResult trace = logicalTrace("&&", false, false, left, right);

            RuleAttributionService service = new RuleAttributionService(mockAdminService, null);
            AttributionReport report = service.analyze(trace, "R1", "复合规则");

            assertThat(report.isTriggered()).isFalse();
            assertThat(report.getFactors()).hasSize(2);
            // 左条件满足
            assertThat(report.getFactors().get(0).getVariable()).isEqualTo("amount");
            assertThat(report.getFactors().get(0).isSatisfied()).isTrue();
            // 右条件不满足
            assertThat(report.getFactors().get(1).getVariable()).isEqualTo("score");
            assertThat(report.getFactors().get(1).isSatisfied()).isFalse();
            // 摘要应包含"但"和"不满足"
            assertThat(report.getSummary()).contains("amount=1500");
            assertThat(report.getSummary()).contains("score=750");
            assertThat(report.getSummary()).contains("不满足");
            assertThat(report.getSummary()).contains("AND 条件不成立");
        }

        @Test
        @DisplayName("左右均满足 → 触发")
        void shouldAttributeAndAllSatisfied() {
            ExpressionTraceNode left = ExpressionTraceNode.comparison(
                    ">", "amount", 1500, "1000", 1000, true);
            ExpressionTraceNode right = ExpressionTraceNode.comparison(
                    ">", "score", 900, "800", 800, true);

            ExpressionEvaluator.TraceResult trace = logicalTrace("&&", true, false, left, right);

            RuleAttributionService service = new RuleAttributionService(mockAdminService, null);
            AttributionReport report = service.analyze(trace, "R1", "复合规则");

            assertThat(report.isTriggered()).isTrue();
            assertThat(report.getFactors()).hasSize(2);
            assertThat(report.getFactors().get(0).isSatisfied()).isTrue();
            assertThat(report.getFactors().get(1).isSatisfied()).isTrue();
            assertThat(report.getSummary()).contains("触发");
        }
    }

    // ==================== OR 逻辑归因 ====================

    @Nested
    @DisplayName("OR 逻辑归因")
    class OrLogicTest {

        @Test
        @DisplayName("左满足短路 → 触发，右条件被短路跳过")
        void shouldAttributeOrShortCircuit() {
            ExpressionTraceNode left = ExpressionTraceNode.comparison(
                    ">", "amount", 1500, "1000", 1000, true);
            ExpressionTraceNode right = shortCircuitedNode("score > 800");

            ExpressionEvaluator.TraceResult trace = logicalTrace("||", true, true, left, right);

            RuleAttributionService service = new RuleAttributionService(mockAdminService, null);
            AttributionReport report = service.analyze(trace, "R1", "OR 规则");

            assertThat(report.isTriggered()).isTrue();
            assertThat(report.getFactors()).hasSize(2);
            // 左条件满足
            assertThat(report.getFactors().get(0).getVariable()).isEqualTo("amount");
            assertThat(report.getFactors().get(0).isSatisfied()).isTrue();
            // 右条件被短路
            assertThat(report.getFactors().get(1).isShortCircuited()).isTrue();
            assertThat(report.getSummary()).contains("触发");
        }

        @Test
        @DisplayName("左右均不满足 → 未触发")
        void shouldAttributeOrAllUnsatisfied() {
            ExpressionTraceNode left = ExpressionTraceNode.comparison(
                    ">", "amount", 500, "1000", 1000, false);
            ExpressionTraceNode right = ExpressionTraceNode.comparison(
                    ">", "score", 700, "800", 800, false);

            ExpressionEvaluator.TraceResult trace = logicalTrace("||", false, false, left, right);

            RuleAttributionService service = new RuleAttributionService(mockAdminService, null);
            AttributionReport report = service.analyze(trace, "R1", "OR 规则");

            assertThat(report.isTriggered()).isFalse();
            assertThat(report.getFactors()).hasSize(2);
            assertThat(report.getFactors().get(0).isSatisfied()).isFalse();
            assertThat(report.getFactors().get(1).isSatisfied()).isFalse();
            assertThat(report.getSummary()).contains("OR 条件均不成立");
        }
    }

    // ==================== 嵌套表达式归因 ====================

    @Nested
    @DisplayName("嵌套表达式归因")
    class NestedExpressionTest {

        @Test
        @DisplayName("(a > 1 && b > 2) || c > 3 → 触发，提取 3 个因子")
        void shouldAttributeNestedExpression() {
            // (a > 1 && b > 2) || c > 3 = (true && false) || true = true
            ExpressionTraceNode aNode = ExpressionTraceNode.comparison(
                    ">", "a", 5, "1", 1, true);
            ExpressionTraceNode bNode = ExpressionTraceNode.comparison(
                    ">", "b", 1, "2", 2, false);
            ExpressionTraceNode andNode = ExpressionTraceNode.builder()
                    .nodeType(ExpressionTraceNode.NodeType.LOGICAL)
                    .operator("&&")
                    .expression("a > 1 && b > 2")
                    .result(false)
                    .shortCircuited(false)
                    .children(List.of(aNode, bNode))
                    .build();
            ExpressionTraceNode cNode = ExpressionTraceNode.comparison(
                    ">", "c", 10, "3", 3, true);

            ExpressionTraceNode root = ExpressionTraceNode.builder()
                    .nodeType(ExpressionTraceNode.NodeType.LOGICAL)
                    .operator("||")
                    .expression("a > 1 && b > 2 || c > 3")
                    .result(true)
                    .shortCircuited(false)
                    .children(List.of(andNode, cNode))
                    .build();
            ExpressionEvaluator.TraceResult trace = new ExpressionEvaluator.TraceResult(true, root);

            RuleAttributionService service = new RuleAttributionService(mockAdminService, null);
            AttributionReport report = service.analyze(trace, "R1", "嵌套规则");

            assertThat(report.isTriggered()).isTrue();
            // 3 个比较因子：a, b, c
            assertThat(report.getFactors()).hasSize(3);
            assertThat(report.getFactors()).extracting(AttributionReport.AttributionFactor::getVariable)
                    .containsExactly("a", "b", "c");
            assertThat(report.getFactors().get(0).isSatisfied()).isTrue();  // a > 1
            assertThat(report.getFactors().get(1).isSatisfied()).isFalse(); // b > 2
            assertThat(report.getFactors().get(2).isSatisfied()).isTrue();  // c > 3
        }
    }

    // ==================== LLM 增强 ====================

    @Nested
    @DisplayName("LLM 增强归因")
    class LlmEnhancementTest {

        @Test
        @DisplayName("LLM 可用时生成 llmAnalysis 和 recommendation")
        void shouldGenerateLlmAnalysisWhenAvailable() {
            ExpressionEvaluator.TraceResult trace = simpleComparison(
                    "amount", 1500, ">", 1000, true);

            when(mockLlmClient.chat(anyString(), anyString(), any()))
                    .thenReturn("{\"analysis\": \"金额超过阈值触发规则\", \"recommendation\": \"建议调高阈值至 2000\"}");

            RuleAttributionService service = new RuleAttributionService(mockAdminService, mockLlmClient);
            AttributionReport report = service.analyze(trace, "R1", "金额规则");

            assertThat(report.getLlmAnalysis()).isEqualTo("金额超过阈值触发规则");
            assertThat(report.getRecommendation()).isEqualTo("建议调高阈值至 2000");
            assertThat(report.getSummary()).isNotNull();
            verify(mockLlmClient).chat(anyString(), anyString(), any());
        }

        @Test
        @DisplayName("LLM 不可用时 llmAnalysis=null，仍返回基础归因")
        void shouldReturnBasicAttributionWhenLlmUnavailable() {
            ExpressionEvaluator.TraceResult trace = simpleComparison(
                    "amount", 1500, ">", 1000, true);

            RuleAttributionService service = new RuleAttributionService(mockAdminService, null);
            AttributionReport report = service.analyze(trace, "R1", "金额规则");

            assertThat(report.getLlmAnalysis()).isNull();
            assertThat(report.getRecommendation()).isNull();
            assertThat(report.getSummary()).isNotNull();
            assertThat(report.getFactors()).hasSize(1);
        }

        @Test
        @DisplayName("LLM 调用异常时降级返回基础归因")
        void shouldFallbackWhenLlmThrows() {
            ExpressionEvaluator.TraceResult trace = simpleComparison(
                    "amount", 1500, ">", 1000, true);

            when(mockLlmClient.chat(anyString(), anyString(), any()))
                    .thenThrow(new LLMException("test-provider", "LLM 服务不可用"));

            RuleAttributionService service = new RuleAttributionService(mockAdminService, mockLlmClient);
            AttributionReport report = service.analyze(trace, "R1", "金额规则");

            // LLM 异常被吞掉，基础归因仍可用
            assertThat(report.getLlmAnalysis()).isNull();
            assertThat(report.getRecommendation()).isNull();
            assertThat(report.getSummary()).isNotNull();
            assertThat(report.getFactors()).hasSize(1);
        }
    }

    // ==================== 按规则编码归因 ====================

    @Nested
    @DisplayName("按规则编码归因")
    class RuleCodeAttributionTest {

        @Test
        @DisplayName("规则存在时调用 traceExpression 归因")
        void shouldAnalyzeByRuleCode() {
            RuleDefinition def = buildRule("R1", "金额规则", "amount > 1000");
            when(mockAdminService.getByCode("R1")).thenReturn(def);

            ExpressionEvaluator.TraceResult trace = simpleComparison(
                    "amount", 1500, ">", 1000, true);
            when(mockAdminService.traceExpression(eq("amount > 1000"), anyMap())).thenReturn(trace);

            Map<String, Object> facts = new HashMap<>();
            facts.put("amount", 1500);

            RuleAttributionService service = new RuleAttributionService(mockAdminService, null);
            AttributionReport report = service.analyze("R1", facts);

            assertThat(report.getRuleCode()).isEqualTo("R1");
            assertThat(report.getRuleName()).isEqualTo("金额规则");
            assertThat(report.isTriggered()).isTrue();
            assertThat(report.getSeverity()).isEqualTo("YELLOW");
            verify(mockAdminService).getByCode("R1");
            verify(mockAdminService).traceExpression(eq("amount > 1000"), eq(facts));
        }

        @Test
        @DisplayName("规则不存在时返回错误报告")
        void shouldReturnErrorWhenRuleNotFound() {
            when(mockAdminService.getByCode("NON_EXISTENT")).thenReturn(null);

            RuleAttributionService service = new RuleAttributionService(mockAdminService, null);
            AttributionReport report = service.analyze("NON_EXISTENT", new HashMap<>());

            assertThat(report.isTriggered()).isFalse();
            assertThat(report.getSummary()).contains("规则不存在");
            assertThat(report.getFactors()).isEmpty();
            verify(mockAdminService, never()).traceExpression(anyString(), anyMap());
        }

        @Test
        @DisplayName("ruleCode 为空时返回错误报告")
        void shouldReturnErrorWhenRuleCodeBlank() {
            RuleAttributionService service = new RuleAttributionService(mockAdminService, null);
            AttributionReport report = service.analyze("", new HashMap<>());

            assertThat(report.isTriggered()).isFalse();
            assertThat(report.getSummary()).contains("不能为空");
            verify(mockAdminService, never()).getByCode(anyString());
        }
    }

    // ==================== 批量归因 ====================

    @Nested
    @DisplayName("批量归因")
    class BatchAttributionTest {

        @Test
        @DisplayName("批量归因多条轨迹")
        void shouldAnalyzeBatch() {
            RuleDefinition def1 = buildRule("R1", "规则1", "amount > 1000");
            RuleDefinition def2 = buildRule("R2", "规则2", "score > 800");
            when(mockAdminService.getByCode("R1")).thenReturn(def1);
            when(mockAdminService.getByCode("R2")).thenReturn(def2);

            when(mockAdminService.traceExpression(eq("amount > 1000"), anyMap()))
                    .thenReturn(simpleComparison("amount", 1500, ">", 1000, true));
            when(mockAdminService.traceExpression(eq("score > 800"), anyMap()))
                    .thenReturn(simpleComparison("score", 700, ">", 800, false));

            Map<String, Object> facts1 = new HashMap<>();
            facts1.put("amount", 1500);
            Map<String, Object> facts2 = new HashMap<>();
            facts2.put("score", 700);

            RuleExecutionTrace trace1 = new RuleExecutionTrace("t1", "R1", "规则1", "S",
                    true, "YELLOW", "amount > 1000", 5, facts1, null, null);
            RuleExecutionTrace trace2 = new RuleExecutionTrace("t2", "R2", "规则2", "S",
                    false, null, "score > 800", 3, facts2, null, null);

            RuleAttributionService service = new RuleAttributionService(mockAdminService, null);
            List<AttributionReport> reports = service.analyzeBatch(List.of(trace1, trace2));

            assertThat(reports).hasSize(2);
            assertThat(reports.get(0).getRuleCode()).isEqualTo("R1");
            assertThat(reports.get(0).isTriggered()).isTrue();
            assertThat(reports.get(1).getRuleCode()).isEqualTo("R2");
            assertThat(reports.get(1).isTriggered()).isFalse();
        }

        @Test
        @DisplayName("空列表返回空结果")
        void shouldReturnEmptyForEmptyList() {
            RuleAttributionService service = new RuleAttributionService(mockAdminService, null);
            List<AttributionReport> reports = service.analyzeBatch(List.of());
            assertThat(reports).isEmpty();
        }

        @Test
        @DisplayName("null 列表返回空结果")
        void shouldReturnEmptyForNullList() {
            RuleAttributionService service = new RuleAttributionService(mockAdminService, null);
            List<AttributionReport> reports = service.analyzeBatch(null);
            assertThat(reports).isEmpty();
        }
    }

    // ==================== 边界场景 ====================

    @Nested
    @DisplayName("边界场景")
    class EdgeCaseTest {

        @Test
        @DisplayName("空表达式归因 - 返回错误摘要")
        void shouldHandleEmptyExpression() {
            ExpressionTraceNode root = ExpressionTraceNode.builder()
                    .nodeType(ExpressionTraceNode.NodeType.ROOT)
                    .expression("")
                    .result(false)
                    .error("表达式为空")
                    .build();
            ExpressionEvaluator.TraceResult trace = new ExpressionEvaluator.TraceResult(false, root);

            RuleAttributionService service = new RuleAttributionService(mockAdminService, null);
            AttributionReport report = service.analyze(trace, "R1", "空规则");

            assertThat(report.isTriggered()).isFalse();
            assertThat(report.getSummary()).contains("表达式为空");
            assertThat(report.getFactors()).isEmpty();
            assertThat(report.getLlmAnalysis()).isNull();
        }

        @Test
        @DisplayName("变量不存在归因 - variableValue=null")
        void shouldHandleNonExistentVariable() {
            ExpressionTraceNode root = ExpressionTraceNode.comparison(
                    ">", "nonexistent", null, "1000", 1000, false);
            ExpressionEvaluator.TraceResult trace = new ExpressionEvaluator.TraceResult(false, root);

            RuleAttributionService service = new RuleAttributionService(mockAdminService, null);
            AttributionReport report = service.analyze(trace, "R1", "变量规则");

            assertThat(report.isTriggered()).isFalse();
            assertThat(report.getFactors()).hasSize(1);
            AttributionReport.AttributionFactor f = report.getFactors().get(0);
            assertThat(f.getVariable()).isEqualTo("nonexistent");
            assertThat(f.getCurrentValue()).isNull();
            assertThat(f.isSatisfied()).isFalse();
        }

        @Test
        @DisplayName("null TraceResult 返回错误报告")
        void shouldHandleNullTraceResult() {
            RuleAttributionService service = new RuleAttributionService(mockAdminService, null);
            AttributionReport report = service.analyze(null, "R1", "规则");

            assertThat(report.isTriggered()).isFalse();
            assertThat(report.getSummary()).contains("追踪结果为空");
        }

        @Test
        @DisplayName("analyzedAt 已填充")
        void shouldPopulateAnalyzedAt() {
            ExpressionEvaluator.TraceResult trace = simpleComparison(
                    "amount", 1500, ">", 1000, true);

            RuleAttributionService service = new RuleAttributionService(mockAdminService, null);
            AttributionReport report = service.analyze(trace, "R1", "规则");

            assertThat(report.getAnalyzedAt()).isNotNull();
        }
    }

    // ==================== summary 生成 ====================

    @Nested
    @DisplayName("summary 生成")
    class SummaryTest {

        @Test
        @DisplayName("触发时 summary 包含变量值和'触发'")
        void shouldGenerateTriggeredSummary() {
            ExpressionEvaluator.TraceResult trace = simpleComparison(
                    "amount", 1500, ">", 1000, true);

            RuleAttributionService service = new RuleAttributionService(mockAdminService, null);
            AttributionReport report = service.analyze(trace, "R1", "规则");

            assertThat(report.getSummary())
                    .contains("amount=1500")
                    .contains("1000")
                    .contains("满足")
                    .contains("触发");
        }

        @Test
        @DisplayName("未触发时 summary 包含'不满足'和'未触发'")
        void shouldGenerateNotTriggeredSummary() {
            ExpressionEvaluator.TraceResult trace = simpleComparison(
                    "amount", 500, ">", 1000, false);

            RuleAttributionService service = new RuleAttributionService(mockAdminService, null);
            AttributionReport report = service.analyze(trace, "R1", "规则");

            assertThat(report.getSummary())
                    .contains("amount=500")
                    .contains("不满足")
                    .contains("未触发");
        }

        @Test
        @DisplayName("AND 未触发时 summary 包含'AND 条件不成立'")
        void shouldGenerateAndFailureSummary() {
            ExpressionTraceNode left = ExpressionTraceNode.comparison(
                    ">", "amount", 1500, "1000", 1000, true);
            ExpressionTraceNode right = ExpressionTraceNode.comparison(
                    ">", "score", 750, "800", 800, false);
            ExpressionEvaluator.TraceResult trace = logicalTrace("&&", false, false, left, right);

            RuleAttributionService service = new RuleAttributionService(mockAdminService, null);
            AttributionReport report = service.analyze(trace, "R1", "规则");

            assertThat(report.getSummary()).contains("AND 条件不成立");
        }
    }
}
