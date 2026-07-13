package com.njydsz.pmis.literule.server.benchmark;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.LongAdder;

import org.springframework.stereotype.Service;

import com.njydsz.pmis.literule.api.RuleResult;
import com.njydsz.pmis.literule.server.config.RuleAdminService;

import lombok.Data;
import lombok.extern.slf4j.Slf4j;

/**
 * 规则压测服务（P2-9）
 *
 * <p>对规则引擎进行并发压测，统计 QPS、P50/P95/P99 耗时、错误率等指标，
 * 用于规则变更前的性能回归验证与容量评估。
 *
 * <h3>工作流程</h3>
 * <ol>
 *   <li>预热阶段：使用 warmupIterations 次迭代预热 JIT，不统计耗时</li>
 *   <li>压测阶段：使用 threads 个线程并发执行 iterations 次迭代</li>
 *   <li>统计阶段：聚合所有线程的耗时样本，计算分位数</li>
 * </ol>
 *
 * <h3>使用方式</h3>
 * <pre>
 * RuleStressTestService service = new RuleStressTestService(ruleAdminService);
 * StressTestResult result = service.run("BUDGET_WARN", factsList, 10, 1000, 100);
 * </pre>
 *
 * @author ydsz-pmis-team
 * @since 1.6.0
 */
@Slf4j
@Service
public class RuleStressTestService {

    private final RuleAdminService ruleAdminService;

    public RuleStressTestService(RuleAdminService ruleAdminService) {
        this.ruleAdminService = ruleAdminService;
    }

    /**
     * 执行规则压测
     *
     * @param ruleCode          目标规则编码（null 表示对全部启用规则求值）
     * @param factsList         事实数据列表（将循环采样作为每次迭代的输入；为空时使用空 Map）
     * @param threads           并发线程数
     * @param iterations        每个线程的迭代次数（不含预热）
     * @param warmupIterations  每个线程的预热迭代次数（不统计）
     * @return 压测结果
     */
    public StressTestResult run(String ruleCode,
                                 List<Map<String, Object>> factsList,
                                 int threads,
                                 int iterations,
                                 int warmupIterations) {
        // 参数校验
        if (threads <= 0) threads = 1;
        if (iterations < 0) iterations = 0;
        if (warmupIterations < 0) warmupIterations = 0;
        if (factsList == null || factsList.isEmpty()) {
            factsList = Collections.singletonList(Collections.emptyMap());
        }

        // 用于收集所有线程的耗时样本（纳秒）
        ConcurrentLinkedQueue<Long> samples = new ConcurrentLinkedQueue<>();
        // 错误计数
        LongAdder errorCount = new LongAdder();
        // 错误信息（最多收集 50 条，避免内存膨胀）
        ConcurrentLinkedQueue<String> errors = new ConcurrentLinkedQueue<>();
        AtomicInteger errorSampleCount = new AtomicInteger(0);
        // 总执行次数（不含预热）
        LongAdder totalExecutions = new LongAdder();

        // 每个线程分配独立的事实数据游标，避免共享竞争
        ExecutorService executor = Executors.newFixedThreadPool(threads);
        final List<Map<String, Object>> finalFactsList = factsList;
        final int finalIterations = iterations;
        final int finalWarmup = warmupIterations;

        long startNanos = System.nanoTime();
        try {
            for (int t = 0; t < threads; t++) {
                final int threadIndex = t;
                executor.submit(() -> {
                    try {
                        // 预热阶段
                        for (int i = 0; i < finalWarmup; i++) {
                            Map<String, Object> facts = finalFactsList.get(
                                    (threadIndex + i) % finalFactsList.size());
                            try {
                                ruleAdminService.dryRun(ruleCode, facts);
                            } catch (Exception ignored) {
                                // 预热阶段忽略异常
                            }
                        }
                        // 压测阶段
                        for (int i = 0; i < finalIterations; i++) {
                            Map<String, Object> facts = finalFactsList.get(
                                    (threadIndex + i) % finalFactsList.size());
                            long s = System.nanoTime();
                            try {
                                List<RuleResult> results = ruleAdminService.dryRun(ruleCode, facts);
                                // 触发结果消费，避免 JIT 死码消除
                                if (results != null && !results.isEmpty()) {
                                    // no-op
                                }
                            } catch (Exception e) {
                                errorCount.increment();
                                if (errorSampleCount.getAndIncrement() < 50) {
                                    errors.add(e.getClass().getSimpleName() + ": " + e.getMessage());
                                }
                            }
                            long elapsed = System.nanoTime() - s;
                            samples.add(elapsed);
                            totalExecutions.increment();
                        }
                    } catch (Throwable t1) {
                        log.warn("[StressTest] 线程 {} 异常: {}", threadIndex, t1.getMessage());
                    }
                });
            }
        } finally {
            executor.shutdown();
            try {
                if (!executor.awaitTermination(60, TimeUnit.MINUTES)) {
                    log.warn("[StressTest] 压测线程池未在 60 分钟内完成，强制关闭");
                    executor.shutdownNow();
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                executor.shutdownNow();
            }
        }
        long totalNanos = System.nanoTime() - startNanos;

        // 聚合样本计算分位数
        List<Long> sorted = new ArrayList<>(samples);
        Collections.sort(sorted);
        long totalExecutionsLong = totalExecutions.sum();
        double totalTimeMs = totalNanos / 1_000_000.0;
        double qps = totalTimeMs > 0 ? (totalExecutionsLong * 1000.0 / totalTimeMs) : 0;
        double p50Ms = percentile(sorted, 50) / 1_000_000.0;
        double p95Ms = percentile(sorted, 95) / 1_000_000.0;
        double p99Ms = percentile(sorted, 99) / 1_000_000.0;
        double errorRate = totalExecutionsLong > 0
                ? (double) errorCount.sum() / totalExecutionsLong
                : 0.0;

        StressTestResult result = new StressTestResult();
        result.setTotalExecutions(totalExecutionsLong);
        result.setTotalTimeMs(totalTimeMs);
        result.setQps(qps);
        result.setP50Ms(p50Ms);
        result.setP95Ms(p95Ms);
        result.setP99Ms(p99Ms);
        result.setErrorRate(errorRate);
        result.setErrorCount(errorCount.sum());
        result.setErrors(new ArrayList<>(errors));
        // 直方图分桶（按毫秒分桶，最多 30 桶）
        result.setHistogram(buildHistogram(sorted));
        log.info("[StressTest] 压测完成: ruleCode={}, threads={}, iterations={}, qps={}, p50={}ms, p95={}ms, p99={}ms, errorRate={}",
                ruleCode, threads, finalIterations,
                String.format("%.2f", qps),
                String.format("%.3f", p50Ms),
                String.format("%.3f", p95Ms),
                String.format("%.3f", p99Ms),
                String.format("%.4f", errorRate));
        return result;
    }

    /**
     * 计算分位数（线性插值法）
     *
     * @param sorted 已升序排序的样本
     * @param p      百分位（0-100）
     * @return 分位数值（纳秒）
     */
    private long percentile(List<Long> sorted, double p) {
        if (sorted.isEmpty()) return 0;
        if (p <= 0) return sorted.get(0);
        if (p >= 100) return sorted.get(sorted.size() - 1);
        // 线性插值
        double index = (p / 100.0) * (sorted.size() - 1);
        int lower = (int) Math.floor(index);
        int upper = (int) Math.ceil(index);
        if (lower == upper) return sorted.get(lower);
        double fraction = index - lower;
        return (long) (sorted.get(lower) + fraction * (sorted.get(upper) - sorted.get(lower)));
    }

    /**
     * 构建耗时直方图（按毫秒分桶）
     *
     * @param sorted 已升序排序的样本（纳秒）
     * @return 直方图（每桶包含 bucketLabel 与 count）
     */
    private List<HistogramBucket> buildHistogram(List<Long> sorted) {
        List<HistogramBucket> buckets = new ArrayList<>();
        if (sorted.isEmpty()) return buckets;
        long minNs = sorted.get(0);
        long maxNs = sorted.get(sorted.size() - 1);
        double minMs = minNs / 1_000_000.0;
        double maxMs = maxNs / 1_000_000.0;
        // 至少 1ms 范围，避免分桶为 0
        if (maxMs - minMs < 0.001) {
            HistogramBucket b = new HistogramBucket();
            b.setBucketLabel(String.format("%.3f", minMs));
            b.setCount(sorted.size());
            buckets.add(b);
            return buckets;
        }
        // 分 20 桶
        int bucketCount = 20;
        double bucketSize = (maxMs - minMs) / bucketCount;
        if (bucketSize <= 0) bucketSize = 0.001;
        int[] counts = new int[bucketCount];
        for (long ns : sorted) {
            double ms = ns / 1_000_000.0;
            int idx = (int) ((ms - minMs) / bucketSize);
            if (idx >= bucketCount) idx = bucketCount - 1;
            if (idx < 0) idx = 0;
            counts[idx]++;
        }
        for (int i = 0; i < bucketCount; i++) {
            HistogramBucket b = new HistogramBucket();
            double low = minMs + i * bucketSize;
            double high = low + bucketSize;
            b.setBucketLabel(String.format("%.3f-%.3f", low, high));
            b.setCount(counts[i]);
            buckets.add(b);
        }
        return buckets;
    }

    /**
     * 压测结果
     */
    @Data
    public static class StressTestResult {
        /** 总执行次数（不含预热） */
        private long totalExecutions;
        /** 总耗时（毫秒） */
        private double totalTimeMs;
        /** QPS（每秒查询数） */
        private double qps;
        /** P50 耗时（毫秒） */
        private double p50Ms;
        /** P95 耗时（毫秒） */
        private double p95Ms;
        /** P99 耗时（毫秒） */
        private double p99Ms;
        /** 错误率（0~1） */
        private double errorRate;
        /** 错误次数 */
        private long errorCount;
        /** 错误信息列表（最多 50 条） */
        private List<String> errors;
        /** 耗时直方图（按毫秒分桶） */
        private List<HistogramBucket> histogram;
    }

    /**
     * 直方图桶
     */
    @Data
    public static class HistogramBucket {
        /** 桶标签（如 "0.001-0.005" 表示 0.001ms ~ 0.005ms） */
        private String bucketLabel;
        /** 该桶内的样本数 */
        private int count;
    }
}
