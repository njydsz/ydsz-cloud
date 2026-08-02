package com.njydsz.gateway.filter;

import java.util.UUID;

import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;

import com.njydsz.gateway.config.GatewayConstants;

import reactor.core.publisher.Mono;

/**
 * W3C Trace Context 注入过滤器（P3-13）
 *
 * <p>在网关入口注入 W3C 标准 Trace Context 头，使下游服务可通过
 * OpenTelemetry / SkyWalking / Jaeger / Zipkin 自动采集全链路追踪。
 *
 * <h3>W3C Trace Context 格式</h3>
 * <pre>
 *   traceparent: 00-{traceId(32hex)}-{spanId(16hex)}-{flags(2hex)}
 *   示例: 00-0af7651916cd43dd8448eb211c80319c-b7ad6b7169203331-01
 * </pre>
 *
 * <h3>兼容性</h3>
 * <ul>
 *   <li>保留现有 {@code X-Trace-Id} 头，向后兼容</li>
 *   <li>新增 {@code traceparent} 头，符合 W3C Recommendation</li>
 *   <li>下游服务若部署了 OTel Agent，会自动解析 traceparent</li>
 * </ul>
 *
 * <h3>执行顺序</h3>
 * <p>{@code HIGHEST_PRECEDENCE + 2}，在 {@link AccessLogGlobalFilter}(+1) 之后、
 * {@link IpBlacklistFilter}(+3) 之前，确保所有下游请求都携带 trace context。
 *
 * @since 1.0.0
 * @author ydsz-team
 */
@Component
public class W3CTraceContextFilter implements GlobalFilter, Ordered {

    /** W3C Trace Context 版本 */
    private static final String TRACE_VERSION = "00";

    /** W3C Trace Context 采样标志（01=sampled） */
    private static final String TRACE_FLAGS = "01";

    /** traceparent 请求头名 */
    private static final String HEADER_TRACEPARENT = "traceparent";

    /**
     * 注入 W3C Trace Context，建立全链路追踪上下文。
     *
     * <p>生成符合 W3C 标准的 traceparent（32hex traceId + 16hex spanId）并注入请求，
     * 同时兼容写入 {@code X-Trace-Id}；响应头也回写两者。作为最早执行的过滤器（order=0），
     * 后续过滤器统一复用此 traceId，避免各自生成导致链路断裂。
     *
     * @param exchange 服务器 Web 交换上下文
     * @param chain    网关过滤器链
     * @return 完成信号 Mono
     */
    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        ServerHttpRequest request = exchange.getRequest();

        // 生成 W3C 格式的 traceId（32 hex）和 spanId（16 hex）
        String traceId = generateTraceId();
        String spanId = generateSpanId();

        // 构造 traceparent 头
        String traceparent = TRACE_VERSION + "-" + traceId + "-" + spanId + "-" + TRACE_FLAGS;

        // 注入 traceparent 和 X-Trace-Id（兼容）
        ServerHttpRequest mutated = request.mutate()
                .header(HEADER_TRACEPARENT, traceparent)
                .header(GatewayConstants.HEADER_TRACE_ID, traceId)
                .build();

        // 同时写入响应头
        exchange.getResponse().getHeaders().add(GatewayConstants.HEADER_TRACE_ID, traceId);
        exchange.getResponse().getHeaders().add(HEADER_TRACEPARENT, traceparent);

        return chain.filter(exchange.mutate().request(mutated).build());
    }

    /**
     * 生成 W3C 格式的 traceId（32 位 hex，去除 UUID 的短横线）
     *
     * @return 32 位 hex 字符串
     */
    private String generateTraceId() {
        return UUID.randomUUID().toString().replace("-", "");
    }

    /**
     * 生成 W3C 格式的 spanId（16 位 hex，取 UUID 前 16 位）
     *
     * @return 16 位 hex 字符串
     */
    private String generateSpanId() {
        return UUID.randomUUID().toString().replace("-", "").substring(0, 16);
    }

    /**
     * P0-2: 过滤器执行顺序调整为 HIGHEST_PRECEDENCE + 0
     *
     * <p>作为最早期执行的过滤器，统一生成 traceId 并注入 traceparent 头。
     * 后续所有过滤器（AccessLog +1、IpBlacklist +3、Auth +10 等）直接读取已注入的 traceId，
     * 不再各自生成新的 traceId，避免 traceId 被覆盖导致链路追踪断裂。
     *
     * @return 顺序值
     */
    @Override
    public int getOrder() {
        return Ordered.HIGHEST_PRECEDENCE;
    }
}
