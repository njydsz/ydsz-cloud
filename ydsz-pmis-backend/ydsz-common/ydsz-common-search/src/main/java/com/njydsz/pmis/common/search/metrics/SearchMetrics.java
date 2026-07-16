package com.njydsz.common.search.metrics;

import java.time.Duration;
import java.util.concurrent.atomic.AtomicLong;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;

/**
 * 搜索指标收集
 * <p>
 * 通过 Micrometer 收集搜索 QPS、延迟、零结果率等指标。
 * 当 Micrometer 不可用时降级为内部计数器。
 *
 * @author ydsz-team
 * @since 1.4.0
 */
public class SearchMetrics {

    private final MeterRegistry meterRegistry;

    // 内部计数器（Micrometer 不可用时使用）
    private final AtomicLong totalSearches = new AtomicLong(0);
    private final AtomicLong zeroResultSearches = new AtomicLong(0);
    private final AtomicLong totalIndexOps = new AtomicLong(0);
    private final AtomicLong failedIndexOps = new AtomicLong(0);

    // Micrometer 指标
    private Counter searchCounter;
    private Counter zeroResultCounter;
    private Timer searchTimer;
    private Counter indexOpCounter;
    private Counter indexFailedCounter;

    public SearchMetrics(MeterRegistry meterRegistry) {
        this.meterRegistry = meterRegistry;
        initMetrics();
    }

    private void initMetrics() {
        if (meterRegistry == null) {
            return;
        }
        searchCounter = Counter.builder("ydsz.search.requests")
                .description("Total search requests")
                .register(meterRegistry);
        zeroResultCounter = Counter.builder("ydsz.search.zero_results")
                .description("Search requests with zero results")
                .register(meterRegistry);
        searchTimer = Timer.builder("ydsz.search.duration")
                .description("Search duration")
                .register(meterRegistry);
        indexOpCounter = Counter.builder("ydsz.search.index_ops")
                .description("Total index operations")
                .register(meterRegistry);
        indexFailedCounter = Counter.builder("ydsz.search.index_failed")
                .description("Failed index operations")
                .register(meterRegistry);
    }

    /**
     * 记录搜索请求
     *
     * @param tookMs    耗时（毫秒）
     * @param totalHits 结果数
     */
    public void recordSearch(long tookMs, long totalHits) {
        totalSearches.incrementAndGet();
        if (totalHits == 0) {
            zeroResultSearches.incrementAndGet();
        }

        if (searchCounter != null) {
            searchCounter.increment();
        }
        if (totalHits == 0 && zeroResultCounter != null) {
            zeroResultCounter.increment();
        }
        if (searchTimer != null) {
            searchTimer.record(Duration.ofMillis(tookMs));
        }
    }

    /**
     * 记录索引操作
     *
     * @param success 是否成功
     */
    public void recordIndexOp(boolean success) {
        totalIndexOps.incrementAndGet();
        if (!success) {
            failedIndexOps.incrementAndGet();
        }

        if (indexOpCounter != null) {
            indexOpCounter.increment();
        }
        if (!success && indexFailedCounter != null) {
            indexFailedCounter.increment();
        }
    }

    /**
     * 获取零结果率
     */
    public double getZeroResultRate() {
        long total = totalSearches.get();
        if (total == 0) {
            return 0.0;
        }
        return (double) zeroResultSearches.get() / total;
    }

    /**
     * 获取索引失败率
     */
    public double getIndexFailureRate() {
        long total = totalIndexOps.get();
        if (total == 0) {
            return 0.0;
        }
        return (double) failedIndexOps.get() / total;
    }

    public long getTotalSearches() {
        return totalSearches.get();
    }

    public long getZeroResultSearches() {
        return zeroResultSearches.get();
    }

    public long getTotalIndexOps() {
        return totalIndexOps.get();
    }

    public long getFailedIndexOps() {
        return failedIndexOps.get();
    }
}
