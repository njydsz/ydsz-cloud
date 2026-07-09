package com.njydsz.pmis.literule.core;

import com.njydsz.pmis.literule.api.EffectivenessReport;
import com.njydsz.pmis.literule.api.RuleEffectivenessMetrics;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.within;

/**
 * {@link RuleEffectivenessService} 单元测试。
 *
 * <p>覆盖反馈记录、指标计算、报告生成、窗口淘汰、管理操作等核心能力。
 *
 * @author ydsz-pmis-team
 */
@DisplayName("规则效果评估服务测试")
class RuleEffectivenessServiceTest {

    private RuleEffectivenessService service;

    @BeforeEach
    void setUp() {
        service = new RuleEffectivenessService();
    }

    @Nested
    @DisplayName("反馈记录：recordFeedback")
    class RecordFeedbackTest {

        @Test
        @DisplayName("正常场景：记录真正例反馈")
        void shouldRecordTruePositive() {
            service.recordFeedback("R001", RuleEffectivenessService.FeedbackType.TRUE_POSITIVE);

            RuleEffectivenessMetrics metrics = service.getMetrics("R001");
            assertThat(metrics.getTruePositives()).isEqualTo(1);
            assertThat(metrics.getTotalSamples()).isEqualTo(1);
        }

        @Test
        @DisplayName("正常场景：记录多种类型反馈")
        void shouldRecordMultipleTypes() {
            service.recordFeedback("R001", RuleEffectivenessService.FeedbackType.TRUE_POSITIVE);
            service.recordFeedback("R001", RuleEffectivenessService.FeedbackType.FALSE_POSITIVE);
            service.recordFeedback("R001", RuleEffectivenessService.FeedbackType.FALSE_NEGATIVE);
            service.recordFeedback("R001", RuleEffectivenessService.FeedbackType.TRUE_NEGATIVE);

            RuleEffectivenessMetrics metrics = service.getMetrics("R001");
            assertThat(metrics.getTruePositives()).isEqualTo(1);
            assertThat(metrics.getFalsePositives()).isEqualTo(1);
            assertThat(metrics.getFalseNegatives()).isEqualTo(1);
            assertThat(metrics.getTrueNegatives()).isEqualTo(1);
            assertThat(metrics.getTotalSamples()).isEqualTo(4);
        }

        @Test
        @DisplayName("异常场景：ruleCode 为 null 抛 IllegalArgumentException")
        void shouldThrowWhenRuleCodeNull() {
            assertThatThrownBy(() -> service.recordFeedback(null,
                            RuleEffectivenessService.FeedbackType.TRUE_POSITIVE))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("ruleCode");
        }

        @Test
        @DisplayName("异常场景：ruleCode 为空字符串抛 IllegalArgumentException")
        void shouldThrowWhenRuleCodeBlank() {
            assertThatThrownBy(() -> service.recordFeedback("  ",
                            RuleEffectivenessService.FeedbackType.TRUE_POSITIVE))
                    .isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        @DisplayName("异常场景：反馈类型为 null 抛 IllegalArgumentException")
        void shouldThrowWhenTypeNull() {
            assertThatThrownBy(() -> service.recordFeedback("R001", null))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("反馈类型");
        }
    }

    @Nested
    @DisplayName("批量反馈记录：recordFeedbackBatch")
    class RecordFeedbackBatchTest {

        @Test
        @DisplayName("正常场景：批量记录指定数量反馈")
        void shouldRecordBatch() {
            service.recordFeedbackBatch("R001",
                    RuleEffectivenessService.FeedbackType.TRUE_POSITIVE, 10);

            RuleEffectivenessMetrics metrics = service.getMetrics("R001");
            assertThat(metrics.getTruePositives()).isEqualTo(10);
            assertThat(metrics.getTotalSamples()).isEqualTo(10);
        }

        @Test
        @DisplayName("边界场景：count <= 0 时不记录")
        void shouldNotRecordWhenCountNonPositive() {
            service.recordFeedbackBatch("R001",
                    RuleEffectivenessService.FeedbackType.TRUE_POSITIVE, 0);
            service.recordFeedbackBatch("R001",
                    RuleEffectivenessService.FeedbackType.TRUE_POSITIVE, -5);

            assertThat(service.ruleCount()).isEqualTo(0);
        }
    }

    @Nested
    @DisplayName("批量反馈记录：recordFeedbacks")
    class RecordFeedbacksTest {

        @Test
        @DisplayName("正常场景：批量录入多条不同规则的反馈")
        void shouldRecordMultipleFeedbacks() {
            List<RuleEffectivenessService.FeedbackRecord> feedbacks = List.of(
                    new RuleEffectivenessService.FeedbackRecord("R001",
                            RuleEffectivenessService.FeedbackType.TRUE_POSITIVE),
                    new RuleEffectivenessService.FeedbackRecord("R002",
                            RuleEffectivenessService.FeedbackType.FALSE_POSITIVE),
                    new RuleEffectivenessService.FeedbackRecord("R001",
                            RuleEffectivenessService.FeedbackType.TRUE_NEGATIVE)
            );

            service.recordFeedbacks(feedbacks);

            assertThat(service.ruleCount()).isEqualTo(2);
            assertThat(service.getMetrics("R001").getTotalSamples()).isEqualTo(2);
            assertThat(service.getMetrics("R002").getTotalSamples()).isEqualTo(1);
        }

        @Test
        @DisplayName("边界场景：null 列表不记录")
        void shouldHandleNullList() {
            service.recordFeedbacks(null);

            assertThat(service.ruleCount()).isEqualTo(0);
        }

        @Test
        @DisplayName("边界场景：空列表不记录")
        void shouldHandleEmptyList() {
            service.recordFeedbacks(List.of());

            assertThat(service.ruleCount()).isEqualTo(0);
        }
    }

    @Nested
    @DisplayName("指标查询：getMetrics")
    class GetMetricsTest {

        @Test
        @DisplayName("边界场景：规则无反馈数据返回空指标")
        void shouldReturnEmptyMetricsWhenNoData() {
            RuleEffectivenessMetrics metrics = service.getMetrics("R_NOT_EXIST");

            assertThat(metrics).isNotNull();
            assertThat(metrics.getRuleCode()).isEqualTo("R_NOT_EXIST");
            assertThat(metrics.getTotalSamples()).isEqualTo(0);
            assertThat(metrics.getTruePositives()).isEqualTo(0);
            assertThat(metrics.getPrecision()).isEqualTo(0.0);
            assertThat(metrics.getRecall()).isEqualTo(0.0);
            assertThat(metrics.getF1Score()).isEqualTo(0.0);
        }

        @Test
        @DisplayName("正常场景：精确率计算正确")
        void shouldCalculatePrecision() {
            service.recordFeedbackBatch("R001",
                    RuleEffectivenessService.FeedbackType.TRUE_POSITIVE, 80);
            service.recordFeedbackBatch("R001",
                    RuleEffectivenessService.FeedbackType.FALSE_POSITIVE, 20);

            RuleEffectivenessMetrics metrics = service.getMetrics("R001");

            assertThat(metrics.getPrecision()).isCloseTo(0.8, within(0.0001));
        }

        @Test
        @DisplayName("正常场景：召回率计算正确")
        void shouldCalculateRecall() {
            service.recordFeedbackBatch("R001",
                    RuleEffectivenessService.FeedbackType.TRUE_POSITIVE, 80);
            service.recordFeedbackBatch("R001",
                    RuleEffectivenessService.FeedbackType.FALSE_NEGATIVE, 20);

            RuleEffectivenessMetrics metrics = service.getMetrics("R001");

            assertThat(metrics.getRecall()).isCloseTo(0.8, within(0.0001));
        }

        @Test
        @DisplayName("正常场景：F1-Score 计算正确")
        void shouldCalculateF1Score() {
            service.recordFeedbackBatch("R001",
                    RuleEffectivenessService.FeedbackType.TRUE_POSITIVE, 80);
            service.recordFeedbackBatch("R001",
                    RuleEffectivenessService.FeedbackType.FALSE_POSITIVE, 20);
            service.recordFeedbackBatch("R001",
                    RuleEffectivenessService.FeedbackType.FALSE_NEGATIVE, 20);

            RuleEffectivenessMetrics metrics = service.getMetrics("R001");

            double expectedF1 = 2.0 * 0.8 * 0.8 / (0.8 + 0.8);
            assertThat(metrics.getF1Score()).isCloseTo(expectedF1, within(0.0001));
        }

        @Test
        @DisplayName("正常场景：准确率计算正确")
        void shouldCalculateAccuracy() {
            service.recordFeedbackBatch("R001",
                    RuleEffectivenessService.FeedbackType.TRUE_POSITIVE, 80);
            service.recordFeedbackBatch("R001",
                    RuleEffectivenessService.FeedbackType.TRUE_NEGATIVE, 90);
            service.recordFeedbackBatch("R001",
                    RuleEffectivenessService.FeedbackType.FALSE_POSITIVE, 20);
            service.recordFeedbackBatch("R001",
                    RuleEffectivenessService.FeedbackType.FALSE_NEGATIVE, 10);

            RuleEffectivenessMetrics metrics = service.getMetrics("R001");

            assertThat(metrics.getAccuracy()).isCloseTo(0.85, within(0.0001));
        }

        @Test
        @DisplayName("正常场景：效果等级判断正确")
        void shouldDetermineLevel() {
            service.recordFeedbackBatch("R001",
                    RuleEffectivenessService.FeedbackType.TRUE_POSITIVE, 95);
            service.recordFeedbackBatch("R001",
                    RuleEffectivenessService.FeedbackType.FALSE_POSITIVE, 5);

            RuleEffectivenessMetrics metrics = service.getMetrics("R001");

            assertThat(metrics.getLevel()).isEqualTo(RuleEffectivenessMetrics.EffectivenessLevel.EXCELLENT);
        }

        @Test
        @DisplayName("边界场景：样本不足时等级为 INSUFFICIENT_DATA")
        void shouldReturnInsufficientDataLevel() {
            service.recordFeedback("R001",
                    RuleEffectivenessService.FeedbackType.TRUE_POSITIVE);

            RuleEffectivenessMetrics metrics = service.getMetrics("R001");

            assertThat(metrics.getLevel())
                    .isEqualTo(RuleEffectivenessMetrics.EffectivenessLevel.INSUFFICIENT_DATA);
        }
    }

    @Nested
    @DisplayName("全部指标查询：getAllMetrics")
    class GetAllMetricsTest {

        @Test
        @DisplayName("边界场景：无数据返回空 Map")
        void shouldReturnEmptyWhenNoData() {
            Map<String, RuleEffectivenessMetrics> result = service.getAllMetrics();

            assertThat(result).isEmpty();
        }

        @Test
        @DisplayName("正常场景：返回所有规则的指标")
        void shouldReturnAllMetrics() {
            service.recordFeedback("R001", RuleEffectivenessService.FeedbackType.TRUE_POSITIVE);
            service.recordFeedback("R002", RuleEffectivenessService.FeedbackType.FALSE_POSITIVE);

            Map<String, RuleEffectivenessMetrics> result = service.getAllMetrics();

            assertThat(result).hasSize(2);
            assertThat(result).containsKeys("R001", "R002");
        }
    }

    @Nested
    @DisplayName("全局指标查询：getGlobalMetrics")
    class GetGlobalMetricsTest {

        @Test
        @DisplayName("边界场景：无数据返回空全局指标")
        void shouldReturnEmptyWhenNoData() {
            RuleEffectivenessMetrics global = service.getGlobalMetrics();

            assertThat(global).isNotNull();
            assertThat(global.getTotalSamples()).isEqualTo(0);
            assertThat(global.getRuleCode()).isNull();
        }

        @Test
        @DisplayName("正常场景：汇总所有规则的 TP/FP/FN/TN")
        void shouldAggregateGlobalMetrics() {
            service.recordFeedbackBatch("R001",
                    RuleEffectivenessService.FeedbackType.TRUE_POSITIVE, 50);
            service.recordFeedbackBatch("R002",
                    RuleEffectivenessService.FeedbackType.TRUE_POSITIVE, 30);
            service.recordFeedbackBatch("R001",
                    RuleEffectivenessService.FeedbackType.FALSE_POSITIVE, 10);

            RuleEffectivenessMetrics global = service.getGlobalMetrics();

            assertThat(global.getTruePositives()).isEqualTo(80);
            assertThat(global.getFalsePositives()).isEqualTo(10);
            assertThat(global.getTotalSamples()).isEqualTo(90);
        }
    }

    @Nested
    @DisplayName("报告生成：generateReport")
    class GenerateReportTest {

        @Test
        @DisplayName("边界场景：无数据时报告字段为空")
        void shouldGenerateEmptyReport() {
            EffectivenessReport report = service.generateReport();

            assertThat(report).isNotNull();
            assertThat(report.getGlobalMetrics().getTotalSamples()).isEqualTo(0);
            assertThat(report.getEvaluatedRuleCount()).isEqualTo(0);
            assertThat(report.getPoorRules()).isEmpty();
            assertThat(report.getTopRules()).isEmpty();
            assertThat(report.getLowDataRules()).isEmpty();
            assertThat(report.getGeneratedAt()).isNotNull();
        }

        @Test
        @DisplayName("正常场景：识别低效规则")
        void shouldIdentifyPoorRules() {
            // R001 效果差：FP 很多
            service.recordFeedbackBatch("R001",
                    RuleEffectivenessService.FeedbackType.TRUE_POSITIVE, 10);
            service.recordFeedbackBatch("R001",
                    RuleEffectivenessService.FeedbackType.FALSE_POSITIVE, 50);
            service.recordFeedbackBatch("R001",
                    RuleEffectivenessService.FeedbackType.FALSE_NEGATIVE, 20);
            // 补足样本到 30 以上
            service.recordFeedbackBatch("R001",
                    RuleEffectivenessService.FeedbackType.TRUE_NEGATIVE, 20);

            EffectivenessReport report = service.generateReport();

            assertThat(report.getPoorRules()).hasSize(1);
            assertThat(report.getPoorRules().get(0).getRuleCode()).isEqualTo("R001");
        }

        @Test
        @DisplayName("正常场景：识别优秀规则")
        void shouldIdentifyTopRules() {
            // R001 效果好：高 TP，低 FP/FN
            service.recordFeedbackBatch("R001",
                    RuleEffectivenessService.FeedbackType.TRUE_POSITIVE, 90);
            service.recordFeedbackBatch("R001",
                    RuleEffectivenessService.FeedbackType.TRUE_NEGATIVE, 30);
            service.recordFeedbackBatch("R001",
                    RuleEffectivenessService.FeedbackType.FALSE_POSITIVE, 5);
            service.recordFeedbackBatch("R001",
                    RuleEffectivenessService.FeedbackType.FALSE_NEGATIVE, 5);

            EffectivenessReport report = service.generateReport();

            assertThat(report.getTopRules()).hasSize(1);
            assertThat(report.getTopRules().get(0).getRuleCode()).isEqualTo("R001");
        }

        @Test
        @DisplayName("正常场景：识别样本不足规则")
        void shouldIdentifyLowDataRules() {
            service.recordFeedback("R001", RuleEffectivenessService.FeedbackType.TRUE_POSITIVE);

            EffectivenessReport report = service.generateReport();

            assertThat(report.getLowDataRules()).contains("R001");
        }

        @Test
        @DisplayName("正常场景：报告摘要可读")
        void shouldGenerateReadableSummary() {
            service.recordFeedbackBatch("R001",
                    RuleEffectivenessService.FeedbackType.TRUE_POSITIVE, 40);
            service.recordFeedbackBatch("R001",
                    RuleEffectivenessService.FeedbackType.TRUE_NEGATIVE, 40);

            EffectivenessReport report = service.generateReport();

            assertThat(report.getSummary()).contains("共评估");
            assertThat(report.getSummary()).contains("反馈样本");
        }
    }

    @Nested
    @DisplayName("管理操作")
    class ManagementTest {

        @Test
        @DisplayName("正常场景：clearRule 清除指定规则数据")
        void shouldClearRule() {
            service.recordFeedback("R001", RuleEffectivenessService.FeedbackType.TRUE_POSITIVE);
            service.recordFeedback("R002", RuleEffectivenessService.FeedbackType.TRUE_POSITIVE);

            service.clearRule("R001");

            assertThat(service.ruleCount()).isEqualTo(1);
            assertThat(service.getMetrics("R001").getTotalSamples()).isEqualTo(0);
            assertThat(service.getMetrics("R002").getTotalSamples()).isEqualTo(1);
        }

        @Test
        @DisplayName("正常场景：clearAll 清除全部数据")
        void shouldClearAll() {
            service.recordFeedback("R001", RuleEffectivenessService.FeedbackType.TRUE_POSITIVE);
            service.recordFeedback("R002", RuleEffectivenessService.FeedbackType.TRUE_POSITIVE);

            service.clearAll();

            assertThat(service.ruleCount()).isEqualTo(0);
            assertThat(service.totalFeedbackCount()).isEqualTo(0);
        }

        @Test
        @DisplayName("正常场景：ruleCount 返回规则数量")
        void shouldReturnRuleCount() {
            service.recordFeedback("R001", RuleEffectivenessService.FeedbackType.TRUE_POSITIVE);
            service.recordFeedback("R002", RuleEffectivenessService.FeedbackType.TRUE_POSITIVE);
            service.recordFeedback("R003", RuleEffectivenessService.FeedbackType.TRUE_POSITIVE);

            assertThat(service.ruleCount()).isEqualTo(3);
        }

        @Test
        @DisplayName("正常场景：totalFeedbackCount 返回总样本数")
        void shouldReturnTotalFeedbackCount() {
            service.recordFeedbackBatch("R001",
                    RuleEffectivenessService.FeedbackType.TRUE_POSITIVE, 10);
            service.recordFeedbackBatch("R002",
                    RuleEffectivenessService.FeedbackType.FALSE_POSITIVE, 20);

            assertThat(service.totalFeedbackCount()).isEqualTo(30);
        }
    }

    @Nested
    @DisplayName("滑动窗口淘汰")
    class EvictionTest {

        @Test
        @DisplayName("正常场景：evictNow 不抛异常")
        void shouldEvictWithoutError() {
            service.recordFeedback("R001", RuleEffectivenessService.FeedbackType.TRUE_POSITIVE);

            service.evictNow();

            // 窗口为 7 天，刚记录的反馈不会被淘汰
            assertThat(service.getMetrics("R001").getTotalSamples()).isEqualTo(1);
        }

        @Test
        @DisplayName("正常场景：短窗口服务创建不抛异常")
        void shouldCreateWithShortWindow() {
            RuleEffectivenessService shortWindowService = new RuleEffectivenessService(Duration.ofMillis(1));

            assertThat(shortWindowService).isNotNull();
            assertThat(shortWindowService.ruleCount()).isEqualTo(0);
        }

        @Test
        @DisplayName("边界场景：window 为 null 时使用默认窗口")
        void shouldUseDefaultWindowWhenNull() {
            RuleEffectivenessService nullWindowService = new RuleEffectivenessService(null);

            assertThat(nullWindowService).isNotNull();
        }
    }
}
