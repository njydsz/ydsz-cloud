package com.njydsz.pmis.common.sentry.metrics;

import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import com.njydsz.pmis.common.sentry.spi.MetricsCollector;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.DistributionSummary;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Tag;
import io.micrometer.core.instrument.Tags;
import io.micrometer.core.instrument.Timer;
import lombok.extern.slf4j.Slf4j;

/**
 * Micrometer 指标采集器
 *
 * <p>基于 Micrometer MeterRegistry 实现指标采集，自动暴露到 Prometheus。
 * 当 MeterRegistry 不可用时自动降级为 {@link InMemoryMetricsCollector}。
 *
 * @author ydsz-pmis-team
 * @since 1.5.0
 */
@Slf4j
public class MicrometerMetricsCollector implements MetricsCollector {

    private final MeterRegistry meterRegistry;
    private final InMemoryMetricsCollector fallback;
    private final ConcurrentHashMap<String, Counter> counterCache = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, Timer> timerCache = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, DistributionSummary> histogramCache = new ConcurrentHashMap<>();

    /** Timer SLO 配置（启用百分位和直方图，支持 Prometheus Exemplar） */
    private static final Duration[] TIMER_SLOS = {
            Duration.ofMillis(50), Duration.ofMillis(100), Duration.ofMillis(250),
            Duration.ofMillis(500), Duration.ofMillis(1000), Duration.ofMillis(5000)
    };
    private static final double[] HISTOGRAM_SLOS = {1, 5, 10, 50, 100, 500, 1000};

    public MicrometerMetricsCollector(MeterRegistry meterRegistry) {
        this.meterRegistry = meterRegistry;
        this.fallback = new InMemoryMetricsCollector();
        log.info("[Sentry] MicrometerMetricsCollector 初始化完成, MeterRegistry={}",
                meterRegistry != null ? meterRegistry.getClass().getSimpleName() : "null");
    }

    @Override
    public void incrementCounter(String name, String description, Map<String, String> tags, double amount) {
        if (!isAvailable()) {
            fallback.incrementCounter(name, description, tags, amount);
            return;
        }
        try {
            String cacheKey = buildCacheKey(name, tags);
            Counter counter = counterCache.computeIfAbsent(cacheKey, k ->
                    Counter.builder(name)
                            .description(description)
                            .tags(toTags(tags))
                            .register(meterRegistry));
            counter.increment(amount);
        } catch (Exception e) {
            log.debug("[Sentry] Micrometer Counter 记录失败, 降级到内存: name={}, err={}", name, e.getMessage());
            fallback.incrementCounter(name, description, tags, amount);
        }
    }

    @Override
    public void setGauge(String name, String description, Map<String, String> tags, double value) {
        if (!isAvailable()) {
            fallback.setGauge(name, description, tags, value);
            return;
        }
        try {
            meterRegistry.gauge(name, toTags(tags), value);
        } catch (Exception e) {
            log.debug("[Sentry] Micrometer Gauge 记录失败, 降级到内存: name={}, err={}", name, e.getMessage());
            fallback.setGauge(name, description, tags, value);
        }
    }

    @Override
    public void recordTimer(String name, String description, Map<String, String> tags, Duration duration) {
        if (!isAvailable()) {
            fallback.recordTimer(name, description, tags, duration);
            return;
        }
        try {
            String cacheKey = buildCacheKey(name, tags);
            Timer timer = timerCache.computeIfAbsent(cacheKey, k ->
                    Timer.builder(name)
                            .description(description)
                            .tags(toTags(tags))
                            .sla(TIMER_SLOS)
                            .register(meterRegistry));
            timer.record(duration);
        } catch (Exception e) {
            log.debug("[Sentry] Micrometer Timer 记录失败, 降级到内存: name={}, err={}", name, e.getMessage());
            fallback.recordTimer(name, description, tags, duration);
        }
    }

    @Override
    public void recordHistogram(String name, String description, Map<String, String> tags, double value) {
        if (!isAvailable()) {
            fallback.recordHistogram(name, description, tags, value);
            return;
        }
        try {
            String cacheKey = buildCacheKey(name, tags);
            DistributionSummary summary = histogramCache.computeIfAbsent(cacheKey, k ->
                    DistributionSummary.builder(name)
                            .description(description)
                            .tags(toTags(tags))
                            .sla(HISTOGRAM_SLOS)
                            .register(meterRegistry));
            summary.record(value);
        } catch (Exception e) {
            log.debug("[Sentry] Micrometer Histogram 记录失败, 降级到内存: name={}, err={}", name, e.getMessage());
            fallback.recordHistogram(name, description, tags, value);
        }
    }

    @Override
    public boolean isAvailable() {
        return meterRegistry != null;
    }

    @Override
    public String getName() {
        return "micrometer";
    }

    /**
     * 获取内存降级采集器
     */
    public InMemoryMetricsCollector getFallback() {
        return fallback;
    }

    /**
     * 将 Map<String,String> 转换为 Micrometer Tags
     */
    private Tags toTags(Map<String, String> tags) {
        if (tags == null || tags.isEmpty()) {
            return Tags.empty();
        }
        return Tags.of(tags.entrySet().stream()
                .map(e -> Tag.of(e.getKey(), e.getValue()))
                .toList());
    }

    /**
     * 构建缓存 Key
     */
    private String buildCacheKey(String name, Map<String, String> tags) {
        if (tags == null || tags.isEmpty()) {
            return name;
        }
        return name + "|" + tags.toString();
    }
}
