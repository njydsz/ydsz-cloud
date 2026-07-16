package com.njydsz.pmis.common.sentry.metrics;

import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.atomic.DoubleAdder;
import java.util.concurrent.atomic.LongAdder;

import com.njydsz.pmis.common.sentry.spi.MetricsCollector;

import lombok.extern.slf4j.Slf4j;

/**
 * 内存降级指标采集器
 *
 * <p>当 Micrometer 不可用或熔断时，自动降级为内存计数器。
 * 基于 LongAdder / DoubleAdder 实现，零依赖，始终可用。
 *
 * @author ydsz-pmis-team
 * @since 1.5.0
 */
@Slf4j
public class InMemoryMetricsCollector implements MetricsCollector {

    private final ConcurrentHashMap<String, DoubleAdder> counters = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, AtomicReference<Double>> gauges = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, LongAdder> timerCounts = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, LongAdder> timerTotals = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, LongAdder> histogramCounts = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, DoubleAdder> histogramTotals = new ConcurrentHashMap<>();

    @Override
    public void incrementCounter(String name, String description, Map<String, String> tags, double amount) {
        String key = buildKey(name, tags);
        counters.computeIfAbsent(key, k -> new DoubleAdder()).add(amount);
    }

    @Override
    public void setGauge(String name, String description, Map<String, String> tags, double value) {
        String key = buildKey(name, tags);
        AtomicReference<Double> ref = gauges.computeIfAbsent(key, k -> new AtomicReference<>());
        ref.set(value);
    }

    @Override
    public void recordTimer(String name, String description, Map<String, String> tags, Duration duration) {
        String key = buildKey(name, tags);
        timerCounts.computeIfAbsent(key, k -> new LongAdder()).increment();
        timerTotals.computeIfAbsent(key, k -> new LongAdder()).add(duration.toMillis());
    }

    @Override
    public void recordHistogram(String name, String description, Map<String, String> tags, double value) {
        String key = buildKey(name, tags);
        histogramCounts.computeIfAbsent(key, k -> new LongAdder()).increment();
        histogramTotals.computeIfAbsent(key, k -> new DoubleAdder()).add(value);
    }

    @Override
    public boolean isAvailable() {
        return true;
    }

    @Override
    public String getName() {
        return "in-memory";
    }

    /**
     * 获取 Counter 值
     */
    public double getCounterValue(String name, Map<String, String> tags) {
        DoubleAdder adder = counters.get(buildKey(name, tags));
        return adder != null ? adder.sum() : 0;
    }

    /**
     * 获取 Gauge 值
     */
    public double getGaugeValue(String name, Map<String, String> tags) {
        AtomicReference<Double> ref = gauges.get(buildKey(name, tags));
        return ref != null && ref.get() != null ? ref.get() : 0;
    }

    /**
     * 获取 Timer 平均耗时（毫秒）
     */
    public double getTimerAvgMillis(String name, Map<String, String> tags) {
        String key = buildKey(name, tags);
        LongAdder count = timerCounts.get(key);
        LongAdder total = timerTotals.get(key);
        if (count == null || count.sum() == 0) {
            return 0;
        }
        return (double) (total != null ? total.sum() : 0) / count.sum();
    }

    /**
     * 获取所有 Counter 快照
     */
    public Map<String, Double> snapshotCounters() {
        Map<String, Double> snapshot = new ConcurrentHashMap<>();
        counters.forEach((k, v) -> snapshot.put(k, v.sum()));
        return snapshot;
    }

    /**
     * 构建指标 Key
     */
    private String buildKey(String name, Map<String, String> tags) {
        if (tags == null || tags.isEmpty()) {
            return name;
        }
        return name + "|" + tags.toString();
    }
}
