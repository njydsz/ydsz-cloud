package com.njydsz.common.feign.monitor;

import java.time.Duration;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;

/**
 * Feign 调用 Micrometer 指标收集器。
 *
 * <p>为 Feign 调用提供 Micrometer 指标采集，使用 {@link Timer.Sample} 模式进行低开销测量。
 * 通过 {@link #getInstance(MeterRegistry)} 获取全局单例，避免重复创建。
 *
 * <p>注册的指标：
 * <ul>
 *   <li>{@code feign.request.latency} - Feign 请求延迟 Timer（标签: client, method）</li>
 *   <li>{@code feign.request.errors} - Feign 请求错误 Counter（标签: client, method, status_code）</li>
 *   <li>{@code feign.request.slow} - Feign 慢调用 Counter（标签: client, method）</li>
 * </ul>
 *
 * <p><b>Timer 缓存：</b>使用 {@link ConcurrentHashMap} 缓存已创建的 Timer 实例，
 * 避免每次调用都创建新的 {@link Timer.Builder} 对象。
 *
 * @author ydsz-team
 * @since 1.0.0
 */
public class FeignMicrometerCollector {

    private static final String METRIC_REQUEST_LATENCY = "feign.request.latency";
    private static final String METRIC_REQUEST_ERRORS = "feign.request.errors";
    private static final String METRIC_REQUEST_SLOW = "feign.request.slow";
    private static final String TAG_CLIENT = "client";
    private static final String TAG_METHOD = "method";
    private static final String TAG_STATUS_CODE = "status_code";

    /** 单例实例（按 MeterRegistry 维度缓存） */
    private static final ConcurrentHashMap<MeterRegistry, FeignMicrometerCollector> INSTANCES =
            new ConcurrentHashMap<>();

    /** Timer 缓存，Key = "client|method" */
    private final ConcurrentHashMap<String, Timer> timerCache = new ConcurrentHashMap<>();

    private final MeterRegistry registry;

    private FeignMicrometerCollector(MeterRegistry registry) {
        this.registry = registry;
    }

    /**
     * 获取或创建 Feign 指标收集器单例。
     *
     * @param registry MeterRegistry 实例
     * @return 全局唯一的 FeignMicrometerCollector 实例
     */
    public static FeignMicrometerCollector getInstance(MeterRegistry registry) {
        return INSTANCES.computeIfAbsent(registry, FeignMicrometerCollector::new);
    }

    /**
     * 获取或创建 Timer 实例（带缓存）。
     *
     * @param clientName Feign 客户端名称
     * @param method     HTTP 方法
     * @return 缓存的 Timer 实例
     */
    private Timer getOrCreateTimer(String clientName, String method) {
        String cacheKey = clientName + "|" + method;
        return timerCache.computeIfAbsent(cacheKey, k ->
                Timer.builder(METRIC_REQUEST_LATENCY)
                        .tag(TAG_CLIENT, clientName)
                        .tag(TAG_METHOD, method)
                        .description("Feign request latency")
                        .register(registry));
    }

    /**
     * 记录 Feign 请求延迟。
     *
     * @param clientName Feign 客户端名称
     * @param method     HTTP 方法（如 GET, POST）
     * @param durationMs 耗时（毫秒）
     */
    public void recordLatency(String clientName, String method, long durationMs) {
        getOrCreateTimer(clientName, method).record(Duration.ofMillis(durationMs));
    }

    /**
     * 记录 Feign 请求延迟（通过 Supplier 包装调用）。
     *
     * @param clientName Feign 客户端名称
     * @param method     HTTP 方法
     * @param supplier   要执行的 Feign 调用
     * @param <T>        返回值类型
     * @return 调用返回值
     */
    public <T> T recordLatency(String clientName, String method, Supplier<T> supplier) {
        Timer.Sample sample = Timer.start(registry);
        try {
            return supplier.get();
        } finally {
            sample.stop(getOrCreateTimer(clientName, method));
        }
    }

    /**
     * 记录 Feign 请求错误。
     *
     * @param clientName Feign 客户端名称
     * @param method     HTTP 方法
     * @param statusCode HTTP 状态码
     */
    public void recordError(String clientName, String method, String statusCode) {
        Counter.builder(METRIC_REQUEST_ERRORS)
                .tag(TAG_CLIENT, clientName)
                .tag(TAG_METHOD, method)
                .tag(TAG_STATUS_CODE, statusCode)
                .description("Feign request errors")
                .register(registry)
                .increment();
    }

    /**
     * 记录 Feign 请求错误（从异常推断状态码）。
     *
     * @param clientName Feign 客户端名称
     * @param method     HTTP 方法
     * @param exception  异常实例
     */
    public void recordError(String clientName, String method, Throwable exception) {
        String statusCode = exception != null ? exception.getClass().getSimpleName() : "Unknown";
        recordError(clientName, method, statusCode);
    }

    /**
     * 记录 Feign 慢调用。
     *
     * @param clientName Feign 客户端名称
     * @param method     HTTP 方法
     */
    public void recordSlowCall(String clientName, String method) {
        Counter.builder(METRIC_REQUEST_SLOW)
                .tag(TAG_CLIENT, clientName)
                .tag(TAG_METHOD, method)
                .description("Feign slow call count (exceeds configured threshold)")
                .register(registry)
                .increment();
    }
}
