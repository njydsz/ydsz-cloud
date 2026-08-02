package com.njydsz.gateway.config;

import java.time.Duration;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicInteger;

import org.springframework.stereotype.Component;

import com.njydsz.common.base.metrics.AbstractModuleMetrics;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Tags;
import io.micrometer.core.instrument.Timer;
import lombok.extern.slf4j.Slf4j;

/**
 * 网关自定义 Prometheus 指标。
 *
 * <p>P0-2 架构优化：继承 {@link AbstractModuleMetrics}，统一指标前缀 {@code ydsz_gateway_}，
 * 消除手动 ConcurrentHashMap Counter/Timer 缓存（Micrometer 内部已缓存），
 * 修复 {@code recordJwtValidationDuration} 每次创建新 Timer 的性能问题。
 *
 * <h3>指标清单（Prometheus 指标名 = 前缀 + 名称）</h3>
 * <ul>
 *   <li>{@code ydsz_gateway_request_duration_seconds} — 按路由分桶的请求延迟（P50/P95/P99）</li>
 *   <li>{@code ydsz_gateway_request_total} — 请求总数计数器（route/method/status 标签）</li>
 *   <li>{@code ydsz_gateway_ratelimit_triggered_total} — 限流触发计数器（dimension/route 标签）</li>
 *   <li>{@code ydsz_gateway_jwt_validation_duration_seconds} — JWT 校验耗时（cached 标签）</li>
 *   <li>{@code ydsz_gateway_circuit_breaker_state} — 熔断器状态（0=closed, 1=open, 2=half-open）</li>
 * </ul>
 *
 * <p><b>命名变更说明</b>：原 {@code gateway_*} 指标名统一加 {@code ydsz_} 前缀，
 * 与 FlowMetrics({@code ydsz_flow_*})、CronjobMetrics({@code ydsz_cronjob_*}) 等保持一致。
 * Grafana 看板需同步更新指标名。
 *
 * @since 1.0.0
 * @author ydsz-team
 */
@Slf4j
@Component
public class GatewayMetrics extends AbstractModuleMetrics {

    /** 按 routeId 维护的熔断器状态引用（AtomicInteger 可变，Gauge 回调能读到最新值） */
    private final ConcurrentMap<String, AtomicInteger> breakerStates = new ConcurrentHashMap<>();

    /**
     * 构造网关指标组件。
     *
     * <p>委托基类 {@link AbstractModuleMetrics} 以 {@code ydsz_gateway_} 为前缀注册 Micrometer 指标，
     * 由 Micrometer 内部缓存 Timer / Counter 实例，避免每次调用重复创建。
     *
     * @param meterRegistry Micrometer 指标注册中心
     */
    public GatewayMetrics(MeterRegistry meterRegistry) {
        super(meterRegistry, "ydsz_gateway_");
        log.info("[GatewayMetrics] 自定义 Prometheus 指标初始化完成");
    }

    /**
     * 记录请求延迟（使用基类 timer() 方法，Micrometer 内部缓存 Timer 实例）。
     */
    public void recordRequestDuration(String routeId, String method, int status, long durationMs) {
        timer("request_duration_seconds",
                "route", safe(routeId),
                "method", safe(method),
                "status", String.valueOf(status))
                .record(Duration.ofMillis(durationMs));
    }

    /**
     * 增加请求计数。
     */
    public void incrementRequestTotal(String routeId, String method, int status) {
        incrementCounter("request_total",
                "route", safe(routeId),
                "method", safe(method),
                "status", String.valueOf(status));
    }

    /**
     * 增加限流触发计数。
     */
    public void incrementRatelimitTriggered(String dimension, String routeId) {
        incrementCounter("ratelimit_triggered_total",
                "dimension", safe(dimension),
                "route", safe(routeId));
    }

    /**
     * 记录 JWT 校验耗时。
     *
     * <p>P0-2 修复：原实现每次调用 {@code Timer.builder().register()} 创建新 Timer，
     * 现委托基类 {@link #timer(String, String...)} 方法，Micrometer 内部缓存保证 Timer 复用。
     *
     * @param durationMs 耗时（毫秒）
     * @param cached     是否命中缓存
     */
    public void recordJwtValidationDuration(long durationMs, boolean cached) {
        timer("jwt_validation_duration_seconds", "cached", String.valueOf(cached))
                .record(Duration.ofMillis(durationMs));
    }

    /**
     * 设置熔断器状态。
     *
     * <p>P0-2 修复：原实现每次传入 int 原始值（autobox 为不可变 Integer），
     * Gauge 无法反映后续状态变更。现使用 {@link AtomicInteger} 可变引用，
     * Gauge 回调时通过 {@code get()} 读取最新状态值。
     *
     * @param routeId 路由 ID
     * @param state   状态（0=closed, 1=open, 2=half-open）
     */
    public void setCircuitBreakerState(String routeId, int state) {
        AtomicInteger ref = breakerStates.computeIfAbsent(routeId, k -> {
            AtomicInteger holder = new AtomicInteger(state);
            registry.gauge(prefix + "circuit_breaker_state",
                    Tags.of("route", safe(k)),
                    holder, AtomicInteger::doubleValue);
            return holder;
        });
        ref.set(state);
    }
}
