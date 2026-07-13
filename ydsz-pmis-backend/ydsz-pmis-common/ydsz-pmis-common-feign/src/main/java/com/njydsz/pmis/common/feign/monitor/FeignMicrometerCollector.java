package com.njydsz.pmis.common.feign.monitor;

import java.util.function.Supplier;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;

/**
 * Feign 调用 Micrometer 指标收集器
 *
 * <p>为 Feign 调用提供可选的 Micrometer 指标采集，仅当 Micrometer 在 classpath 中时启用。
 * 使用 {@link Timer.Sample} 模式进行低开销测量。
 *
 * <p>注册的指标：
 * <ul>
 *   <li>{@code feign.request.latency} - Feign 请求延迟 Timer</li>
 *   <li>{@code feign.request.errors} - Feign 请求错误 Counter</li>
 *   <li>{@code feign.request.slow} - Feign 慢调用 Counter（P2 可观测性增强）</li>
 * </ul>
 *
 * <p>指标标签：
 * <ul>
 *   <li>{@code client} - Feign 客户端名称</li>
 *   <li>{@code method} - 请求方法（如 GET, POST）</li>
 *   <li>{@code status_code} - HTTP 状态码（仅 errors 指标）</li>
 * </ul>
 *
 * <p><b>使用示例：</b></p>
 * <pre>{@code
 * FeignMicrometerCollector collector = FeignMicrometerCollector.getOrCreate(registry);
 *
 * // 延迟指标
 * collector.recordLatency("UserService", "GET", () -> feignClient.getUser(id));
 *
 * // 错误指标
 * collector.recordError("UserService", "POST", "500");
 * }</pre>
 *
 * @author Marvin Lee
 * @email limw1888@126.com
 * @version 3.5.0
 */
public class FeignMicrometerCollector {

    /** Feign 请求延迟指标名称 */
    private static final String METRIC_REQUEST_LATENCY = "feign.request.latency";
    /** Feign 请求错误指标名称 */
    private static final String METRIC_REQUEST_ERRORS = "feign.request.errors";
    /** Feign 慢调用指标名称 */
    private static final String METRIC_REQUEST_SLOW = "feign.request.slow";
    /** 客户端名称标签键 */
    private static final String TAG_CLIENT = "client";
    /** HTTP 方法标签键 */
    private static final String TAG_METHOD = "method";
    /** HTTP 状态码标签键 */
    private static final String TAG_STATUS_CODE = "status_code";

    /** Micrometer 指标注册表 */
    private final MeterRegistry registry;

    /**
     * 构造 Feign Micrometer 指标收集器。
     *
     * @param registry Micrometer 指标注册表
     */
    private FeignMicrometerCollector(MeterRegistry registry) {
        this.registry = registry;
    }

    /**
     * 创建或获取 Feign 指标收集器
     *
     * @param registry MeterRegistry 实例
     * @return FeignMicrometerCollector 实例
     */
    public static FeignMicrometerCollector getOrCreate(MeterRegistry registry) {
        return new FeignMicrometerCollector(registry);
    }

    /**
     * 记录 Feign 请求延迟
     *
     * <p>使用 Timer.Sample 模式，开销极低。
     *
     * @param clientName Feign 客户端名称
     * @param method     HTTP 方法（如 GET, POST）
     * @param supplier   要执行的 Feign 调用
     * @param <T>        返回值类型
     * @return 调用返回值
     */
    public <T> T recordLatency(String clientName, String method, Supplier<T> supplier) {
        Timer.Sample sample = Timer.start(registry);
        try {
            return supplier.get();
        } finally {
            sample.stop(Timer.builder(METRIC_REQUEST_LATENCY)
                    .tag(TAG_CLIENT, clientName)
                    .tag(TAG_METHOD, method)
                    .description("Feign request latency")
                    .register(registry));
        }
    }

    /**
     * 记录无返回值的 Feign 请求延迟
     *
     * @param clientName Feign 客户端名称
     * @param method     HTTP 方法
     * @param runnable   要执行的 Feign 调用
     */
    public void recordLatency(String clientName, String method, Runnable runnable) {
        Timer.Sample sample = Timer.start(registry);
        try {
            runnable.run();
        } finally {
            sample.stop(Timer.builder(METRIC_REQUEST_LATENCY)
                    .tag(TAG_CLIENT, clientName)
                    .tag(TAG_METHOD, method)
                    .description("Feign request latency")
                    .register(registry));
        }
    }

    /**
     * 记录 Feign 请求错误
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
     * 记录 Feign 请求错误（从异常推断状态码）
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
     * 记录 Feign 慢调用（P2 可观测性增强）
     *
     * <p>当 Feign 调用耗时超过配置阈值时递增此计数器，
     * 标签包含客户端名称和 HTTP 方法，便于按服务维度告警。
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
