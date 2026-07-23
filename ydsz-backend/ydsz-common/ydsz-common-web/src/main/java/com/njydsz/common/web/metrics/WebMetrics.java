package com.njydsz.common.web.metrics;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicLong;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;

/**
 * Web 模块 Micrometer 指标采集
 *
 * <p>统一采集 Web 基座核心链路指标：
 * <ul>
 *   <li>{@code web.auth.total} — 认证请求总数（tag: result=success/failure）</li>
 *   <li>{@code web.auth.duration} — 认证耗时分布</li>
 *   <li>{@code web.request.total} — HTTP 请求总数（tag: method, status）</li>
 *   <li>{@code web.request.duration} — HTTP 请求耗时分布（tag: method）</li>
 *   <li>{@code web.ratelimit.rejected} — 限流拒绝计数</li>
 *   <li>{@code web.security.header.injected} — 安全响应头注入计数</li>
 * </ul>
 *
 * <p>指标注册采用惰性创建模式，首次调用时注册到 MeterRegistry，
 * 后续复用已注册的 Counter/Timer 实例，避免重复创建。
 *
 * @author ydsz-team
 * @see MeterRegistry
 * @see Counter
 * @see Timer
 */
public class WebMetrics {

    private static final String METRIC_AUTH_TOTAL = "web.auth.total";
    private static final String METRIC_AUTH_DURATION = "web.auth.duration";
    private static final String METRIC_REQUEST_TOTAL = "web.request.total";
    private static final String METRIC_REQUEST_DURATION = "web.request.duration";
    private static final String METRIC_RATELIMIT_REJECTED = "web.ratelimit.rejected";
    private static final String METRIC_SECURITY_HEADER = "web.security.header.injected";

    private final MeterRegistry meterRegistry;

    private final ConcurrentMap<String, Counter> counterCache = new ConcurrentHashMap<>();
    private final ConcurrentMap<String, Timer> timerCache = new ConcurrentHashMap<>();
    private final AtomicLong totalAuthRequests = new AtomicLong(0);
    private final AtomicLong totalAuthFailures = new AtomicLong(0);
    private final AtomicLong totalRateLimitRejected = new AtomicLong(0);
    private final AtomicLong totalSecurityHeadersInjected = new AtomicLong(0);

    public WebMetrics(MeterRegistry meterRegistry) {
        this.meterRegistry = meterRegistry;
    }

    public void recordAuthSuccess(long durationNanos) {
        totalAuthRequests.incrementAndGet();
        getCounter(METRIC_AUTH_TOTAL, "result", "success").increment();
        getTimer(METRIC_AUTH_DURATION).record(java.time.Duration.ofNanos(durationNanos));
    }

    public void recordAuthFailure(long durationNanos) {
        totalAuthRequests.incrementAndGet();
        totalAuthFailures.incrementAndGet();
        getCounter(METRIC_AUTH_TOTAL, "result", "failure").increment();
        getTimer(METRIC_AUTH_DURATION).record(java.time.Duration.ofNanos(durationNanos));
    }

    public void recordRequest(String method, int status, long durationNanos) {
        getCounter(METRIC_REQUEST_TOTAL, "method", method, "status", String.valueOf(status)).increment();
        getTimer(METRIC_REQUEST_DURATION, "method", method).record(java.time.Duration.ofNanos(durationNanos));
    }

    public void recordRateLimitRejected() {
        totalRateLimitRejected.incrementAndGet();
        getCounter(METRIC_RATELIMIT_REJECTED).increment();
    }

    public void recordSecurityHeaderInjected() {
        totalSecurityHeadersInjected.incrementAndGet();
        getCounter(METRIC_SECURITY_HEADER).increment();
    }

    public long getTotalAuthRequests() {
        return totalAuthRequests.get();
    }

    public long getTotalAuthFailures() {
        return totalAuthFailures.get();
    }

    public long getTotalRateLimitRejected() {
        return totalRateLimitRejected.get();
    }

    public long getTotalSecurityHeadersInjected() {
        return totalSecurityHeadersInjected.get();
    }

    private Counter getCounter(String name, String... tags) {
        String key = buildCacheKey(name, tags);
        return counterCache.computeIfAbsent(key, k -> {
            if (tags.length == 0) {
                return meterRegistry.counter(name);
            }
            return Counter.builder(name).tags(tags).register(meterRegistry);
        });
    }

    private Timer getTimer(String name, String... tags) {
        String key = buildCacheKey(name, tags);
        return timerCache.computeIfAbsent(key, k -> {
            if (tags.length == 0) {
                return Timer.builder(name).register(meterRegistry);
            }
            return Timer.builder(name).tags(tags).register(meterRegistry);
        });
    }

    private String buildCacheKey(String name, String... tags) {
        StringBuilder sb = new StringBuilder(name);
        for (int i = 0; i < tags.length; i++) {
            sb.append(':').append(tags[i]);
        }
        return sb.toString();
    }
}
