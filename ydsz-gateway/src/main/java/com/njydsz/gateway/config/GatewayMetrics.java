package com.njydsz.gateway.config;

import java.time.Duration;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.stereotype.Component;
import com.njydsz.common.sentry.adapter.SentryMetricsAdapter;

/**
 * 网关自定义 Prometheus 指标。
 *
 * <p>继承 {@link SentryMetricsAdapter}，统一指标前缀 {@code ydsz_gateway_}，
 * 消除手动 ConcurrentHashMap Counter/Timer 缓存（Micrometer 内部已缓存）。
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
 * <p><b>v2.1.0 变更</b>：删除 MeterRegistry 构造参数，改为继承 SentryMetricsAdapter
 * 通过 MetricsCollector SPI 注册指标，符合《云顶编码规范》第 27.2.1 节。
 *
 * @since 1.0.0
 * @author ydsz-team
 */
@Slf4j
@Component
@ConditionalOnClass(MeterRegistry.class)
public class GatewayMetrics extends SentryMetricsAdapter {

    /** 按 routeId 维护的熔断器状态引用（AtomicInteger 可变，Gauge 回调能读到最新值） */
    private final ConcurrentMap<String, AtomicInteger> breakerStates = new ConcurrentHashMap<>();

    /** P1-2: 本地兜底限流配额引用（Gauge 读取最新值） */
    private final AtomicInteger fallbackQuotaRef = new AtomicInteger(0);

    /**
     * 构造网关指标组件。
     *
     * <p>委托基类 {@link SentryMetricsAdapter} 以 {@code ydsz_gateway_} 为前缀注册指标。
     */
    public GatewayMetrics() {
        super("ydsz_gateway_");
        log.info("[GatewayMetrics] 自定义 Prometheus 指标初始化完成");
    }

    /**
     * 记录请求延迟（使用基类 timer() 方法）。
     */
    public void recordRequestDuration(String routeId, String method, int status, long durationMs) {
        recordTimer("request_duration_seconds", durationMs,
                "route", safe(routeId),
                "method", safe(method),
                "status", String.valueOf(status));
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
     * 增加限流本地兜底计数（Redis 不可用时的降级模式）。
     *
     * <p>P1-2: 用于监控 Redis 故障期间限流降级频率，
     * 指标 {@code ydsz_gateway_ratelimit_fallback_total}，Grafana 可据此告警
     * "限流降级中，请检查 Redis"。
     */
    public void incrementRatelimitFallback() {
        incrementCounter("ratelimit_fallback_total");
    }

    /**
     * 上报本地兜底令牌桶的自适应配额（按实例数分摊后的 QPS）。
     *
     * <p>P1-2: Gauge 指标 {@code ydsz_gateway_ratelimit_fallback_quota}，
     * 便于确认 Redis 故障期间的实际限流阈值。首次调用注册 Gauge，后续仅更新引用值。
     *
     * @param quota 自适应分摊后的本地 QPS 配额
     */
    public void setRatelimitFallbackQuota(int quota) {
        if (fallbackQuotaRef.getAndSet(quota) == 0) {
            // 首次注册 Gauge（幂等：Micrometer 对同名称+标签的重复注册会合并）
            gaugeRef("ratelimit_fallback_quota", fallbackQuotaRef, AtomicInteger::doubleValue);
        }
    }

    /**
     * 记录 JWT 校验耗时。
     *
     * @param durationMs 耗时（毫秒）
     * @param cached     是否命中缓存
     */
    public void recordJwtValidationDuration(long durationMs, boolean cached) {
        recordTimer("jwt_validation_duration_seconds", durationMs,
                "cached", String.valueOf(cached));
    }

    /**
     * 设置熔断器状态。
     *
     * <p>使用 AtomicInteger 可变引用，Gauge 回调时通过 {@code get()} 读取最新状态值。
     *
     * @param routeId 路由 ID
     * @param state   状态（0=closed, 1=open, 2=half-open）
     */
    public void setCircuitBreakerState(String routeId, int state) {
        AtomicInteger ref = breakerStates.computeIfAbsent(routeId, k -> {
            AtomicInteger holder = new AtomicInteger(state);
            // 通过 Adapter 提供的 gaugeRef 注册 Gauge
            gaugeRef("circuit_breaker_state", holder, AtomicInteger::doubleValue, "route", safe(k));
            return holder;
        });
        ref.set(state);
    }

    /**
     * P0-3: 注册 JWT 缓存命中/未命中计数器到 Prometheus。
     *
     * <p>指标名：
     * <ul>
     *   <li>{@code ydsz_gateway_jwt_cache_hit_rate} — 缓存命中次数</li>
     *   <li>{@code ydsz_gateway_jwt_cache_miss_total} — 缓存未命中次数</li>
     * </ul>
     * 命中率 = hit / (hit + miss)，Grafana 可通过 {@code rate()} 计算实时命中率。
     *
     * @param hitCounter  命中计数器引用
     * @param missCounter 未命中计数器引用
     */
    public void registerJwtCacheCounters(AtomicLong hitCounter, AtomicLong missCounter) {
        gaugeRef("jwt_cache_hit_rate", hitCounter, AtomicLong::doubleValue);
        gaugeRef("jwt_cache_miss_total", missCounter, AtomicLong::doubleValue);
        log.info("[GatewayMetrics] JWT 缓存命中/未命中 Prometheus 指标已注册");
    }

    /**
     * 获取 JWT 缓存命中率（需要在 CachedJwtValidator 已初始化后调用）。
     *
     * <p>该方法由 CachedJwtValidator 通过回调获取，不直接暴露 Counter。
     *
     * @param hitCounter  命中计数器引用
     * @param missCounter 未命中计数器引用
     * @return 缓存命中率（0.0 ~ 1.0），无请求时返回 -1.0
     */
    public static double calculateJwtCacheHitRate(AtomicLong hitCounter, AtomicLong missCounter) {
        long hits = hitCounter.get();
        long total = hits + missCounter.get();
        return total > 0 ? (double) hits / total : -1.0;
    }
}
