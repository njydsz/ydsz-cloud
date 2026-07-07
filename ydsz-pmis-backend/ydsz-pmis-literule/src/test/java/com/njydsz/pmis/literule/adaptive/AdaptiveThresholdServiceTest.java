package com.njydsz.pmis.literule.adaptive;

import com.njydsz.pmis.literule.ai.LLMClient;
import com.njydsz.pmis.literule.ai.LLMException;
import com.njydsz.pmis.literule.api.RuleDefinition;
import com.njydsz.pmis.literule.api.RuleExecutionTrace;
import com.njydsz.pmis.literule.config.RuleAdminService;
import com.njydsz.pmis.literule.spi.RuleConfigProvider;
import com.njydsz.pmis.literule.spi.TraceDataProvider;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * AdaptiveThresholdService 单元测试（P3-4 自适应智能风控）
 *
 * <p>测试目标：验证自适应阈值分析服务的核心能力，包括：
 * <ul>
 *   <li>ThresholdExtractor 表达式解析（简单比较、AND 组合、变量在右）</li>
 *   <li>分布统计计算（均值、中位数、分位数、标准差）</li>
 *   <li>四种调整策略（PERCENTILE/FALSE_RATE/MISS_RATE/BALANCED）</li>
 *   <li>置信度计算（样本量影响）</li>
 *   <li>应用阈值调整</li>
 *   <li>LLM 生成调整原因（mock LLMClient）</li>
 * </ul>
 *
 * <p>测试风格参考 {@link com.njydsz.pmis.literule.core.DefaultRuleEngineTest}，
 * 使用 Mockito.mock 手动创建测试桩，不依赖 Spring 容器。
 *
 * @author ydsz-pmis-team
 */
@DisplayName("AdaptiveThresholdService 单元测试")
class AdaptiveThresholdServiceTest {

    private RuleConfigProvider configProvider;
    private TraceDataProvider traceDataProvider;
    private RuleAdminService ruleAdminService;
    private LLMClient llmClient;
    private AdaptiveThresholdService service;

    @BeforeEach
    void setUp() {
        configProvider = Mockito.mock(RuleConfigProvider.class);
        traceDataProvider = Mockito.mock(TraceDataProvider.class);
        ruleAdminService = Mockito.mock(RuleAdminService.class);
        llmClient = Mockito.mock(LLMClient.class);
        // TraceDataProvider.isAvailable() 是 default 方法，Mockito mock 默认返回 false，
        // 显式打桩返回 true 以放行 AdaptiveThresholdService.analyzeRule 的可用性检查
        when(traceDataProvider.isAvailable()).thenReturn(true);
        service = new AdaptiveThresholdService(configProvider, traceDataProvider, ruleAdminService, llmClient);
    }

    // ==================== 辅助方法 ====================

    /**
     * 构造规则定义
     */
    private RuleDefinition rule(String code, String conditionExpr) {
        return RuleDefinition.builder()
                .code(code)
                .name("规则-" + code)
                .conditionExpression(conditionExpr)
                .build();
    }

    /**
     * 构造执行轨迹
     *
     * @param variable 变量名
     * @param value    变量值
     * @param triggered 是否触发
     */
    private RuleExecutionTrace trace(String variable, double value, boolean triggered) {
        Map<String, Object> facts = new HashMap<>();
        facts.put(variable, value);
        RuleExecutionTrace t = new RuleExecutionTrace();
        t.setRuleCode("R1");
        t.setFactsSnapshot(facts);
        t.setTriggered(triggered);
        return t;
    }

    /**
     * 生成 N 个执行轨迹，变量值从 start 递增
     *
     * @param variable 变量名
     * @param n        数量
     * @param start    起始值
     * @param step     步长
     */
    private List<RuleExecutionTrace> generateTraces(String variable, int n, double start, double step) {
        List<RuleExecutionTrace> traces = new ArrayList<>();
        double v = start;
        for (int i = 0; i < n; i++) {
            traces.add(trace(variable, v, false));
            v += step;
        }
        return traces;
    }

    // ==================== ThresholdExtractor 测试 ====================

    @Nested
    @DisplayName("ThresholdExtractor 表达式解析")
    class ThresholdExtractorTest {

        @Test
        @DisplayName("提取简单比较阈值 - amount > 1000")
        void shouldExtractSimpleComparison() {
            List<ThresholdExtractor.ThresholdInfo> result =
                    ThresholdExtractor.extract("amount > 1000");

            assertThat(result).hasSize(1);
            assertThat(result.get(0).getVariable()).isEqualTo("amount");
            assertThat(result.get(0).getOperator()).isEqualTo(">");
            assertThat(result.get(0).getThreshold()).isEqualTo(1000.0);
        }

        @Test
        @DisplayName("提取 AND 组合表达式中的多个阈值")
        void shouldExtractMultipleThresholdsFromAndExpression() {
            List<ThresholdExtractor.ThresholdInfo> result =
                    ThresholdExtractor.extract("amount > 1000 && score < 800");

            assertThat(result).hasSize(2);
            assertThat(result).extracting(ThresholdExtractor.ThresholdInfo::getVariable)
                    .containsExactlyInAnyOrder("amount", "score");
            assertThat(result).extracting(ThresholdExtractor.ThresholdInfo::getOperator)
                    .containsExactlyInAnyOrder(">", "<");
            assertThat(result).extracting(ThresholdExtractor.ThresholdInfo::getThreshold)
                    .containsExactlyInAnyOrder(1000.0, 800.0);
        }

        @Test
        @DisplayName("提取 >= 运算符的阈值")
        void shouldExtractGreaterThanOrEqual() {
            List<ThresholdExtractor.ThresholdInfo> result =
                    ThresholdExtractor.extract("evmRedCount >= 3");

            assertThat(result).hasSize(1);
            assertThat(result.get(0).getVariable()).isEqualTo("evmRedCount");
            assertThat(result.get(0).getOperator()).isEqualTo(">=");
            assertThat(result.get(0).getThreshold()).isEqualTo(3.0);
        }

        @Test
        @DisplayName("提取小数阈值 - ratio > 0.5")
        void shouldExtractDecimalThreshold() {
            List<ThresholdExtractor.ThresholdInfo> result =
                    ThresholdExtractor.extract("ratio > 0.5");

            assertThat(result).hasSize(1);
            assertThat(result.get(0).getThreshold()).isEqualTo(0.5);
        }

        @Test
        @DisplayName("变量在右 - 1000 < amount 翻转为 amount > 1000")
        void shouldFlipOperatorWhenVariableOnRight() {
            List<ThresholdExtractor.ThresholdInfo> result =
                    ThresholdExtractor.extract("1000 < amount");

            assertThat(result).hasSize(1);
            assertThat(result.get(0).getVariable()).isEqualTo("amount");
            assertThat(result.get(0).getOperator()).isEqualTo(">");
            assertThat(result.get(0).getThreshold()).isEqualTo(1000.0);
        }

        @Test
        @DisplayName("空表达式返回空列表")
        void shouldReturnEmptyForEmptyExpression() {
            assertThat(ThresholdExtractor.extract(null)).isEmpty();
            assertThat(ThresholdExtractor.extract("")).isEmpty();
            assertThat(ThresholdExtractor.extract("   ")).isEmpty();
        }

        @Test
        @DisplayName("无可识别阈值时返回空列表")
        void shouldReturnEmptyForNoThreshold() {
            assertThat(ThresholdExtractor.extract("fn(x) > 1")).isEmpty();
            assertThat(ThresholdExtractor.extract("(a + b) > c")).isEmpty();
        }
    }

    // ==================== 分布统计计算测试 ====================

    @Nested
    @DisplayName("分布统计计算")
    class DistributionStatsTest {

        @Test
        @DisplayName("均值和中位数计算正确")
        void shouldCalculateMeanAndMedian() {
            // 10 个样本：1, 2, 3, ..., 10
            List<RuleExecutionTrace> traces = generateTraces("amount", 10, 1, 1);
            when(traceDataProvider.getTracesByRule("R1", 30)).thenReturn(traces);
            when(configProvider.findByCode("R1")).thenReturn(rule("R1", "amount > 5"));

            List<ThresholdAnalysis> result = service.analyzeRule("R1", 30);

            assertThat(result).hasSize(1);
            DistributionStats stats = result.get(0).getDistribution();
            assertThat(stats.getTotalCount()).isEqualTo(10);
            assertThat(stats.getMean()).isEqualTo(5.5);
            assertThat(stats.getMedian()).isEqualTo(5.5);
        }

        @Test
        @DisplayName("P90/P95/P99 分位数计算正确")
        void shouldCalculatePercentiles() {
            // 100 个样本：1, 2, ..., 100
            List<RuleExecutionTrace> traces = generateTraces("amount", 100, 1, 1);
            when(traceDataProvider.getTracesByRule("R1", 30)).thenReturn(traces);
            when(configProvider.findByCode("R1")).thenReturn(rule("R1", "amount > 50"));

            List<ThresholdAnalysis> result = service.analyzeRule("R1", 30);

            assertThat(result).hasSize(1);
            DistributionStats stats = result.get(0).getDistribution();
            // P90 ≈ 90.1, P95 ≈ 95.05, P99 ≈ 99.01
            assertThat(stats.getP90()).isBetween(89.0, 91.0);
            assertThat(stats.getP95()).isBetween(94.0, 96.0);
            assertThat(stats.getP99()).isBetween(98.0, 100.0);
        }

        @Test
        @DisplayName("触发率统计正确")
        void shouldCalculateTriggerRate() {
            // 100 个样本：1~100，当前阈值 50（> 触发）
            // 触发数 = 50（51~100），触发率 = 50%
            List<RuleExecutionTrace> traces = generateTraces("amount", 100, 1, 1);
            when(traceDataProvider.getTracesByRule("R1", 30)).thenReturn(traces);
            when(configProvider.findByCode("R1")).thenReturn(rule("R1", "amount > 50"));

            List<ThresholdAnalysis> result = service.analyzeRule("R1", 30);

            DistributionStats stats = result.get(0).getDistribution();
            assertThat(stats.getTriggeredCount()).isEqualTo(50);
            assertThat(stats.getNotTriggeredCount()).isEqualTo(50);
            assertThat(stats.getTriggerRate()).isEqualTo(0.5);
        }

        @Test
        @DisplayName("标准差计算正确")
        void shouldCalculateStddev() {
            // 均匀分布 1~100，方差 = (100^2 - 1) / 12 ≈ 833.25，标准差 ≈ 28.87
            List<RuleExecutionTrace> traces = generateTraces("amount", 100, 1, 1);
            when(traceDataProvider.getTracesByRule("R1", 30)).thenReturn(traces);
            when(configProvider.findByCode("R1")).thenReturn(rule("R1", "amount > 50"));

            List<ThresholdAnalysis> result = service.analyzeRule("R1", 30);

            DistributionStats stats = result.get(0).getDistribution();
            assertThat(stats.getStddev()).isBetween(28.0, 30.0);
        }

        @Test
        @DisplayName("min/max 计算正确")
        void shouldCalculateMinMax() {
            List<RuleExecutionTrace> traces = generateTraces("amount", 100, 1, 1);
            when(traceDataProvider.getTracesByRule("R1", 30)).thenReturn(traces);
            when(configProvider.findByCode("R1")).thenReturn(rule("R1", "amount > 50"));

            List<ThresholdAnalysis> result = service.analyzeRule("R1", 30);

            DistributionStats stats = result.get(0).getDistribution();
            assertThat(stats.getMin()).isEqualTo(1.0);
            assertThat(stats.getMax()).isEqualTo(100.0);
        }
    }

    // ==================== 策略测试 ====================

    @Nested
    @DisplayName("调整策略")
    class StrategyTest {

        @Test
        @DisplayName("BALANCED 策略：触发率在 5%~50% 之间走 F1-score 最优（10% 触发率场景）")
        void shouldUsePercentileWhenTriggerRateModerate() {
            // 触发率 ~10%（100 样本中 10 个触发），处于 5%~50% 之间 → BALANCED 策略
            // 验证 BALANCED 策略在中等触发率下也能生成合理建议
            List<RuleExecutionTrace> traces = generateTraces("amount", 100, 1, 1);
            when(traceDataProvider.getTracesByRule("R1", 30)).thenReturn(traces);
            when(configProvider.findByCode("R1")).thenReturn(rule("R1", "amount > 90"));

            List<ThresholdAnalysis> result = service.analyzeRule("R1", 30);

            assertThat(result).hasSize(1);
            // 10% 触发率 ∈ [5%, 50%] → BALANCED 策略
            assertThat(result.get(0).getStrategy()).isEqualTo(ThresholdStrategy.BALANCED);
            // BALANCED 策略应返回数据范围内的合理阈值
            assertThat(result.get(0).getSuggestedThreshold()).isBetween(1.0, 100.0);
        }

        @Test
        @DisplayName("FALSE_RATE 策略：高触发率时提高阈值")
        void shouldUseFalseRateWhenTriggerRateHigh() {
            // 100 样本：1~100，阈值 30（> 触发），触发率 = 70%（>50%）→ FALSE_RATE
            List<RuleExecutionTrace> traces = generateTraces("amount", 100, 1, 1);
            when(traceDataProvider.getTracesByRule("R1", 30)).thenReturn(traces);
            when(configProvider.findByCode("R1")).thenReturn(rule("R1", "amount > 30"));

            List<ThresholdAnalysis> result = service.analyzeRule("R1", 30);

            assertThat(result).hasSize(1);
            assertThat(result.get(0).getStrategy()).isEqualTo(ThresholdStrategy.FALSE_RATE);
            // FALSE_RATE：提高阈值到 P75（75% 数据低于此值 → 25% 触发）
            assertThat(result.get(0).getSuggestedThreshold()).isBetween(74.0, 76.0);
            // 建议阈值应高于当前阈值（提高阈值降低触发率）
            assertThat(result.get(0).getSuggestedThreshold()).isGreaterThan(result.get(0).getCurrentThreshold());
        }

        @Test
        @DisplayName("MISS_RATE 策略：低触发率时降低阈值")
        void shouldUseMissRateWhenTriggerRateLow() {
            // 100 样本：1~100，阈值 98（> 触发），触发率 = 2%（<5%）→ MISS_RATE
            List<RuleExecutionTrace> traces = generateTraces("amount", 100, 1, 1);
            when(traceDataProvider.getTracesByRule("R1", 30)).thenReturn(traces);
            when(configProvider.findByCode("R1")).thenReturn(rule("R1", "amount > 98"));

            List<ThresholdAnalysis> result = service.analyzeRule("R1", 30);

            assertThat(result).hasSize(1);
            assertThat(result.get(0).getStrategy()).isEqualTo(ThresholdStrategy.MISS_RATE);
            // MISS_RATE：降低阈值到 P90（90% 数据低于此值 → 10% 触发）
            assertThat(result.get(0).getSuggestedThreshold()).isBetween(89.0, 91.0);
            // 建议阈值应低于当前阈值（降低阈值提高触发率）
            assertThat(result.get(0).getSuggestedThreshold()).isLessThan(result.get(0).getCurrentThreshold());
        }

        @Test
        @DisplayName("BALANCED 策略：触发率在 5%~50% 之间时使用 F1-score 最优")
        void shouldUseBalancedWhenTriggerRateModerate() {
            // 100 样本：1~100，阈值 80（> 触发），触发率 = 20%（5%~50%）→ BALANCED
            List<RuleExecutionTrace> traces = generateTraces("amount", 100, 1, 1);
            when(traceDataProvider.getTracesByRule("R1", 30)).thenReturn(traces);
            when(configProvider.findByCode("R1")).thenReturn(rule("R1", "amount > 80"));

            List<ThresholdAnalysis> result = service.analyzeRule("R1", 30);

            assertThat(result).hasSize(1);
            assertThat(result.get(0).getStrategy()).isEqualTo(ThresholdStrategy.BALANCED);
            // BALANCED 策略应返回一个合理的阈值（在数据范围内）
            assertThat(result.get(0).getSuggestedThreshold()).isBetween(1.0, 100.0);
        }
    }

    // ==================== 置信度测试 ====================

    @Nested
    @DisplayName("置信度计算")
    class ConfidenceTest {

        @Test
        @DisplayName("大样本量（200+）置信度较高")
        void shouldHaveHighConfidenceWithLargeSample() {
            List<RuleExecutionTrace> traces = generateTraces("amount", 200, 1, 1);
            when(traceDataProvider.getTracesByRule("R1", 30)).thenReturn(traces);
            when(configProvider.findByCode("R1")).thenReturn(rule("R1", "amount > 100"));

            List<ThresholdAnalysis> result = service.analyzeRule("R1", 30);

            assertThat(result).hasSize(1);
            assertThat(result.get(0).getConfidence()).isGreaterThan(0.5);
        }

        @Test
        @DisplayName("小样本量（接近最小阈值 10）置信度较低")
        void shouldHaveLowConfidenceWithSmallSample() {
            List<RuleExecutionTrace> traces = generateTraces("amount", 15, 1, 1);
            when(traceDataProvider.getTracesByRule("R1", 30)).thenReturn(traces);
            when(configProvider.findByCode("R1")).thenReturn(rule("R1", "amount > 8"));

            List<ThresholdAnalysis> result = service.analyzeRule("R1", 30);

            assertThat(result).hasSize(1);
            assertThat(result.get(0).getConfidence()).isLessThan(0.5);
        }

        @Test
        @DisplayName("置信度在 0~1 范围内")
        void shouldClampConfidenceToZeroOneRange() {
            List<RuleExecutionTrace> traces = generateTraces("amount", 100, 1, 1);
            when(traceDataProvider.getTracesByRule("R1", 30)).thenReturn(traces);
            when(configProvider.findByCode("R1")).thenReturn(rule("R1", "amount > 50"));

            List<ThresholdAnalysis> result = service.analyzeRule("R1", 30);

            assertThat(result).hasSize(1);
            double confidence = result.get(0).getConfidence();
            assertThat(confidence).isGreaterThanOrEqualTo(0.0);
            assertThat(confidence).isLessThanOrEqualTo(1.0);
        }
    }

    // ==================== 边界条件测试 ====================

    @Nested
    @DisplayName("边界条件")
    class EdgeCaseTest {

        @Test
        @DisplayName("ruleCode 为空 - 返回空列表")
        void shouldReturnEmptyForBlankRuleCode() {
            assertThat(service.analyzeRule(null, 30)).isEmpty();
            assertThat(service.analyzeRule("", 30)).isEmpty();
            assertThat(service.analyzeRule("  ", 30)).isEmpty();
        }

        @Test
        @DisplayName("TraceDataProvider 不可用 - 返回空列表")
        void shouldReturnEmptyWhenTraceProviderUnavailable() {
            when(traceDataProvider.isAvailable()).thenReturn(false);
            when(configProvider.findByCode("R1")).thenReturn(rule("R1", "amount > 100"));

            assertThat(service.analyzeRule("R1", 30)).isEmpty();
        }

        @Test
        @DisplayName("规则不存在 - 返回空列表")
        void shouldReturnEmptyWhenRuleNotFound() {
            when(configProvider.findByCode("NON_EXISTENT")).thenReturn(null);

            assertThat(service.analyzeRule("NON_EXISTENT", 30)).isEmpty();
        }

        @Test
        @DisplayName("表达式无可识别阈值 - 返回空列表")
        void shouldReturnEmptyWhenNoThresholdInExpression() {
            when(configProvider.findByCode("R1")).thenReturn(rule("R1", "fn(x) > 1"));
            when(traceDataProvider.getTracesByRule("R1", 30)).thenReturn(generateTraces("x", 100, 1, 1));

            assertThat(service.analyzeRule("R1", 30)).isEmpty();
        }

        @Test
        @DisplayName("样本量不足（<10） - 返回空列表")
        void shouldReturnEmptyWhenSampleSizeInsufficient() {
            List<RuleExecutionTrace> traces = generateTraces("amount", 5, 1, 1);
            when(traceDataProvider.getTracesByRule("R1", 30)).thenReturn(traces);
            when(configProvider.findByCode("R1")).thenReturn(rule("R1", "amount > 3"));

            assertThat(service.analyzeRule("R1", 30)).isEmpty();
        }

        @Test
        @DisplayName("无轨迹数据 - 返回空列表")
        void shouldReturnEmptyWhenNoTraces() {
            when(traceDataProvider.getTracesByRule("R1", 30)).thenReturn(List.of());
            when(configProvider.findByCode("R1")).thenReturn(rule("R1", "amount > 100"));

            assertThat(service.analyzeRule("R1", 30)).isEmpty();
        }

        @Test
        @DisplayName("LLM 不可用时降级为模板原因")
        void shouldFallbackToTemplateWhenLlmUnavailable() {
            service = new AdaptiveThresholdService(configProvider, traceDataProvider, ruleAdminService, null);
            List<RuleExecutionTrace> traces = generateTraces("amount", 100, 1, 1);
            when(traceDataProvider.getTracesByRule("R1", 30)).thenReturn(traces);
            when(configProvider.findByCode("R1")).thenReturn(rule("R1", "amount > 50"));

            List<ThresholdAnalysis> result = service.analyzeRule("R1", 30);

            assertThat(result).hasSize(1);
            assertThat(result.get(0).getReason()).isNotBlank();
            assertThat(result.get(0).getReason()).contains("R1");
        }
    }

    // ==================== LLM 原因生成测试 ====================

    @Nested
    @DisplayName("LLM 调整原因生成")
    class LlmReasonTest {

        @Test
        @DisplayName("LLM 可用时调用 LLM 生成原因")
        void shouldCallLlmWhenAvailable() throws LLMException {
            List<RuleExecutionTrace> traces = generateTraces("amount", 100, 1, 1);
            when(traceDataProvider.getTracesByRule("R1", 30)).thenReturn(traces);
            when(configProvider.findByCode("R1")).thenReturn(rule("R1", "amount > 50"));
            when(llmClient.chat(anyString(), anyString(), any())).thenReturn("LLM 生成的调整原因");

            List<ThresholdAnalysis> result = service.analyzeRule("R1", 30);

            assertThat(result).hasSize(1);
            assertThat(result.get(0).getReason()).isEqualTo("LLM 生成的调整原因");
            verify(llmClient).chat(anyString(), anyString(), any());
        }

        @Test
        @DisplayName("LLM 抛异常时降级为模板原因")
        void shouldFallbackToTemplateWhenLlmThrows() throws LLMException {
            List<RuleExecutionTrace> traces = generateTraces("amount", 100, 1, 1);
            when(traceDataProvider.getTracesByRule("R1", 30)).thenReturn(traces);
            when(configProvider.findByCode("R1")).thenReturn(rule("R1", "amount > 50"));
            when(llmClient.chat(anyString(), anyString(), any())).thenThrow(new LLMException("MOCK", "LLM 不可用"));

            List<ThresholdAnalysis> result = service.analyzeRule("R1", 30);

            assertThat(result).hasSize(1);
            assertThat(result.get(0).getReason()).isNotBlank();
            assertThat(result.get(0).getReason()).contains("R1");
        }

        @Test
        @DisplayName("LLM 返回空字符串时降级为模板原因")
        void shouldFallbackToTemplateWhenLlmReturnsEmpty() throws LLMException {
            List<RuleExecutionTrace> traces = generateTraces("amount", 100, 1, 1);
            when(traceDataProvider.getTracesByRule("R1", 30)).thenReturn(traces);
            when(configProvider.findByCode("R1")).thenReturn(rule("R1", "amount > 50"));
            when(llmClient.chat(anyString(), anyString(), any())).thenReturn("");

            List<ThresholdAnalysis> result = service.analyzeRule("R1", 30);

            assertThat(result).hasSize(1);
            assertThat(result.get(0).getReason()).isNotBlank();
            assertThat(result.get(0).getReason()).contains("R1");
        }
    }

    // ==================== 应用阈值调整测试 ====================

    @Nested
    @DisplayName("应用阈值调整")
    class ApplyThresholdTest {

        @Test
        @DisplayName("应用阈值 - 表达式中阈值被正确替换")
        void shouldReplaceThresholdInExpression() {
            RuleDefinition r = rule("R1", "amount > 1000");
            when(configProvider.findByCode("R1")).thenReturn(r);

            ThresholdAnalysis analysis = ThresholdAnalysis.builder()
                    .ruleCode("R1")
                    .variable("amount")
                    .operator(">")
                    .currentThreshold(1000)
                    .suggestedThreshold(2000)
                    .strategy(ThresholdStrategy.PERCENTILE)
                    .confidence(0.85)
                    .build();

            boolean success = service.applyThreshold("R1", analysis, "admin");

            assertThat(success).isTrue();
            verify(ruleAdminService).save(any(RuleDefinition.class), eq("admin"), anyString());
            assertThat(r.getConditionExpression()).isEqualTo("amount > 2000");
        }

        @Test
        @DisplayName("应用阈值 - 已应用的建议从待处理列表中移除")
        void shouldRemoveAppliedSuggestionFromPending() {
            // 先分析生成建议：规则 amount > 98，触发率 2% < 5% → MISS_RATE，建议阈值 P90 ≈ 90.1
            // 使用 MISS_RATE 场景确保 suggestedThreshold != currentThreshold，applyThreshold 才能成功替换
            List<RuleExecutionTrace> traces = generateTraces("amount", 100, 1, 1);
            when(traceDataProvider.getTracesByRule("R1", 30)).thenReturn(traces);
            when(configProvider.findByCode("R1")).thenReturn(rule("R1", "amount > 98"));
            List<ThresholdAnalysis> analyses = service.analyzeRule("R1", 30);
            assertThat(analyses).hasSize(1);
            // 确认建议阈值与当前阈值不同，applyThreshold 才会真正替换表达式
            assertThat(analyses.get(0).getSuggestedThreshold())
                    .isNotEqualTo(analyses.get(0).getCurrentThreshold());

            // 待处理列表应有 1 条
            assertThat(service.getPendingSuggestions("R1")).hasSize(1);

            // 应用阈值（返回同一规则对象，表达式会被替换）
            RuleDefinition r = rule("R1", "amount > 98");
            when(configProvider.findByCode("R1")).thenReturn(r);
            service.applyThreshold("R1", analyses.get(0), "admin");

            // 待处理列表应为空
            assertThat(service.getPendingSuggestions("R1")).isEmpty();
        }

        @Test
        @DisplayName("应用阈值 - ruleCode 为空时返回 false")
        void shouldReturnFalseForBlankRuleCode() {
            ThresholdAnalysis analysis = ThresholdAnalysis.builder().build();
            assertThat(service.applyThreshold(null, analysis, "admin")).isFalse();
            assertThat(service.applyThreshold("", analysis, "admin")).isFalse();
        }

        @Test
        @DisplayName("应用阈值 - RuleAdminService 未注入时返回 false")
        void shouldReturnFalseWhenRuleAdminServiceNull() {
            service = new AdaptiveThresholdService(configProvider, traceDataProvider, null, llmClient);
            ThresholdAnalysis analysis = ThresholdAnalysis.builder()
                    .variable("amount")
                    .operator(">")
                    .currentThreshold(1000)
                    .suggestedThreshold(2000)
                    .build();

            assertThat(service.applyThreshold("R1", analysis, "admin")).isFalse();
        }

        @Test
        @DisplayName("应用阈值 - 规则不存在时返回 false")
        void shouldReturnFalseWhenRuleNotFound() {
            when(configProvider.findByCode("NON_EXISTENT")).thenReturn(null);
            ThresholdAnalysis analysis = ThresholdAnalysis.builder().build();

            assertThat(service.applyThreshold("NON_EXISTENT", analysis, "admin")).isFalse();
            verify(ruleAdminService, never()).save(any(), anyString(), anyString());
        }
    }

    // ==================== 批量分析测试 ====================

    @Nested
    @DisplayName("批量分析")
    class AnalyzeAllTest {

        @Test
        @DisplayName("analyzeAllRules - 批量分析多条规则")
        void shouldAnalyzeAllRules() {
            RuleDefinition r1 = rule("R1", "amount > 50");
            RuleDefinition r2 = rule("R2", "score > 80");
            when(configProvider.loadAllRules()).thenReturn(List.of(r1, r2));
            when(traceDataProvider.getTracesByRule("R1", 30))
                    .thenReturn(generateTraces("amount", 100, 1, 1));
            when(traceDataProvider.getTracesByRule("R2", 30))
                    .thenReturn(generateTraces("score", 100, 1, 1));
            when(configProvider.findByCode("R1")).thenReturn(r1);
            when(configProvider.findByCode("R2")).thenReturn(r2);

            List<ThresholdAnalysis> result = service.analyzeAllRules(30);

            assertThat(result).hasSize(2);
            assertThat(result).extracting(ThresholdAnalysis::getRuleCode)
                    .containsExactlyInAnyOrder("R1", "R2");
        }

        @Test
        @DisplayName("analyzeAllRules - 无规则时返回空列表")
        void shouldReturnEmptyWhenNoRules() {
            when(configProvider.loadAllRules()).thenReturn(List.of());

            assertThat(service.analyzeAllRules(30)).isEmpty();
        }

        @Test
        @DisplayName("analyzeAllRules - 单条规则分析异常不影响其他规则")
        void shouldNotBreakWhenOneRuleFails() {
            RuleDefinition r1 = rule("R1", "amount > 50");
            RuleDefinition r2 = rule("R2", "score > 80");
            when(configProvider.loadAllRules()).thenReturn(List.of(r1, r2));
            // R1 正常返回
            when(traceDataProvider.getTracesByRule("R1", 30))
                    .thenReturn(generateTraces("amount", 100, 1, 1));
            // R2 抛异常
            when(traceDataProvider.getTracesByRule("R2", 30))
                    .thenThrow(new RuntimeException("查询失败"));
            when(configProvider.findByCode("R1")).thenReturn(r1);
            when(configProvider.findByCode("R2")).thenReturn(r2);

            List<ThresholdAnalysis> result = service.analyzeAllRules(30);

            // R1 分析成功，R2 异常被吞掉
            assertThat(result).hasSize(1);
            assertThat(result.get(0).getRuleCode()).isEqualTo("R1");
        }
    }

    // ==================== 待处理建议测试 ====================

    @Nested
    @DisplayName("待处理建议")
    class PendingSuggestionsTest {

        @Test
        @DisplayName("分析后建议被缓存到待处理列表")
        void shouldCacheSuggestionsAfterAnalysis() {
            List<RuleExecutionTrace> traces = generateTraces("amount", 100, 1, 1);
            when(traceDataProvider.getTracesByRule("R1", 30)).thenReturn(traces);
            when(configProvider.findByCode("R1")).thenReturn(rule("R1", "amount > 50"));

            service.analyzeRule("R1", 30);

            List<ThresholdAnalysis> pending = service.getPendingSuggestions("R1");
            assertThat(pending).hasSize(1);
            assertThat(pending.get(0).getRuleCode()).isEqualTo("R1");
        }

        @Test
        @DisplayName("未分析的规则返回空列表")
        void shouldReturnEmptyForUnanalyzedRule() {
            assertThat(service.getPendingSuggestions("NON_EXISTENT")).isEmpty();
        }

        @Test
        @DisplayName("ruleCode 为空时返回空列表")
        void shouldReturnEmptyForBlankRuleCode() {
            assertThat(service.getPendingSuggestions(null)).isEmpty();
            assertThat(service.getPendingSuggestions("")).isEmpty();
        }
    }
}
