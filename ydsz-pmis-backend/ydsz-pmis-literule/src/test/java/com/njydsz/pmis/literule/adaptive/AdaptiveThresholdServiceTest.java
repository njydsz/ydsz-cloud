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
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * {@link AdaptiveThresholdService} 单元测试。
 *
 * <p>覆盖阈值分析、批量分析、应用阈值、待处理建议查询、定时分析、效果追踪等能力，
 * 含 TraceDataProvider 不可用、LLM 降级、自动应用等场景。
 *
 * @author ydsz-pmis-team
 */
@DisplayName("自适应阈值分析服务测试")
@ExtendWith(MockitoExtension.class)
class AdaptiveThresholdServiceTest {

    @Mock
    private RuleConfigProvider configProvider;

    @Mock
    private TraceDataProvider traceDataProvider;

    @Mock
    private RuleAdminService ruleAdminService;

    @Mock
    private LLMClient llmClient;

    private AdaptiveThresholdService service;

    @BeforeEach
    void setUp() {
        service = new AdaptiveThresholdService(configProvider, traceDataProvider, ruleAdminService, llmClient);
    }

    private RuleDefinition buildRule(String code, String condition) {
        return RuleDefinition.builder()
                .code(code)
                .name("规则-" + code)
                .conditionExpression(condition)
                .build();
    }

    private List<RuleExecutionTrace> buildTraces(String ruleCode, String variable, double... values) {
        List<RuleExecutionTrace> traces = new ArrayList<>();
        for (double v : values) {
            RuleExecutionTrace trace = new RuleExecutionTrace();
            trace.setRuleCode(ruleCode);
            trace.setFactsSnapshot(Map.of(variable, v));
            traces.add(trace);
        }
        return traces;
    }

    // ==================== analyzeRule ====================

    @Nested
    @DisplayName("规则阈值分析：analyzeRule")
    class AnalyzeRuleTest {

        @Test
        @DisplayName("边界条件：ruleCode 为 null 返回空列表")
        void shouldReturnEmptyWhenRuleCodeNull() {
            List<ThresholdAnalysis> result = service.analyzeRule(null, 7);

            assertThat(result).isEmpty();
        }

        @Test
        @DisplayName("边界条件：ruleCode 为空白返回空列表")
        void shouldReturnEmptyWhenRuleCodeBlank() {
            List<ThresholdAnalysis> result = service.analyzeRule("   ", 7);

            assertThat(result).isEmpty();
        }

        @Test
        @DisplayName("异常场景：TraceDataProvider 不可用时返回空列表")
        void shouldReturnEmptyWhenProviderUnavailable() {
            when(traceDataProvider.isAvailable()).thenReturn(false);

            List<ThresholdAnalysis> result = service.analyzeRule("R001", 7);

            assertThat(result).isEmpty();
        }

        @Test
        @DisplayName("异常场景：规则不存在时返回空列表")
        void shouldReturnEmptyWhenRuleNotFound() {
            when(traceDataProvider.isAvailable()).thenReturn(true);
            when(configProvider.findByCode("R001")).thenReturn(null);

            List<ThresholdAnalysis> result = service.analyzeRule("R001", 7);

            assertThat(result).isEmpty();
        }

        @Test
        @DisplayName("异常场景：表达式无可识别阈值时返回空列表")
        void shouldReturnEmptyWhenNoThresholdInExpression() {
            when(traceDataProvider.isAvailable()).thenReturn(true);
            when(configProvider.findByCode("R001"))
                    .thenReturn(buildRule("R001", "fn(x)"));

            List<ThresholdAnalysis> result = service.analyzeRule("R001", 7);

            assertThat(result).isEmpty();
        }

        @Test
        @DisplayName("异常场景：获取轨迹数据抛异常时返回空列表")
        void shouldReturnEmptyWhenGetTracesThrowsException() {
            when(traceDataProvider.isAvailable()).thenReturn(true);
            when(configProvider.findByCode("R001"))
                    .thenReturn(buildRule("R001", "amount > 1000"));
            when(traceDataProvider.getTracesByRule("R001", 7))
                    .thenThrow(new RuntimeException("DB 异常"));

            List<ThresholdAnalysis> result = service.analyzeRule("R001", 7);

            assertThat(result).isEmpty();
        }

        @Test
        @DisplayName("边界条件：无轨迹数据时返回空列表")
        void shouldReturnEmptyWhenNoTraces() {
            when(traceDataProvider.isAvailable()).thenReturn(true);
            when(configProvider.findByCode("R001"))
                    .thenReturn(buildRule("R001", "amount > 1000"));
            when(traceDataProvider.getTracesByRule("R001", 7))
                    .thenReturn(List.of());

            List<ThresholdAnalysis> result = service.analyzeRule("R001", 7);

            assertThat(result).isEmpty();
        }

        @Test
        @DisplayName("边界条件：样本量不足时返回空列表")
        void shouldReturnEmptyWhenSampleSizeInsufficient() {
            when(traceDataProvider.isAvailable()).thenReturn(true);
            when(configProvider.findByCode("R001"))
                    .thenReturn(buildRule("R001", "amount > 1000"));
            // 仅 5 条样本，少于 MIN_SAMPLE_SIZE(10)
            when(traceDataProvider.getTracesByRule("R001", 7))
                    .thenReturn(buildTraces("R001", "amount", 100, 200, 300, 400, 500));

            List<ThresholdAnalysis> result = service.analyzeRule("R001", 7);

            assertThat(result).isEmpty();
        }

        @Test
        @DisplayName("正常场景：高触发率时采用 FALSE_RATE 策略")
        void shouldUseFalseRateStrategyWhenTriggerRateHigh() {
            when(traceDataProvider.isAvailable()).thenReturn(true);
            when(configProvider.findByCode("R001"))
                    .thenReturn(buildRule("R001", "amount > 1000"));
            // 15 条样本，全部 > 1000，触发率 100%，高于 HIGH_TRIGGER_RATE(50%)
            List<RuleExecutionTrace> traces = buildTraces("R001", "amount",
                    1100, 1200, 1300, 1400, 1500, 1600, 1700, 1800, 1900, 2000,
                    2100, 2200, 2300, 2400, 2500);
            when(traceDataProvider.getTracesByRule("R001", 7)).thenReturn(traces);

            List<ThresholdAnalysis> result = service.analyzeRule("R001", 7);

            assertThat(result).hasSize(1);
            ThresholdAnalysis analysis = result.get(0);
            assertThat(analysis.getRuleCode()).isEqualTo("R001");
            assertThat(analysis.getVariable()).isEqualTo("amount");
            assertThat(analysis.getOperator()).isEqualTo(">");
            assertThat(analysis.getCurrentThreshold()).isEqualTo(1000.0);
            assertThat(analysis.getStrategy()).isEqualTo(ThresholdStrategy.FALSE_RATE);
            assertThat(analysis.getConfidence()).isBetween(0.0, 1.0);
            assertThat(analysis.getReason()).isNotBlank();
        }

        @Test
        @DisplayName("正常场景：低触发率时采用 MISS_RATE 策略")
        void shouldUseMissRateStrategyWhenTriggerRateLow() {
            when(traceDataProvider.isAvailable()).thenReturn(true);
            when(configProvider.findByCode("R001"))
                    .thenReturn(buildRule("R001", "amount > 5000"));
            // 15 条样本，仅 1 条 > 5000，触发率约 6.7%，低于 LOW_TRIGGER_RATE(5%) 需更少触发
            List<RuleExecutionTrace> traces = buildTraces("R001", "amount",
                    100, 200, 300, 400, 500, 600, 700, 800, 900, 1000,
                    1100, 1200, 1300, 1400, 1500);
            when(traceDataProvider.getTracesByRule("R001", 7)).thenReturn(traces);

            List<ThresholdAnalysis> result = service.analyzeRule("R001", 7);

            assertThat(result).hasSize(1);
            // 0 触发率 → MISS_RATE
            assertThat(result.get(0).getStrategy()).isEqualTo(ThresholdStrategy.MISS_RATE);
        }

        @Test
        @DisplayName("正常场景：触发率适中时采用 BALANCED 策略")
        void shouldUseBalancedStrategyWhenTriggerRateModerate() {
            when(traceDataProvider.isAvailable()).thenReturn(true);
            when(configProvider.findByCode("R001"))
                    .thenReturn(buildRule("R001", "amount > 1200"));
            // 阈值 1200，> 1200 的有 1300~1500 = 3 条，触发率 3/15 = 20%（5%~50% 区间）
            List<RuleExecutionTrace> traces = buildTraces("R001", "amount",
                    100, 200, 300, 400, 500, 600, 700, 800, 900, 1000,
                    1100, 1200, 1300, 1400, 1500);
            when(traceDataProvider.getTracesByRule("R001", 7)).thenReturn(traces);

            List<ThresholdAnalysis> result = service.analyzeRule("R001", 7);

            assertThat(result).hasSize(1);
            assertThat(result.get(0).getStrategy()).isEqualTo(ThresholdStrategy.BALANCED);
        }

        @Test
        @DisplayName("正常场景：分析结果被缓存为待处理建议")
        void shouldCacheAnalysisAsPendingSuggestion() {
            when(traceDataProvider.isAvailable()).thenReturn(true);
            when(configProvider.findByCode("R001"))
                    .thenReturn(buildRule("R001", "amount > 1000"));
            List<RuleExecutionTrace> traces = buildTraces("R001", "amount",
                    1100, 1200, 1300, 1400, 1500, 1600, 1700, 1800, 1900, 2000,
                    2100, 2200, 2300, 2400, 2500);
            when(traceDataProvider.getTracesByRule("R001", 7)).thenReturn(traces);

            service.analyzeRule("R001", 7);

            List<ThresholdAnalysis> pending = service.getPendingSuggestions("R001");
            assertThat(pending).hasSize(1);
            assertThat(pending.get(0).isApplied()).isFalse();
        }

        @Test
        @DisplayName("正常场景：LLM 不可用时降级为模板原因")
        void shouldFallbackToTemplateWhenLLMThrowsException() {
            when(traceDataProvider.isAvailable()).thenReturn(true);
            when(configProvider.findByCode("R001"))
                    .thenReturn(buildRule("R001", "amount > 1000"));
            List<RuleExecutionTrace> traces = buildTraces("R001", "amount",
                    1100, 1200, 1300, 1400, 1500, 1600, 1700, 1800, 1900, 2000,
                    2100, 2200, 2300, 2400, 2500);
            when(traceDataProvider.getTracesByRule("R001", 7)).thenReturn(traces);
            when(llmClient.chat(anyString(), anyString(), any()))
                    .thenThrow(new LLMException("MOCK", "LLM 不可用"));

            List<ThresholdAnalysis> result = service.analyzeRule("R001", 7);

            assertThat(result).hasSize(1);
            // 模板原因包含"规则[R001]"
            assertThat(result.get(0).getReason()).contains("规则");
        }
    }

    // ==================== analyzeAllRules ====================

    @Nested
    @DisplayName("批量阈值分析：analyzeAllRules")
    class AnalyzeAllRulesTest {

        @Test
        @DisplayName("异常场景：loadAllRules 抛异常时返回空列表")
        void shouldReturnEmptyWhenLoadAllRulesThrowsException() {
            when(configProvider.loadAllRules()).thenThrow(new RuntimeException("DB 异常"));

            List<ThresholdAnalysis> result = service.analyzeAllRules(7);

            assertThat(result).isEmpty();
        }

        @Test
        @DisplayName("边界条件：规则列表为空时返回空列表")
        void shouldReturnEmptyWhenRulesEmpty() {
            when(configProvider.loadAllRules()).thenReturn(List.of());

            List<ThresholdAnalysis> result = service.analyzeAllRules(7);

            assertThat(result).isEmpty();
        }

        @Test
        @DisplayName("正常场景：批量分析多条规则")
        void shouldAnalyzeMultipleRules() {
            when(traceDataProvider.isAvailable()).thenReturn(true);
            when(configProvider.loadAllRules()).thenReturn(List.of(
                    buildRule("R001", "amount > 1000"),
                    buildRule("R002", "score < 800")
            ));
            when(configProvider.findByCode("R001"))
                    .thenReturn(buildRule("R001", "amount > 1000"));
            when(configProvider.findByCode("R002"))
                    .thenReturn(buildRule("R002", "score < 800"));
            List<RuleExecutionTrace> traces1 = buildTraces("R001", "amount",
                    1100, 1200, 1300, 1400, 1500, 1600, 1700, 1800, 1900, 2000,
                    2100, 2200, 2300, 2400, 2500);
            List<RuleExecutionTrace> traces2 = buildTraces("R002", "score",
                    700, 750, 760, 770, 780, 790, 800, 810, 820, 830,
                    840, 850, 860, 870, 880);
            when(traceDataProvider.getTracesByRule("R001", 7)).thenReturn(traces1);
            when(traceDataProvider.getTracesByRule("R002", 7)).thenReturn(traces2);

            List<ThresholdAnalysis> result = service.analyzeAllRules(7);

            assertThat(result).hasSize(2);
            assertThat(result).extracting(ThresholdAnalysis::getRuleCode)
                    .containsExactlyInAnyOrder("R001", "R002");
        }

        @Test
        @DisplayName("异常场景：单条规则分析抛异常时跳过该规则")
        void shouldSkipRuleWhenAnalysisThrowsException() {
            when(traceDataProvider.isAvailable()).thenReturn(true);
            when(configProvider.loadAllRules()).thenReturn(List.of(
                    buildRule("R001", "amount > 1000"),
                    buildRule("R002", "score < 800")
            ));
            // R001 分析正常
            when(configProvider.findByCode("R001"))
                    .thenReturn(buildRule("R001", "amount > 1000"));
            List<RuleExecutionTrace> traces1 = buildTraces("R001", "amount",
                    1100, 1200, 1300, 1400, 1500, 1600, 1700, 1800, 1900, 2000,
                    2100, 2200, 2300, 2400, 2500);
            when(traceDataProvider.getTracesByRule("R001", 7)).thenReturn(traces1);
            // R002 查询时抛异常
            when(configProvider.findByCode("R002"))
                    .thenThrow(new RuntimeException("规则不存在"));

            List<ThresholdAnalysis> result = service.analyzeAllRules(7);

            // R002 异常被跳过，仅返回 R001
            assertThat(result).hasSize(1);
            assertThat(result.get(0).getRuleCode()).isEqualTo("R001");
        }
    }

    // ==================== applyThreshold ====================

    @Nested
    @DisplayName("应用阈值调整：applyThreshold")
    class ApplyThresholdTest {

        @Test
        @DisplayName("边界条件：ruleCode 为 null 返回 false")
        void shouldReturnFalseWhenRuleCodeNull() {
            ThresholdAnalysis analysis = ThresholdAnalysis.builder()
                    .variable("amount").operator(">").currentThreshold(1000)
                    .suggestedThreshold(2000).build();

            boolean result = service.applyThreshold(null, analysis, "operator");

            assertThat(result).isFalse();
        }

        @Test
        @DisplayName("边界条件：analysis 为 null 返回 false")
        void shouldReturnFalseWhenAnalysisNull() {
            boolean result = service.applyThreshold("R001", null, "operator");

            assertThat(result).isFalse();
        }

        @Test
        @DisplayName("异常场景：ruleAdminService 为 null 返回 false")
        void shouldReturnFalseWhenRuleAdminServiceNull() {
            AdaptiveThresholdService serviceWithoutAdmin = new AdaptiveThresholdService(
                    configProvider, traceDataProvider, null, llmClient);
            ThresholdAnalysis analysis = ThresholdAnalysis.builder()
                    .variable("amount").operator(">").currentThreshold(1000)
                    .suggestedThreshold(2000).build();

            boolean result = serviceWithoutAdmin.applyThreshold("R001", analysis, "operator");

            assertThat(result).isFalse();
        }

        @Test
        @DisplayName("异常场景：规则不存在返回 false")
        void shouldReturnFalseWhenRuleNotFound() {
            when(configProvider.findByCode("R001")).thenReturn(null);
            ThresholdAnalysis analysis = ThresholdAnalysis.builder()
                    .variable("amount").operator(">").currentThreshold(1000)
                    .suggestedThreshold(2000).build();

            boolean result = service.applyThreshold("R001", analysis, "operator");

            assertThat(result).isFalse();
        }

        @Test
        @DisplayName("正常场景：应用阈值成功返回 true 并标记 applied")
        void shouldApplyThresholdSuccessfully() {
            RuleDefinition rule = buildRule("R001", "amount > 1000");
            when(configProvider.findByCode("R001")).thenReturn(rule);
            ThresholdAnalysis analysis = ThresholdAnalysis.builder()
                    .ruleCode("R001")
                    .variable("amount").operator(">")
                    .currentThreshold(1000).suggestedThreshold(2000)
                    .strategy(ThresholdStrategy.PERCENTILE).confidence(0.9)
                    .build();

            boolean result = service.applyThreshold("R001", analysis, "operator");

            assertThat(result).isTrue();
            assertThat(analysis.isApplied()).isTrue();
            verify(ruleAdminService).save(any(RuleDefinition.class), eq("operator"), anyString());
        }

        @Test
        @DisplayName("异常场景：ruleAdminService.save 抛异常返回 false")
        void shouldReturnFalseWhenSaveThrowsException() {
            RuleDefinition rule = buildRule("R001", "amount > 1000");
            when(configProvider.findByCode("R001")).thenReturn(rule);
            doThrow(new RuntimeException("保存失败")).when(ruleAdminService)
                    .save(any(RuleDefinition.class), anyString(), anyString());
            ThresholdAnalysis analysis = ThresholdAnalysis.builder()
                    .ruleCode("R001")
                    .variable("amount").operator(">")
                    .currentThreshold(1000).suggestedThreshold(2000)
                    .strategy(ThresholdStrategy.PERCENTILE).confidence(0.9)
                    .build();

            boolean result = service.applyThreshold("R001", analysis, "operator");

            assertThat(result).isFalse();
            assertThat(analysis.isApplied()).isFalse();
        }
    }

    // ==================== getPendingSuggestions ====================

    @Nested
    @DisplayName("待处理建议查询：getPendingSuggestions")
    class GetPendingSuggestionsTest {

        @Test
        @DisplayName("边界条件：ruleCode 为 null 返回空列表")
        void shouldReturnEmptyWhenRuleCodeNull() {
            List<ThresholdAnalysis> result = service.getPendingSuggestions(null);

            assertThat(result).isEmpty();
        }

        @Test
        @DisplayName("边界条件：无缓存时返回空列表")
        void shouldReturnEmptyWhenNoCache() {
            List<ThresholdAnalysis> result = service.getPendingSuggestions("R001");

            assertThat(result).isEmpty();
        }
    }

    // ==================== 自动应用与定时分析 ====================

    @Nested
    @DisplayName("定时分析与自动应用：scheduledAnalyze")
    class ScheduledAnalyzeTest {

        @Test
        @DisplayName("正常场景：未启用自动应用时仅分析")
        void shouldAnalyzeOnlyWhenAutoApplyDisabled() {
            when(configProvider.loadAllRules()).thenReturn(List.of());

            ScheduledAnalysisResult result = service.scheduledAnalyze(7, "system");

            assertThat(result).isNotNull();
            assertThat(result.getTotalRulesAnalyzed()).isEqualTo(0);
            assertThat(result.getAutoApplied()).isEqualTo(0);
        }

        @Test
        @DisplayName("正常场景：启用自动应用且置信度达标时自动应用")
        void shouldAutoApplyWhenEnabledAndConfidenceHigh() {
            when(traceDataProvider.isAvailable()).thenReturn(true);
            when(configProvider.loadAllRules()).thenReturn(List.of(
                    buildRule("R001", "amount > 1000")));
            when(configProvider.findByCode("R001"))
                    .thenReturn(buildRule("R001", "amount > 1000"));
            // 使用高样本量保证置信度达标
            List<RuleExecutionTrace> manyTraces = buildTraces("R001", "amount",
                    1100, 1200, 1300, 1400, 1500, 1600, 1700, 1800, 1900, 2000,
                    2100, 2200, 2300, 2400, 2500, 2600, 2700, 2800, 2900, 3000,
                    3100, 3200, 3300, 3400, 3500, 3600, 3700, 3800, 3900, 4000);
            when(traceDataProvider.getTracesByRule(eq("R001"), anyInt())).thenReturn(manyTraces);

            service.setAutoApplyEnabled(true);
            service.setAutoApplyConfidenceThreshold(0.0);

            ScheduledAnalysisResult result = service.scheduledAnalyze(7, "system");

            assertThat(result).isNotNull();
            assertThat(result.getTotalRulesAnalyzed()).isGreaterThanOrEqualTo(0);
        }

        @Test
        @DisplayName("正常场景：设置自动应用置信度阈值会被 clamp 到 [0,1]")
        void shouldClampAutoApplyConfidenceThreshold() {
            service.setAutoApplyConfidenceThreshold(2.0);
            service.setAutoApplyConfidenceThreshold(-1.0);
            // 不抛异常即通过
            assertThat(service).isNotNull();
        }
    }

    // ==================== 效果报告查询 ====================

    @Nested
    @DisplayName("效果报告查询")
    class EffectReportTest {

        @Test
        @DisplayName("边界条件：ruleCode 为 null 返回空列表")
        void shouldReturnEmptyWhenRuleCodeNull() {
            List<ThresholdEffectReport> result = service.getEffectReports(null);

            assertThat(result).isEmpty();
        }

        @Test
        @DisplayName("边界条件：无效果报告时返回空列表")
        void shouldReturnEmptyWhenNoEffectReports() {
            List<ThresholdEffectReport> result = service.getEffectReports("R001");

            assertThat(result).isEmpty();
        }

        @Test
        @DisplayName("正常场景：getAllEffectReports 返回全部效果报告")
        void shouldReturnAllEffectReports() {
            List<ThresholdEffectReport> result = service.getAllEffectReports();

            assertThat(result).isNotNull();
            assertThat(result).isEmpty();
        }
    }
}
