package com.njydsz.gateway.config;

import java.time.Duration;

import org.springframework.stereotype.Component;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Tags;
import io.micrometer.core.instrument.Timer;
import lombok.extern.slf4j.Slf4j;

/**
 * 网关自定义 Prometheus 指标（P3-14）
 *
 * <p>注册网关层精细化监控指标，对标大厂网关的 SLA 度量体系。
 *
 * <h3>指标清单</h3>
 * <ul>
 *   <li>{@code gateway_request_duration_seconds} — 按路由分桶的请求延迟（P50/P95/P99）</li>
 *   <li>{@code gateway_request_total} — 请求总数计数器（按路由/状态码/方法标签）</li>
 *   <li>{@code gateway_ratelimit_triggered_total} — 限流触发计数器（按维度标签）</li>
 *   <li>{@code gateway_jwt_validation_duration_seconds} — JWT 校验耗时</li>
 *   <li>{@code gateway_circuit_breaker_state} — 熔断器状态（0=closed, 1=open, 2=half-open）</li>
 * </ul>
 *
 * <h3>使用方式</h3>
 * <p>各过滤器通过依赖注入获取本组件，调用对应方法记录指标。
 * Prometheus 通过 {@code /actuator/prometheus} 端点采集。
 *
 * @since 2.2.0
 */
@Slf4j
@Component
public class GatewayMetrics {

    /** 指标名: 请求延迟 */
    private static final String METRIC_REQUEST_DURATION = "gateway_request_duration_seconds";
    /** 指标名: 请求总数 */
    private static final String METRIC_REQUEST_TOTAL = "gateway_request_total";
    /** 指标名: 限流触发 */
    private static final String METRIC_RATELIMIT_TRIGGERED = "gateway_ratelimit_triggered_total";
    /** 指标名: JWT 校验耗时 */
    private static final String METRIC_JWT_VALIDATION_DURATION = "gateway_jwt_validation_duration_seconds";
    /** 指标名: 熔断器状态 */
    private static final String METRIC_CIRCUIT_BREAKER_STATE = "gateway_circuit_breaker_state";

    /** Micrometer 指标注册器 */
    private final MeterRegistry meterRegistry;

    /**
     * 构造网关指标组件
     *
     * @param meterRegistry Micrometer 指标注册器
     */
    public GatewayMetrics(MeterRegistry meterRegistry) {
        this.meterRegistry = meterRegistry;
        log.info("[GatewayMetrics] 自定义 Prometheus 指标初始化完成");
    }

    /**
     * 记录请求延迟
     *
     * @param routeId   路由 ID
     * @param method    HTTP 方法
     * @param status    HTTP 状态码
     * @param durationMs 延迟（毫秒）
     */
    public void recordRequestDuration(String routeId, String method, int status, long durationMs) {
        Timer.builder(METRIC_REQUEST_DURATION)
                .tags(Tags.of("route", routeId, "method", method, "status", String.valueOf(status)))
                .description("Gateway request duration in seconds")
                .register(meterRegistry)
                .record(Duration.ofMillis(durationMs));
    }

    /**
     * 增加请求计数
     *
     * @param routeId 路由 ID
     * @param method  HTTP 方法
     * @param status  HTTP 状态码
     */
    public void incrementRequestTotal(String routeId, String method, int status) {
        Counter.builder(METRIC_REQUEST_TOTAL)
                .tags(Tags.of("route", routeId, "method", method, "status", String.valueOf(status)))
                .description("Gateway request total count")
                .register(meterRegistry)
                .increment();
    }

    /**
     * 增加限流触发计数
     *
     * @param dimension 限流维度（IP / USER / TENANT）
     * @param routeId   路由 ID
     */
    public void incrementRatelimitTriggered(String dimension, String routeId) {
        Counter.builder(METRIC_RATELIMIT_TRIGGERED)
                .tags(Tags.of("dimension", dimension, "route", routeId))
                .description("Gateway rate limit triggered count")
                .register(meterRegistry)
                .increment();
    }

    /**
     * 记录 JWT 校验耗时
     *
     * @param durationMs 耗时（毫秒）
     * @param cached     是否命中缓存
     */
    public void recordJwtValidationDuration(long durationMs, boolean cached) {
        Timer.builder(METRIC_JWT_VALIDATION_DURATION)
                .tags(Tags.of("cached", String.valueOf(cached)))
                .description("JWT validation duration in seconds")
                .register(meterRegistry)
                .record(Duration.ofMillis(durationMs));
    }

    /**
     * 设置熔断器状态
     *
     * @param routeId 路由 ID
     * @param state   状态（0=closed, 1=open, 2=half-open）
     */
    public void setCircuitBreakerState(String routeId, int state) {
        meterRegistry.gauge(METRIC_CIRCUIT_BREAKER_STATE,
                Tags.of("route", routeId),
                state);
    }
}
