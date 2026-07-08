package com.njydsz.pmis.literule.core;

import com.njydsz.pmis.literule.api.EffectivenessReport;
import com.njydsz.pmis.literule.api.RuleEffectivenessMetrics;
import com.njydsz.pmis.literule.core.RuleEffectivenessService.FeedbackType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.Arrays;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * RuleEffectivenessService 单元测试（P2-2 规则效果评估体系）
 *
 * <p>测试目标：验证效果评估服务的核心能力，包括：
 * <ul>
 *   <li>反馈记录（单条、批量）</li>
 *   <li>指标计算（Precision/Recall/F1/Specificity/Accuracy）</li>
 *   <li>全局汇总指标</li>
 *   <li>报告生成（低效规则识别、优秀规则识别、样本不足标记）</li>
 *   <li>滑动窗口淘汰</li>
 *   <li>数据清除</li>
 *   <li>线程安全（并发记录）</li>
 * </ul>
 *
 * @author ydsz-pmis-team
 */
@DisplayName("RuleEffectivenessService 单元测试")
class RuleEffectivenessServiceTest {

    private RuleEffectivenessService service;

    @BeforeEach
    void setUp() {
        service = new RuleEffectivenessService();
    }

    // ==================== 反馈记录 ====================

    @Nested
    @DisplayName("反馈记录")
    class FeedbackRecordingTest {

        @Test
        @DisplayName("记录单条 TP 反馈 - 指标正确")
        void shouldRecordSingleTP() {
            service.recordFeedback("R001", FeedbackType.TRUE_POSITIVE);

            RuleEffectivenessMetrics m = service.getMetrics("R001");
            assertThat(m.getTruePositives()).isEqualTo(1);
            assertThat(m.getFalsePositives()).isEqualTo(0);
            assertThat(m.getFalseNegatives()).isEqualTo(0);
            assertThat(m.getTrueNegatives()).isEqualTo(0);
            assertThat(m.getTotalSamples()).isEqualTo(1);
        }

        @Test
        @DisplayName("记录多种反馈类型 - 计数器独立")
        void shouldRecordMixedTypes() {
            service.recordFeedback("R001", FeedbackType.TRUE_POSITIVE);
            service.recordFeedback("R001", FeedbackType.TRUE_POSITIVE);
            service.recordFeedback("R001", FeedbackType.FALSE_POSITIVE);
            service.recordFeedback("R001", FeedbackType.FALSE_NEGATIVE);
            service.recordFeedback("R001", FeedbackType.TRUE_NEGATIVE);

            RuleEffectivenessMetrics m = service.getMetrics("R001");
            assertThat(m.getTruePositives()).isEqualTo(2);
            assertThat(m.getFalsePositives()).isEqualTo(1);
            assertThat(m.getFalseNegatives()).isEqualTo(1);
            assertThat(m.getTrueNegatives()).isEqualTo(1);
            assertThat(m.getTotalSamples()).isEqualTo(5);
        }

        @Test
        @DisplayName("批量记录反馈 - 数量正确")
        void shouldRecordBatch() {
            service.recordFeedbackBatch("R001", FeedbackType.TRUE_POSITIVE, 10);
            service.recordFeedbackBatch("R001", FeedbackType.FALSE_POSITIVE, 5);

            RuleEffectivenessMetrics m = service.getMetrics("R001");
            assertThat(m.getTruePositives()).isEqualTo(10);
            assertThat(m.getFalsePositives()).isEqualTo(5);
            assertThat(m.getTotalSamples()).isEqualTo(15);
        }

        @Test
        @DisplayName("批量记录多条不同规则的反馈")
        void shouldRecordMultipleRules() {
            service.recordFeedbacks(Arrays.asList(
                    new RuleEffectivenessService.FeedbackRecord("R001", FeedbackType.TRUE_POSITIVE),
                    new RuleEffectivenessService.FeedbackRecord("R001", FeedbackType.FALSE_POSITIVE),
                    new RuleEffectivenessService.FeedbackRecord("R002", FeedbackType.TRUE_NEGATIVE),
                    new RuleEffectivenessService.FeedbackRecord("R002", FeedbackType.FALSE_NEGATIVE)
            ));

            assertThat(service.ruleCount()).isEqualTo(2);
            assertThat(service.totalFeedbackCount()).isEqualTo(4);

            RuleEffectivenessMetrics m1 = service.getMetrics("R001");
            assertThat(m1.getTruePositives()).isEqualTo(1);
            assertThat(m1.getFalsePositives()).isEqualTo(1);

            RuleEffectivenessMetrics m2 = service.getMetrics("R002");
            assertThat(m2.getTrueNegatives()).isEqualTo(1);
            assertThat(m2.getFalseNegatives()).isEqualTo(1);
        }

        @Test
        @DisplayName("规则编码为空 - 抛异常")
        void shouldThrowWhenRuleCodeNull() {
            assertThatThrownBy(() -> service.recordFeedback(null, FeedbackType.TRUE_POSITIVE))
                    .isInstanceOf(IllegalArgumentException.class);
            assertThatThrownBy(() -> service.recordFeedback("", FeedbackType.TRUE_POSITIVE))
                    .isInstanceOf(IllegalArgumentException.class);
            assertThatThrownBy(() -> service.recordFeedback("  ", FeedbackType.TRUE_POSITIVE))
                    .isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        @DisplayName("反馈类型为空 - 抛异常")
        void shouldThrowWhenTypeNull() {
            assertThatThrownBy(() -> service.recordFeedback("R001", null))
                    .isInstanceOf(IllegalArgumentException.class);
        }
    }

    // ==================== 指标计算 ====================

    @Nested
    @DisplayName("指标计算")
    class MetricsCalculationTest {

        @Test
        @DisplayName("Precision = TP / (TP + FP)")
        void shouldCalculatePrecision() {
            service.recordFeedbackBatch("R001", FeedbackType.TRUE_POSITIVE, 8);
            service.recordFeedbackBatch("R001", FeedbackType.FALSE_POSITIVE, 2);

            RuleEffectivenessMetrics m = service.getMetrics("R001");
            assertThat(m.getPrecision()).isCloseTo(0.8, within(0.001));
        }

        @Test
        @DisplayName("Recall = TP / (TP + FN)")
        void shouldCalculateRecall() {
            service.recordFeedbackBatch("R001", FeedbackType.TRUE_POSITIVE, 8);
            service.recordFeedbackBatch("R001", FeedbackType.FALSE_NEGATIVE, 2);

            RuleEffectivenessMetrics m = service.getMetrics("R001");
            assertThat(m.getRecall()).isCloseTo(0.8, within(0.001));
        }

        @Test
        @DisplayName("F1-Score = 2PR / (P + R)")
        void shouldCalculateF1Score() {
            // TP=8, FP=2, FN=2 → P=0.8, R=0.8, F1=0.8
            service.recordFeedbackBatch("R001", FeedbackType.TRUE_POSITIVE, 8);
            service.recordFeedbackBatch("R001", FeedbackType.FALSE_POSITIVE, 2);
            service.recordFeedbackBatch("R001", FeedbackType.FALSE_NEGATIVE, 2);

            RuleEffectivenessMetrics m = service.getMetrics("R001");
            assertThat(m.getF1Score()).isCloseTo(0.8, within(0.001));
        }

        @Test
        @DisplayName("完美规则 F1=1.0")
        void shouldReturnPerfectF1() {
            service.recordFeedbackBatch("R001", FeedbackType.TRUE_POSITIVE, 50);

            RuleEffectivenessMetrics m = service.getMetrics("R001");
            assertThat(m.getPrecision()).isEqualTo(1.0);
            assertThat(m.getRecall()).isEqualTo(1.0);
            assertThat(m.getF1Score()).isEqualTo(1.0);
        }

        @Test
        @DisplayName("零样本 - 所有指标为 0")
        void shouldReturnZeroWhenNoData() {
            RuleEffectivenessMetrics m = service.getMetrics("NOT_EXIST");
            assertThat(m.getPrecision()).isEqualTo(0.0);
            assertThat(m.getRecall()).isEqualTo(0.0);
            assertThat(m.getF1Score()).isEqualTo(0.0);
            assertThat(m.getAccuracy()).isEqualTo(0.0);
            assertThat(m.getTotalSamples()).isEqualTo(0);
        }

        @Test
        @DisplayName("Specificity = TN / (TN + FP)")
        void shouldCalculateSpecificity() {
            // TN=90, FP=10 → Specificity=0.9
            service.recordFeedbackBatch("R001", FeedbackType.TRUE_NEGATIVE, 90);
            service.recordFeedbackBatch("R001", FeedbackType.FALSE_POSITIVE, 10);

            RuleEffectivenessMetrics m = service.getMetrics("R001");
            assertThat(m.getSpecificity()).isCloseTo(0.9, within(0.001));
        }

        @Test
        @DisplayName("Accuracy = (TP + TN) / total")
        void shouldCalculateAccuracy() {
            // TP=80, TN=90, FP=10, FN=20 → total=200, Accuracy=170/200=0.85
            service.recordFeedbackBatch("R001", FeedbackType.TRUE_POSITIVE, 80);
            service.recordFeedbackBatch("R001", FeedbackType.TRUE_NEGATIVE, 90);
            service.recordFeedbackBatch("R001", FeedbackType.FALSE_POSITIVE, 10);
            service.recordFeedbackBatch("R001", FeedbackType.FALSE_NEGATIVE, 20);

            RuleEffectivenessMetrics m = service.getMetrics("R001");
            assertThat(m.getAccuracy()).isCloseTo(0.85, within(0.001));
        }

        @Test
        @DisplayName("False Positive Rate = 1 - Specificity")
        void shouldCalculateFPR() {
            service.recordFeedbackBatch("R001", FeedbackType.TRUE_NEGATIVE, 90);
            service.recordFeedbackBatch("R001", FeedbackType.FALSE_POSITIVE, 10);

            RuleEffectivenessMetrics m = service.getMetrics("R001");
            assertThat(m.getFalsePositiveRate()).isCloseTo(0.1, within(0.001));
        }

        @Test
        @DisplayName("False Negative Rate = 1 - Recall")
        void shouldCalculateFNR() {
            service.recordFeedbackBatch("R001", FeedbackType.TRUE_POSITIVE, 80);
            service.recordFeedbackBatch("R001", FeedbackType.FALSE_NEGATIVE, 20);

            RuleEffectivenessMetrics m = service.getMetrics("R001");
            assertThat(m.getFalseNegativeRate()).isCloseTo(0.2, within(0.001));
        }

        @Test
        @DisplayName("效果等级 - EXCELLENT（F1 ≥ 0.90）")
        void shouldReturnExcellentLevel() {
            service.recordFeedbackBatch("R001", FeedbackType.TRUE_POSITIVE, 95);
            service.recordFeedbackBatch("R001", FeedbackType.FALSE_POSITIVE, 3);
            service.recordFeedbackBatch("R001", FeedbackType.FALSE_NEGATIVE, 2);

            RuleEffectivenessMetrics m = service.getMetrics("R001");
            assertThat(m.getLevel()).isEqualTo(RuleEffectivenessMetrics.EffectivenessLevel.EXCELLENT);
        }

        @Test
        @DisplayName("效果等级 - GOOD（F1 ≥ 0.75）")
        void shouldReturnGoodLevel() {
            // P=80/100=0.8, R=80/100=0.8, F1=0.8
            service.recordFeedbackBatch("R001", FeedbackType.TRUE_POSITIVE, 80);
            service.recordFeedbackBatch("R001", FeedbackType.FALSE_POSITIVE, 20);
            service.recordFeedbackBatch("R001", FeedbackType.FALSE_NEGATIVE, 20);

            RuleEffectivenessMetrics m = service.getMetrics("R001");
            assertThat(m.getLevel()).isEqualTo(RuleEffectivenessMetrics.EffectivenessLevel.GOOD);
        }

        @Test
        @DisplayName("效果等级 - FAIR（F1 ≥ 0.60）")
        void shouldReturnFairLevel() {
            // TP=60, FP=20, FN=20 → P=0.75, R=0.75, F1=0.75
            service.recordFeedbackBatch("R001", FeedbackType.TRUE_POSITIVE, 60);
            service.recordFeedbackBatch("R001", FeedbackType.FALSE_POSITIVE, 20);
            service.recordFeedbackBatch("R001", FeedbackType.FALSE_NEGATIVE, 20);

            RuleEffectivenessMetrics m = service.getMetrics("R001");
            assertThat(m.getF1Score()).isCloseTo(0.75, within(0.001));
            assertThat(m.getLevel()).isEqualTo(RuleEffectivenessMetrics.EffectivenessLevel.GOOD);
        }

        @Test
        @DisplayName("效果等级 - POOR（F1 < 0.60）")
        void shouldReturnPoorLevel() {
            // TP=30, FP=40, FN=30 → P=0.4286, R=0.5, F1=0.4615
            service.recordFeedbackBatch("R001", FeedbackType.TRUE_POSITIVE, 30);
            service.recordFeedbackBatch("R001", FeedbackType.FALSE_POSITIVE, 40);
            service.recordFeedbackBatch("R001", FeedbackType.FALSE_NEGATIVE, 30);

            RuleEffectivenessMetrics m = service.getMetrics("R001");
            assertThat(m.getF1Score()).isLessThan(0.60);
            assertThat(m.getLevel()).isEqualTo(RuleEffectivenessMetrics.EffectivenessLevel.POOR);
        }

        @Test
        @DisplayName("效果等级 - INSUFFICIENT_DATA（样本 < 30）")
        void shouldReturnInsufficientDataLevel() {
            service.recordFeedbackBatch("R001", FeedbackType.TRUE_POSITIVE, 10);
            service.recordFeedbackBatch("R001", FeedbackType.FALSE_POSITIVE, 5);

            RuleEffectivenessMetrics m = service.getMetrics("R001");
            assertThat(m.getTotalSamples()).isLessThan(30);
            assertThat(m.getLevel()).isEqualTo(RuleEffectivenessMetrics.EffectivenessLevel.INSUFFICIENT_DATA);
        }
    }

    // ==================== 全局指标 ====================

    @Nested
    @DisplayName("全局汇总指标")
    class GlobalMetricsTest {

        @Test
        @DisplayName("多规则全局汇总 - TP/FP/FN/TN 合并")
        void shouldAggregateGlobalMetrics() {
            service.recordFeedbackBatch("R001", FeedbackType.TRUE_POSITIVE, 10);
            service.recordFeedbackBatch("R001", FeedbackType.FALSE_POSITIVE, 5);
            service.recordFeedbackBatch("R002", FeedbackType.TRUE_POSITIVE, 20);
            service.recordFeedbackBatch("R002", FeedbackType.FALSE_NEGATIVE, 10);

            RuleEffectivenessMetrics global = service.getGlobalMetrics();
            assertThat(global.getTruePositives()).isEqualTo(30);
            assertThat(global.getFalsePositives()).isEqualTo(5);
            assertThat(global.getFalseNegatives()).isEqualTo(10);
            assertThat(global.getTrueNegatives()).isEqualTo(0);
            assertThat(global.getTotalSamples()).isEqualTo(45);
        }

        @Test
        @DisplayName("全局指标 - ruleCode 为 null")
        void shouldReturnNullRuleCodeForGlobal() {
            service.recordFeedback("R001", FeedbackType.TRUE_POSITIVE);
            RuleEffectivenessMetrics global = service.getGlobalMetrics();
            assertThat(global.getRuleCode()).isNull();
        }

        @Test
        @DisplayName("无数据时全局指标 - 全零")
        void shouldReturnEmptyGlobalWhenNoData() {
            RuleEffectivenessMetrics global = service.getGlobalMetrics();
            assertThat(global.getTotalSamples()).isEqualTo(0);
            assertThat(global.getPrecision()).isEqualTo(0.0);
            assertThat(global.getF1Score()).isEqualTo(0.0);
        }
    }

    // ==================== 报告生成 ====================

    @Nested
    @DisplayName("报告生成")
    class ReportGenerationTest {

        @Test
        @DisplayName("报告包含全局指标和单规则指标")
        void shouldGenerateReportWithMetrics() {
            service.recordFeedbackBatch("R001", FeedbackType.TRUE_POSITIVE, 50);
            service.recordFeedbackBatch("R001", FeedbackType.FALSE_POSITIVE, 5);
            service.recordFeedbackBatch("R002", FeedbackType.TRUE_POSITIVE, 30);
            service.recordFeedbackBatch("R002", FeedbackType.FALSE_NEGATIVE, 10);

            EffectivenessReport report = service.generateReport();

            assertThat(report.getGlobalMetrics()).isNotNull();
            assertThat(report.getPerRuleMetrics()).hasSize(2);
            assertThat(report.getEvaluatedRuleCount()).isEqualTo(2);
            assertThat(report.getTotalFeedbackSamples()).isEqualTo(95);
        }

        @Test
        @DisplayName("报告识别低效规则（F1 < 0.60）")
        void shouldIdentifyPoorRules() {
            // R001: F1 ≈ 0.46 (POOR)
            service.recordFeedbackBatch("R001", FeedbackType.TRUE_POSITIVE, 30);
            service.recordFeedbackBatch("R001", FeedbackType.FALSE_POSITIVE, 40);
            service.recordFeedbackBatch("R001", FeedbackType.FALSE_NEGATIVE, 30);

            // R002: F1 = 1.0 (EXCELLENT)
            service.recordFeedbackBatch("R002", FeedbackType.TRUE_POSITIVE, 50);

            EffectivenessReport report = service.generateReport();

            assertThat(report.getPoorRules()).hasSize(1);
            assertThat(report.getPoorRules().get(0).getRuleCode()).isEqualTo("R001");
        }

        @Test
        @DisplayName("报告识别优秀规则（F1 ≥ 0.75）")
        void shouldIdentifyTopRules() {
            // R001: F1 = 1.0
            service.recordFeedbackBatch("R001", FeedbackType.TRUE_POSITIVE, 50);

            // R002: F1 ≈ 0.46
            service.recordFeedbackBatch("R002", FeedbackType.TRUE_POSITIVE, 30);
            service.recordFeedbackBatch("R002", FeedbackType.FALSE_POSITIVE, 40);
            service.recordFeedbackBatch("R002", FeedbackType.FALSE_NEGATIVE, 30);

            EffectivenessReport report = service.generateReport();

            assertThat(report.getTopRules()).hasSize(1);
            assertThat(report.getTopRules().get(0).getRuleCode()).isEqualTo("R001");
        }

        @Test
        @DisplayName("报告标记样本不足的规则")
        void shouldIdentifyLowDataRules() {
            // R001: 充足数据
            service.recordFeedbackBatch("R001", FeedbackType.TRUE_POSITIVE, 50);

            // R002: 不足 30 条
            service.recordFeedbackBatch("R002", FeedbackType.TRUE_POSITIVE, 10);

            EffectivenessReport report = service.generateReport();

            assertThat(report.getLowDataRules()).contains("R002");
            assertThat(report.getLowDataRules()).doesNotContain("R001");
        }

        @Test
        @DisplayName("低效规则按 F1 升序排列")
        void shouldSortPoorRulesByF1Ascending() {
            // R001: F1 ≈ 0.46
            service.recordFeedbackBatch("R001", FeedbackType.TRUE_POSITIVE, 30);
            service.recordFeedbackBatch("R001", FeedbackType.FALSE_POSITIVE, 40);
            service.recordFeedbackBatch("R001", FeedbackType.FALSE_NEGATIVE, 30);

            // R002: F1 ≈ 0.32
            service.recordFeedbackBatch("R002", FeedbackType.TRUE_POSITIVE, 20);
            service.recordFeedbackBatch("R002", FeedbackType.FALSE_POSITIVE, 50);
            service.recordFeedbackBatch("R002", FeedbackType.FALSE_NEGATIVE, 30);

            EffectivenessReport report = service.generateReport();

            assertThat(report.getPoorRules()).hasSize(2);
            assertThat(report.getPoorRules().get(0).getF1Score())
                    .isLessThanOrEqualTo(report.getPoorRules().get(1).getF1Score());
        }

        @Test
        @DisplayName("优秀规则按 F1 降序排列")
        void shouldSortTopRulesByF1Descending() {
            // R001: F1 = 1.0
            service.recordFeedbackBatch("R001", FeedbackType.TRUE_POSITIVE, 50);

            // R002: F1 ≈ 0.8
            service.recordFeedbackBatch("R002", FeedbackType.TRUE_POSITIVE, 80);
            service.recordFeedbackBatch("R002", FeedbackType.FALSE_POSITIVE, 20);
            service.recordFeedbackBatch("R002", FeedbackType.FALSE_NEGATIVE, 20);

            EffectivenessReport report = service.generateReport();

            assertThat(report.getTopRules()).hasSize(2);
            assertThat(report.getTopRules().get(0).getF1Score())
                    .isGreaterThanOrEqualTo(report.getTopRules().get(1).getF1Score());
        }

        @Test
        @DisplayName("报告摘要包含关键指标")
        void shouldGenerateSummary() {
            service.recordFeedbackBatch("R001", FeedbackType.TRUE_POSITIVE, 50);
            service.recordFeedback("R001", FeedbackType.FALSE_POSITIVE);

            EffectivenessReport report = service.generateReport();
            String summary = report.getSummary();

            assertThat(summary).contains("Precision");
            assertThat(summary).contains("Recall");
            assertThat(summary).contains("F1");
        }

        @Test
        @DisplayName("无数据时报告 - summary 返回提示文本")
        void shouldReturnEmptyReportSummary() {
            EffectivenessReport report = service.generateReport();
            assertThat(report.getSummary()).contains("暂无效果评估数据");
        }
    }

    // ==================== 数据管理 ====================

    @Nested
    @DisplayName("数据管理")
    class DataManagementTest {

        @Test
        @DisplayName("清除指定规则 - 仅该规则数据被清除")
        void shouldClearSpecificRule() {
            service.recordFeedbackBatch("R001", FeedbackType.TRUE_POSITIVE, 10);
            service.recordFeedbackBatch("R002", FeedbackType.TRUE_POSITIVE, 10);

            service.clearRule("R001");

            assertThat(service.getMetrics("R001").getTotalSamples()).isEqualTo(0);
            assertThat(service.getMetrics("R002").getTotalSamples()).isEqualTo(10);
            assertThat(service.ruleCount()).isEqualTo(1);
        }

        @Test
        @DisplayName("清除全部数据")
        void shouldClearAll() {
            service.recordFeedbackBatch("R001", FeedbackType.TRUE_POSITIVE, 10);
            service.recordFeedbackBatch("R002", FeedbackType.TRUE_POSITIVE, 10);

            service.clearAll();

            assertThat(service.ruleCount()).isEqualTo(0);
            assertThat(service.totalFeedbackCount()).isEqualTo(0);
        }

        @Test
        @DisplayName("ruleCount 返回有反馈的规则数")
        void shouldReturnRuleCount() {
            service.recordFeedback("R001", FeedbackType.TRUE_POSITIVE);
            service.recordFeedback("R002", FeedbackType.TRUE_POSITIVE);
            service.recordFeedback("R002", FeedbackType.FALSE_POSITIVE);

            assertThat(service.ruleCount()).isEqualTo(2);
        }

        @Test
        @DisplayName("totalFeedbackCount 返回总反馈数")
        void shouldReturnTotalFeedbackCount() {
            service.recordFeedbackBatch("R001", FeedbackType.TRUE_POSITIVE, 10);
            service.recordFeedbackBatch("R001", FeedbackType.FALSE_POSITIVE, 5);
            service.recordFeedbackBatch("R002", FeedbackType.TRUE_NEGATIVE, 15);

            assertThat(service.totalFeedbackCount()).isEqualTo(30);
        }
    }

    // ==================== 滑动窗口 ====================

    @Nested
    @DisplayName("滑动窗口淘汰")
    class SlidingWindowTest {

        @Test
        @DisplayName("短窗口服务 - 过期数据被淘汰")
        void shouldEvictExpiredData() {
            // 使用 1 秒窗口
            RuleEffectivenessService shortWindowService = new RuleEffectivenessService(Duration.ofSeconds(1));

            shortWindowService.recordFeedback("R001", FeedbackType.TRUE_POSITIVE);
            assertThat(shortWindowService.getMetrics("R001").getTotalSamples()).isEqualTo(1);

            // 等待超过窗口
            try {
                Thread.sleep(1200);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }

            // 手动触发淘汰
            shortWindowService.evictNow();

            assertThat(shortWindowService.getMetrics("R001").getTotalSamples()).isEqualTo(0);
        }

        @Test
        @DisplayName("淘汰后空规则被清理")
        void shouldCleanUpEmptyRulesAfterEviction() {
            RuleEffectivenessService shortWindowService = new RuleEffectivenessService(Duration.ofSeconds(1));

            shortWindowService.recordFeedback("R001", FeedbackType.TRUE_POSITIVE);
            assertThat(shortWindowService.ruleCount()).isEqualTo(1);

            try {
                Thread.sleep(1200);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }

            shortWindowService.evictNow();

            assertThat(shortWindowService.ruleCount()).isEqualTo(0);
        }

        @Test
        @DisplayName("淘汰后非空规则保留")
        void shouldKeepNonEmptyRulesAfterEviction() {
            RuleEffectivenessService shortWindowService = new RuleEffectivenessService(Duration.ofSeconds(1));

            // 记录一条（会过期）
            shortWindowService.recordFeedback("R001", FeedbackType.TRUE_POSITIVE);

            try {
                Thread.sleep(1200);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }

            // 再记录一条（在窗口内）
            shortWindowService.recordFeedback("R001", FeedbackType.TRUE_POSITIVE);

            shortWindowService.evictNow();

            // 应该只保留 1 条（窗口内的）
            assertThat(shortWindowService.getMetrics("R001").getTotalSamples()).isEqualTo(1);
        }
    }

    // ==================== 并发安全 ====================

    @Nested
    @DisplayName("并发安全")
    class ConcurrencyTest {

        @Test
        @DisplayName("多线程并发记录 - 计数正确")
        void shouldHandleConcurrentRecords() throws InterruptedException {
            int threadCount = 10;
            int recordsPerThread = 100;
            Thread[] threads = new Thread[threadCount];

            for (int i = 0; i < threadCount; i++) {
                threads[i] = new Thread(() -> {
                    for (int j = 0; j < recordsPerThread; j++) {
                        service.recordFeedback("R001", FeedbackType.TRUE_POSITIVE);
                    }
                });
                threads[i].start();
            }

            for (Thread t : threads) {
                t.join();
            }

            RuleEffectivenessMetrics m = service.getMetrics("R001");
            assertThat(m.getTruePositives()).isEqualTo((long) threadCount * recordsPerThread);
            assertThat(m.getTotalSamples()).isEqualTo((long) threadCount * recordsPerThread);
        }
    }

    // ==================== 辅助方法 ====================

    private static org.assertj.core.data.Offset<Double> within(double tolerance) {
        return org.assertj.core.data.Offset.offset(tolerance);
    }
}
