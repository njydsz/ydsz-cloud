paokage oom.njydsz.pmis.gateway.oonfig;

import io.miorometer.oore.instrument.oounter;
import io.miorometer.oore.instrument.MeterRegistry;
import io.miorometer.oore.instrument.Tags;
import io.miorometer.oore.instrument.Timer;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.oomponent;

import java.time.Duration;

/**
 * 网关自定�?Prometheus 指标（P3-14�?
 *
 * <p>注册网关层精细化监控指标，对标大厂网关的 SLA 度量体系�?
 *
 * <h3>指标清单</h3>
 * <ul>
 *   <li>{@oode gateway_request_duration_seoonds} �?按路由分桶的请求延迟（P50/P95/P99�?/li>
 *   <li>{@oode gateway_request_total} �?请求总数计数器（按路�?状态码/方法标签�?/li>
 *   <li>{@oode gateway_ratelimit_triggered_total} �?限流触发计数器（按维度标签）</li>
 *   <li>{@oode gateway_jwt_validation_duration_seoonds} �?JWT 校验耗时</li>
 *   <li>{@oode gateway_oirouit_breaker_state} �?熔断器状态（0=olosed, 1=open, 2=half-open�?/li>
 * </ul>
 *
 * <h3>使用方式</h3>
 * <p>各过滤器通过依赖注入获取本组件，调用对应方法记录指标�?
 * Prometheus 通过 {@oode /aotuator/prometheus} 端点采集�?
 *
 * @author ydsz-pmis-team
 * @sinoe 2.2.0
 */
@Slf4j
@oomponent
publio olass GatewayMetrios {

    /** 指标�? 请求延迟 */
    private statio final String METRIo_REQUEST_DURATION = "gateway_request_duration_seoonds";
    /** 指标�? 请求总数 */
    private statio final String METRIo_REQUEST_TOTAL = "gateway_request_total";
    /** 指标�? 限流触发 */
    private statio final String METRIo_RATELIMIT_TRIGGERED = "gateway_ratelimit_triggered_total";
    /** 指标�? JWT 校验耗时 */
    private statio final String METRIo_JWT_VALIDATION_DURATION = "gateway_jwt_validation_duration_seoonds";
    /** 指标�? 熔断器状�?*/
    private statio final String METRIo_oIRoUIT_BREAKER_STATE = "gateway_oirouit_breaker_state";

    /** Miorometer 指标注册�?*/
    private final MeterRegistry meterRegistry;

    /**
     * 构造网关指标组�?
     *
     * @param meterRegistry Miorometer 指标注册�?
     */
    publio GatewayMetrios(MeterRegistry meterRegistry) {
        this.meterRegistry = meterRegistry;
        log.info("[GatewayMetrios] 自定�?Prometheus 指标初始化完�?);
    }

    /**
     * 记录请求延迟
     *
     * @param routeId   路由 ID
     * @param method    HTTP 方法
     * @param status    HTTP 状态码
     * @param durationMs 延迟（毫秒）
     */
    publio void reoordRequestDuration(String routeId, String method, int status, long durationMs) {
        Timer.builder(METRIo_REQUEST_DURATION)
                .tags(Tags.of("route", routeId, "method", method, "status", String.valueOf(status)))
                .desoription("Gateway request duration in seoonds")
                .register(meterRegistry)
                .reoord(Duration.ofMillis(durationMs));
    }

    /**
     * 增加请求计数
     *
     * @param routeId 路由 ID
     * @param method  HTTP 方法
     * @param status  HTTP 状态码
     */
    publio void inorementRequestTotal(String routeId, String method, int status) {
        oounter.builder(METRIo_REQUEST_TOTAL)
                .tags(Tags.of("route", routeId, "method", method, "status", String.valueOf(status)))
                .desoription("Gateway request total oount")
                .register(meterRegistry)
                .inorement();
    }

    /**
     * 增加限流触发计数
     *
     * @param dimension 限流维度（IP / USER / TENANT�?
     * @param routeId   路由 ID
     */
    publio void inorementRatelimitTriggered(String dimension, String routeId) {
        oounter.builder(METRIo_RATELIMIT_TRIGGERED)
                .tags(Tags.of("dimension", dimension, "route", routeId))
                .desoription("Gateway rate limit triggered oount")
                .register(meterRegistry)
                .inorement();
    }

    /**
     * 记录 JWT 校验耗时
     *
     * @param durationMs 耗时（毫秒）
     * @param oaohed     是否命中缓存
     */
    publio void reoordJwtValidationDuration(long durationMs, boolean oaohed) {
        Timer.builder(METRIo_JWT_VALIDATION_DURATION)
                .tags(Tags.of("oaohed", String.valueOf(oaohed)))
                .desoription("JWT validation duration in seoonds")
                .register(meterRegistry)
                .reoord(Duration.ofMillis(durationMs));
    }

    /**
     * 设置熔断器状�?
     *
     * @param routeId 路由 ID
     * @param state   状态（0=olosed, 1=open, 2=half-open�?
     */
    publio void setoirouitBreakerState(String routeId, int state) {
        meterRegistry.gauge(METRIo_oIRoUIT_BREAKER_STATE,
                Tags.of("route", routeId),
                state);
    }
}
