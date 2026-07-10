package com.njydsz.pmis.literule.benchmark;

import com.njydsz.pmis.literule.api.RuleResult;
import com.njydsz.pmis.literule.config.RuleAdminService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * {@link RuleStressTestService} 单元测试。
 *
 * <p>覆盖规则压测服务的参数校验、预热阶段、压测阶段、错误统计、
 * 直方图分桶、分位数计算等场景。
 *
 * @author ydsz-pmis-team
 */
@DisplayName("规则压测服务测试")
@ExtendWith(MockitoExtension.class)
class RuleStressTestServiceTest {

    @Mock
    private RuleAdminService ruleAdminService;

    private RuleStressTestService service;

    @BeforeEach
    void setUp() {
        service = new RuleStressTestService(ruleAdminService);
    }

    private List<Map<String, Object>> factsListOf(Map<String, Object>... facts) {
        List<Map<String, Object>> list = new ArrayList<>();
        Collections.addAll(list, facts);
        return list;
    }

    private Map<String, Object> facts(String key, Object value) {
        return Map.of(key, value);
    }

    // ==================== run：参数校验 ====================

    @Nested
    @DisplayName("run：参数校验")
    class RunParameterValidationTest {

        @Test
        @DisplayName("正常场景：threads<=0 时默认设为 1")
        void shouldDefaultThreadsToOneWhenNonPositive() {
            when(ruleAdminService.dryRun(anyString(), any()))
                    .thenReturn(List.of(RuleResult.notTriggered("R1")));

            RuleStressTestService.StressTestResult result = service.run(
                    "R1", factsListOf(facts("k", 1)), 0, 3, 0);

            // 1 线程 3 迭代 = 3 次 dryRun（不含预热）
            assertThat(result.getTotalExecutions()).isEqualTo(3);
            verify(ruleAdminService, times(3)).dryRun(eq("R1"), any());
        }

        @Test
        @DisplayName("正常场景：threads 为负数时默认设为 1")
        void shouldDefaultThreadsToOneWhenNegative() {
            when(ruleAdminService.dryRun(anyString(), any()))
                    .thenReturn(List.of(RuleResult.notTriggered("R1")));

            RuleStressTestService.StressTestResult result = service.run(
                    "R1", factsListOf(facts("k", 1)), -5, 2, 0);

            assertThat(result.getTotalExecutions()).isEqualTo(2);
            verify(ruleAdminService, times(2)).dryRun(eq("R1"), any());
        }

        @Test
        @DisplayName("边界场景：iterations<0 时设为 0，无执行")
        void shouldSetIterationsToZeroWhenNegative() {
            RuleStressTestService.StressTestResult result = service.run(
                    "R1", factsListOf(facts("k", 1)), 1, -10, 0);

            assertThat(result.getTotalExecutions()).isEqualTo(0);
            assertThat(result.getErrorCount()).isEqualTo(0);
            assertThat(result.getErrorRate()).isEqualTo(0.0);
            // 预热 0 + 压测 0 = 0 次 dryRun
            verify(ruleAdminService, times(0)).dryRun(anyString(), any());
        }

        @Test
        @DisplayName("边界场景：iterations=0 时不执行压测")
        void shouldNotExecuteWhenIterationsZero() {
            RuleStressTestService.StressTestResult result = service.run(
                    "R1", factsListOf(facts("k", 1)), 2, 0, 0);

            assertThat(result.getTotalExecutions()).isEqualTo(0);
            assertThat(result.getP50Ms()).isEqualTo(0.0);
            assertThat(result.getP95Ms()).isEqualTo(0.0);
            assertThat(result.getP99Ms()).isEqualTo(0.0);
            assertThat(result.getHistogram()).isEmpty();
        }

        @Test
        @DisplayName("正常场景：warmupIterations<0 时设为 0，不影响压测")
        void shouldSetWarmupToZeroWhenNegative() {
            when(ruleAdminService.dryRun(anyString(), any()))
                    .thenReturn(List.of(RuleResult.notTriggered("R1")));

            RuleStressTestService.StressTestResult result = service.run(
                    "R1", factsListOf(facts("k", 1)), 1, 2, -100);

            // 预热被置为 0，仅压测 2 次
            assertThat(result.getTotalExecutions()).isEqualTo(2);
            verify(ruleAdminService, times(2)).dryRun(eq("R1"), any());
        }

        @Test
        @DisplayName("正常场景：factsList 为 null 时使用空 Map")
        void shouldUseEmptyMapWhenFactsListNull() {
            when(ruleAdminService.dryRun(anyString(), any()))
                    .thenReturn(List.of(RuleResult.notTriggered("R1")));

            RuleStressTestService.StressTestResult result = service.run(
                    "R1", null, 1, 1, 0);

            assertThat(result.getTotalExecutions()).isEqualTo(1);
            verify(ruleAdminService, times(1)).dryRun(eq("R1"), any());
        }

        @Test
        @DisplayName("正常场景：factsList 为空列表时使用空 Map")
        void shouldUseEmptyMapWhenFactsListEmpty() {
            when(ruleAdminService.dryRun(anyString(), any()))
                    .thenReturn(List.of(RuleResult.notTriggered("R1")));

            RuleStressTestService.StressTestResult result = service.run(
                    "R1", new ArrayList<>(), 1, 1, 0);

            assertThat(result.getTotalExecutions()).isEqualTo(1);
            verify(ruleAdminService, times(1)).dryRun(eq("R1"), any());
        }

        @Test
        @DisplayName("正常场景：ruleCode 为 null 时透传给 dryRun")
        void shouldPassNullRuleCodeToDryRun() {
            when(ruleAdminService.dryRun(eq(null), any()))
                    .thenReturn(List.of(RuleResult.notTriggered("R1")));

            RuleStressTestService.StressTestResult result = service.run(
                    null, factsListOf(facts("k", 1)), 1, 1, 0);

            assertThat(result.getTotalExecutions()).isEqualTo(1);
            verify(ruleAdminService, times(1)).dryRun(eq(null), any());
        }
    }

    // ==================== run：预热阶段 ====================

    @Nested
    @DisplayName("run：预热阶段")
    class RunWarmupTest {

        @Test
        @DisplayName("正常场景：预热阶段异常被忽略，不影响压测")
        void shouldIgnoreWarmupExceptions() {
            // warmup 抛异常，压测正常
            when(ruleAdminService.dryRun(anyString(), any()))
                    .thenThrow(new RuntimeException("warmup boom"))
                    .thenReturn(List.of(RuleResult.notTriggered("R1")));

            RuleStressTestService.StressTestResult result = service.run(
                    "R1", factsListOf(facts("k", 1)), 1, 1, 1);

            // 1 预热 + 1 压测 = 2 次 dryRun
            assertThat(result.getTotalExecutions()).isEqualTo(1);
            assertThat(result.getErrorCount()).isEqualTo(0);
            verify(ruleAdminService, times(2)).dryRun(eq("R1"), any());
        }

        @Test
        @DisplayName("正常场景：多次预热不统计耗时")
        void shouldExecuteWarmupWithoutCounting() {
            when(ruleAdminService.dryRun(anyString(), any()))
                    .thenReturn(List.of(RuleResult.notTriggered("R1")));

            RuleStressTestService.StressTestResult result = service.run(
                    "R1", factsListOf(facts("k", 1)), 1, 1, 5);

            // 5 预热 + 1 压测 = 6 次 dryRun，但 totalExecutions 仅 1
            assertThat(result.getTotalExecutions()).isEqualTo(1);
            verify(ruleAdminService, times(6)).dryRun(eq("R1"), any());
        }

        @Test
        @DisplayName("正常场景：预热阶段循环采样 facts 列表")
        void shouldCycleFactsDuringWarmup() {
            when(ruleAdminService.dryRun(anyString(), any()))
                    .thenReturn(List.of(RuleResult.notTriggered("R1")));

            // 3 条 facts，预热 3 次 + 压测 0 次
            service.run("R1",
                    factsListOf(facts("a", 1), facts("b", 2), facts("c", 3)),
                    1, 0, 3);

            verify(ruleAdminService, times(3)).dryRun(eq("R1"), any());
        }
    }

    // ==================== run：压测统计 ====================

    @Nested
    @DisplayName("run：压测统计")
    class RunStatisticsTest {

        @Test
        @DisplayName("正常场景：单线程多次迭代统计正确")
        void shouldAggregateSingleThreadStats() {
            when(ruleAdminService.dryRun(anyString(), any()))
                    .thenReturn(List.of(RuleResult.notTriggered("R1")));

            RuleStressTestService.StressTestResult result = service.run(
                    "R1", factsListOf(facts("k", 1)), 1, 100, 0);

            assertThat(result.getTotalExecutions()).isEqualTo(100);
            assertThat(result.getErrorCount()).isEqualTo(0);
            assertThat(result.getErrorRate()).isEqualTo(0.0);
            assertThat(result.getQps()).isGreaterThan(0.0);
            assertThat(result.getTotalTimeMs()).isGreaterThanOrEqualTo(0.0);
        }

        @Test
        @DisplayName("正常场景：多线程并发压测统计正确")
        void shouldAggregateMultiThreadStats() {
            when(ruleAdminService.dryRun(anyString(), any()))
                    .thenReturn(List.of(RuleResult.notTriggered("R1")));

            RuleStressTestService.StressTestResult result = service.run(
                    "R1", factsListOf(facts("k", 1)), 4, 50, 0);

            // 4 线程 × 50 迭代 = 200 次
            assertThat(result.getTotalExecutions()).isEqualTo(200);
            assertThat(result.getErrorCount()).isEqualTo(0);
        }

        @Test
        @DisplayName("正常场景：多线程多 facts 循环采样")
        void shouldCycleFactsAcrossThreads() {
            when(ruleAdminService.dryRun(anyString(), any()))
                    .thenReturn(List.of(RuleResult.notTriggered("R1")));

            // 2 线程、3 条 facts、每线程 6 次迭代
            RuleStressTestService.StressTestResult result = service.run(
                    "R1",
                    factsListOf(facts("a", 1), facts("b", 2), facts("c", 3)),
                    2, 6, 0);

            assertThat(result.getTotalExecutions()).isEqualTo(12);
            // 2 线程 × (6 压测) = 12 次
            verify(ruleAdminService, times(12)).dryRun(eq("R1"), any());
        }

        @Test
        @DisplayName("正常场景：分位数 P50/P95/P99 单调递增")
        void shouldHaveMonotonicPercentiles() {
            when(ruleAdminService.dryRun(anyString(), any()))
                    .thenReturn(List.of(RuleResult.notTriggered("R1")));

            RuleStressTestService.StressTestResult result = service.run(
                    "R1", factsListOf(facts("k", 1)), 1, 200, 10);

            assertThat(result.getP50Ms()).isLessThanOrEqualTo(result.getP95Ms());
            assertThat(result.getP95Ms()).isLessThanOrEqualTo(result.getP99Ms());
        }

        @Test
        @DisplayName("正常场景：QPS 非负且合理")
        void shouldComputeNonNegativeQps() {
            when(ruleAdminService.dryRun(anyString(), any()))
                    .thenReturn(List.of(RuleResult.notTriggered("R1")));

            RuleStressTestService.StressTestResult result = service.run(
                    "R1", factsListOf(facts("k", 1)), 2, 50, 0);

            assertThat(result.getQps()).isGreaterThanOrEqualTo(0.0);
            // QPS = totalExecutions * 1000 / totalTimeMs
            // 当 totalTimeMs 接近 0 时 QPS 可能很大，仅校验非负
        }
    }

    // ==================== run：异常统计 ====================

    @Nested
    @DisplayName("run：异常统计")
    class RunErrorStatsTest {

        @Test
        @DisplayName("异常场景：dryRun 抛异常计入 errorCount")
        void shouldCountErrorsWhenDryRunThrows() {
            when(ruleAdminService.dryRun(anyString(), any()))
                    .thenThrow(new RuntimeException("eval boom"));

            RuleStressTestService.StressTestResult result = service.run(
                    "R1", factsListOf(facts("k", 1)), 1, 10, 0);

            assertThat(result.getTotalExecutions()).isEqualTo(10);
            assertThat(result.getErrorCount()).isEqualTo(10);
            assertThat(result.getErrorRate()).isCloseTo(1.0, within(1e-9));
        }

        @Test
        @DisplayName("异常场景：部分失败时 errorRate 介于 0~1")
        void shouldComputeErrorRateWhenPartialFailures() {
            AtomicInteger counter = new AtomicInteger(0);
            when(ruleAdminService.dryRun(anyString(), any())).thenAnswer(inv -> {
                if (counter.incrementAndGet() % 2 == 0) {
                    throw new RuntimeException("even failure");
                }
                return List.of(RuleResult.notTriggered("R1"));
            });

            RuleStressTestService.StressTestResult result = service.run(
                    "R1", factsListOf(facts("k", 1)), 1, 10, 0);

            assertThat(result.getTotalExecutions()).isEqualTo(10);
            assertThat(result.getErrorCount()).isEqualTo(5);
            assertThat(result.getErrorRate()).isCloseTo(0.5, within(1e-9));
        }

        @Test
        @DisplayName("异常场景：错误信息最多收集 50 条")
        void shouldCapErrorsAt50() {
            when(ruleAdminService.dryRun(anyString(), any()))
                    .thenThrow(new IllegalStateException("err"));

            RuleStressTestService.StressTestResult result = service.run(
                    "R1", factsListOf(facts("k", 1)), 1, 100, 0);

            assertThat(result.getErrorCount()).isEqualTo(100);
            assertThat(result.getErrors()).hasSize(50);
            assertThat(result.getErrors().get(0)).contains("IllegalStateException");
            assertThat(result.getErrors().get(0)).contains("err");
        }

        @Test
        @DisplayName("异常场景：dryRun 返回 null 不计入错误")
        void shouldNotCountNullReturnAsError() {
            when(ruleAdminService.dryRun(anyString(), any()))
                    .thenReturn(null);

            RuleStressTestService.StressTestResult result = service.run(
                    "R1", factsListOf(facts("k", 1)), 1, 5, 0);

            assertThat(result.getTotalExecutions()).isEqualTo(5);
            assertThat(result.getErrorCount()).isEqualTo(0);
            assertThat(result.getErrorRate()).isEqualTo(0.0);
        }

        @Test
        @DisplayName("异常场景：dryRun 返回空列表不计入错误")
        void shouldNotCountEmptyReturnAsError() {
            when(ruleAdminService.dryRun(anyString(), any()))
                    .thenReturn(Collections.emptyList());

            RuleStressTestService.StressTestResult result = service.run(
                    "R1", factsListOf(facts("k", 1)), 1, 5, 0);

            assertThat(result.getTotalExecutions()).isEqualTo(5);
            assertThat(result.getErrorCount()).isEqualTo(0);
        }
    }

    // ==================== run：直方图 ====================

    @Nested
    @DisplayName("run：直方图")
    class RunHistogramTest {

        @Test
        @DisplayName("边界场景：无样本时直方图为空")
        void shouldReturnEmptyHistogramWhenNoSamples() {
            RuleStressTestService.StressTestResult result = service.run(
                    "R1", factsListOf(facts("k", 1)), 1, 0, 0);

            assertThat(result.getHistogram()).isEmpty();
        }

        @Test
        @DisplayName("正常场景：有样本时直方图非空")
        void shouldReturnNonEmptyHistogramWhenHasSamples() {
            when(ruleAdminService.dryRun(anyString(), any()))
                    .thenReturn(List.of(RuleResult.notTriggered("R1")));

            RuleStressTestService.StressTestResult result = service.run(
                    "R1", factsListOf(facts("k", 1)), 1, 50, 0);

            assertThat(result.getHistogram()).isNotEmpty();
            // 所有桶的 count 之和应等于总执行次数
            int total = result.getHistogram().stream()
                    .mapToInt(RuleStressTestService.HistogramBucket::getCount)
                    .sum();
            assertThat(total).isEqualTo(result.getTotalExecutions());
        }

        @Test
        @DisplayName("正常场景：直方图桶标签格式合法")
        void shouldHaveValidBucketLabels() {
            when(ruleAdminService.dryRun(anyString(), any()))
                    .thenReturn(List.of(RuleResult.notTriggered("R1")));

            RuleStressTestService.StressTestResult result = service.run(
                    "R1", factsListOf(facts("k", 1)), 1, 100, 0);

            assertThat(result.getHistogram()).isNotEmpty();
            for (RuleStressTestService.HistogramBucket bucket : result.getHistogram()) {
                assertThat(bucket.getBucketLabel()).isNotBlank();
                assertThat(bucket.getCount()).isGreaterThanOrEqualTo(0);
            }
        }
    }

    // ==================== run：综合场景 ====================

    @Nested
    @DisplayName("run：综合场景")
    class RunCompositeTest {

        @Test
        @DisplayName("正常场景：预热 + 压测 + 错误混合场景")
        void shouldHandleMixedWarmupAndErrors() {
            AtomicInteger counter = new AtomicInteger(0);
            when(ruleAdminService.dryRun(anyString(), any())).thenAnswer(inv -> {
                int n = counter.incrementAndGet();
                // 前 5 次（预热）+ 第 6/8/10 次失败
                if (n == 6 || n == 8 || n == 10) {
                    throw new RuntimeException("eval fail " + n);
                }
                return List.of(RuleResult.notTriggered("R1"));
            });

            RuleStressTestService.StressTestResult result = service.run(
                    "R1", factsListOf(facts("k", 1)), 1, 5, 5);

            // 预热 5 + 压测 5 = 10 次调用
            verify(ruleAdminService, times(10)).dryRun(eq("R1"), any());
            // 压测 5 次，其中 3 次失败（n=6,8,10）
            assertThat(result.getTotalExecutions()).isEqualTo(5);
            assertThat(result.getErrorCount()).isEqualTo(3);
            assertThat(result.getErrorRate()).isCloseTo(0.6, within(1e-9));
            assertThat(result.getErrors()).hasSize(3);
        }

        @Test
        @DisplayName("正常场景：多线程预热 + 多迭代压测")
        void shouldHandleMultiThreadWithWarmup() {
            when(ruleAdminService.dryRun(anyString(), any()))
                    .thenReturn(List.of(RuleResult.notTriggered("R1")));

            RuleStressTestService.StressTestResult result = service.run(
                    "R1", factsListOf(facts("k", 1)), 3, 30, 10);

            // 3 线程 × (10 预热 + 30 压测) = 120 次
            verify(ruleAdminService, times(120)).dryRun(eq("R1"), any());
            assertThat(result.getTotalExecutions()).isEqualTo(90);
            assertThat(result.getErrorCount()).isEqualTo(0);
            assertThat(result.getHistogram()).isNotEmpty();
        }

        @Test
        @DisplayName("正常场景：完整结果对象字段均被填充")
        void shouldPopulateAllResultFields() {
            when(ruleAdminService.dryRun(anyString(), any()))
                    .thenReturn(List.of(RuleResult.notTriggered("R1")));

            RuleStressTestService.StressTestResult result = service.run(
                    "R1", factsListOf(facts("k", 1)), 2, 20, 5);

            assertThat(result.getTotalExecutions()).isEqualTo(40);
            assertThat(result.getTotalTimeMs()).isGreaterThanOrEqualTo(0.0);
            assertThat(result.getQps()).isGreaterThanOrEqualTo(0.0);
            assertThat(result.getP50Ms()).isGreaterThanOrEqualTo(0.0);
            assertThat(result.getP95Ms()).isGreaterThanOrEqualTo(0.0);
            assertThat(result.getP99Ms()).isGreaterThanOrEqualTo(0.0);
            assertThat(result.getErrorRate()).isGreaterThanOrEqualTo(0.0);
            assertThat(result.getErrorCount()).isEqualTo(0);
            assertThat(result.getErrors()).isNotNull();
            assertThat(result.getHistogram()).isNotNull();
        }
    }
}
