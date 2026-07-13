package com.njydsz.pmis.common.feign.monitor;

import com.njydsz.pmis.common.feign.interceptor.FeignResponseInterceptor;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import java.time.Duration;

/**
 * Feign 响应指标 Micrometer 适配器
 *
 * <p>实现 {@link FeignResponseInterceptor.FeignResponseMetrics} 接口，
 * 将 Feign 调用指标通过 {@link FeignMicrometerCollector} 注册到 Micrometer。
 *
 * <p>注册的指标：
 * <ul>
 *   <li>{@code feign.request.latency} - 请求延迟 Timer（标签: client, method）</li>
 *   <li>{@code feign.request.errors} - 请求错误 Counter（标签: client, method, status_code）</li>
 *   <li>{@code feign.request.slow} - 慢调用 Counter（标签: client, method）</li>
 * </ul>
 *
 * @author Marvin Lee
 * @email limw1888@126.com
 * @version 4.0.0
 * @since 3.5.0
 */
public class FeignResponseMetricsAdapter implements FeignResponseInterceptor.FeignResponseMetrics {

    private final FeignMicrometerCollector collector;
    private final MeterRegistry meterRegistry;

    /**
     * 构造 Feign 响应指标适配器
     *
     * @param meterRegistry Micrometer 指标注册表
     */
    public FeignResponseMetricsAdapter(MeterRegistry meterRegistry) {
        this.meterRegistry = meterRegistry;
        this.collector = FeignMicrometerCollector.getOrCreate(meterRegistry);
    }

    @Override
    public void recordSuccess(String service, String method, int status, long duration) {
        // 通过 Timer 记录延迟
        Timer.builder("feign.request.latency")
                .tag("client", service)
                .tag("method", method)
                .description("Feign request latency")
                .register(meterRegistry)
                .record(Duration.ofMillis(duration));
    }

    @Override
    public void recordFailure(String service, String method, int status, long duration, String errorType) {
        // 记录错误计数
        collector.recordError(service, method, String.valueOf(status));

        // 同时记录延迟
        Timer.builder("feign.request.latency")
                .tag("client", service)
                .tag("method", method)
                .description("Feign request latency")
                .register(meterRegistry)
                .record(Duration.ofMillis(duration));
    }

    @Override
    public void recordSlowCall(String service, String method, long duration, long threshold) {
        collector.recordSlowCall(service, method);
    }
}
