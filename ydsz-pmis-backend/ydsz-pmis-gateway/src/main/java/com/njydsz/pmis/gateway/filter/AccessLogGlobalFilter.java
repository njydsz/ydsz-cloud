package com.njydsz.pmis.gateway.filter;

import java.net.InetSocketAddress;
import java.util.UUID;

import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.cloud.gateway.route.Route;
import org.springframework.cloud.gateway.support.ServerWebExchangeUtils;
import org.springframework.core.Ordered;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;

import com.njydsz.pmis.common.core.trace.TraceIdGenerator;
import com.njydsz.pmis.gateway.config.GatewayConstants;
import com.njydsz.pmis.gateway.config.GatewayMetrics;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Mono;

/**
 * 网关访问日志全局过滤器（P0-2）
 *
 * <p>记录每个经过网关的请求的结构化访问日志，对标大厂网关（阿里云 API 网关 / Netflix Zuul）的 access log。
 *
 * <h3>日志字段</h3>
 * <ul>
 *   <li>{@code traceId} — 链路追踪 ID</li>
 *   <li>{@code method} — HTTP 方法</li>
 *   <li>{@code path} — 请求路径</li>
 *   <li>{@code query} — 查询参数（截断防日志膨胀）</li>
 *   <li>{@code clientIp} — 客户端 IP（穿透代理）</li>
 *   <li>{@code routeId} — 命中的路由 ID</li>
 *   <li>{@code targetUri} — 目标服务 URI</li>
 *   <li>{@code status} — HTTP 响应状态码</li>
 *   <li>{@code latencyMs} — 请求耗时（毫秒）</li>
 *   <li>{@code userId} — 用户 ID（鉴权后填充）</li>
 *   <li>{@code userAgent} — 客户端 User-Agent（截断）</li>
 * </ul>
 *
 * <h3>执行顺序</h3>
 * <p>{@code HIGHEST_PRECEDENCE + 1}，在 {@link IpWhitelistFilter}(+5) 和
 * {@link AuthGlobalFilter}(+10) 之前执行，确保记录所有请求（含被拒绝的请求）。
 *
 * <h3>日志级别</h3>
 * <ul>
 *   <li>正常请求 (2xx/3xx) — INFO</li>
 *   <li>客户端错误 (4xx) — WARN</li>
 *   <li>服务端错误 (5xx) — ERROR</li>
 * </ul>
 *
 * @author ydsz-pmis-team
 * @since 2.2.0
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AccessLogGlobalFilter implements GlobalFilter, Ordered {

    /** 查询参数最大记录长度 */
    private static final int MAX_QUERY_LENGTH = 200;

    /** User-Agent 最大记录长度 */
    private static final int MAX_UA_LENGTH = 200;

    /** exchange attribute key: 请求开始时间戳 */
    private static final String ATTR_START_TIME = "__gateway_start_time";

    /** exchange attribute key: traceId */
    private static final String ATTR_TRACE_ID = "__gateway_trace_id";

    /** P3-14: 网关自定义指标 */
    private final GatewayMetrics gatewayMetrics;

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        long startTime = System.currentTimeMillis();
        String traceId = exchange.getRequest().getHeaders().getFirst(GatewayConstants.HEADER_TRACE_ID);
        if (traceId == null || traceId.isBlank()) {
            traceId = TraceIdGenerator.generate();
        }

        final String finalTraceId = traceId;
        exchange.getAttributes().put(ATTR_START_TIME, startTime);
        exchange.getAttributes().put(ATTR_TRACE_ID, finalTraceId);

        // 确保响应头携带 traceId
        exchange.getResponse().getHeaders().add(GatewayConstants.HEADER_TRACE_ID, finalTraceId);

        return chain.filter(exchange).doFinally(signalType -> {
            long duration = System.currentTimeMillis() - startTime;
            logAccess(exchange, finalTraceId, duration);
        });
    }

    /**
     * 输出结构化访问日志
     *
     * @param exchange 服务器 Web 交换上下文
     * @param traceId  链路追踪 ID
     * @param duration 请求耗时（毫秒）
     */
    private void logAccess(ServerWebExchange exchange, String traceId, long duration) {
        ServerHttpRequest request = exchange.getRequest();
        ServerHttpResponse response = exchange.getResponse();

        String method = request.getMethod().name();
        String path = request.getURI().getPath();
        String query = request.getURI().getQuery();
        if (query != null && query.length() > MAX_QUERY_LENGTH) {
            query = query.substring(0, MAX_QUERY_LENGTH) + "...";
        }
        String clientIp = extractClientIp(request);
        String userAgent = request.getHeaders().getFirst("User-Agent");
        if (userAgent != null && userAgent.length() > MAX_UA_LENGTH) {
            userAgent = userAgent.substring(0, MAX_UA_LENGTH) + "...";
        }
        String userId = request.getHeaders().getFirst(GatewayConstants.HEADER_USER_ID);

        // 获取路由信息
        Route route = exchange.getAttribute(ServerWebExchangeUtils.GATEWAY_ROUTE_ATTR);
        String routeId = route != null ? route.getId() : "UNKNOWN";
        String targetUri = route != null ? route.getUri().toString() : "UNKNOWN";

        int status = response.getStatusCode() != null ? response.getStatusCode().value() : 0;

        // P3-14: 记录 Prometheus 指标
        gatewayMetrics.recordRequestDuration(routeId, method, status, duration);
        gatewayMetrics.incrementRequestTotal(routeId, method, status);

        String logMessage = String.format(
                "traceId=%s | method=%s | path=%s | query=%s | clientIp=%s | status=%d | latencyMs=%d | " +
                        "routeId=%s | targetUri=%s | userId=%s | userAgent=%s",
                safeTraceId(traceId),
                method,
                path,
                query != null ? query : "-",
                clientIp,
                status,
                duration,
                routeId,
                targetUri,
                userId != null ? userId : "-",
                userAgent != null ? userAgent : "-"
        );

        if (status >= 500) {
            log.error(logMessage);
        } else if (status >= 400) {
            log.warn(logMessage);
        } else {
            log.info(logMessage);
        }
    }

    /**
     * 提取客户端真实 IP（穿透代理）
     *
     * @param request 服务器 HTTP 请求
     * @return 客户端 IP
     */
    private String extractClientIp(ServerHttpRequest request) {
        String ip = request.getHeaders().getFirst("X-Forwarded-For");
        if (ip != null && !ip.isEmpty() && !"unknown".equalsIgnoreCase(ip)) {
            return ip.split(",")[0].trim();
        }
        ip = request.getHeaders().getFirst("X-Real-IP");
        if (ip != null && !ip.isEmpty() && !"unknown".equalsIgnoreCase(ip)) {
            return ip;
        }
        InetSocketAddress remoteAddress = request.getRemoteAddress();
        return remoteAddress != null ? remoteAddress.getAddress().getHostAddress() : "unknown";
    }

    /**
     * traceId 安全输出（确保非 null）
     *
     * @param traceId 链路追踪 ID
     * @return 非 null 的 traceId
     */
    private String safeTraceId(String traceId) {
        return traceId != null ? traceId : UUID.randomUUID().toString().replace("-", "");
    }

    @Override
    public int getOrder() {
        return Ordered.HIGHEST_PRECEDENCE + 1;
    }
}
