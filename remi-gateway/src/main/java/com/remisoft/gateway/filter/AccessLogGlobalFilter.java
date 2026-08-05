package com.remisoft.gateway.filter;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.cloud.gateway.route.Route;
import org.springframework.cloud.gateway.support.ServerWebExchangeUtils;
import org.springframework.core.Ordered;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.util.MultiValueMap;
import org.springframework.web.server.ServerWebExchange;

import com.remisoft.common.json.RemiJson;
import com.remisoft.gateway.config.GatewayConstants;
import com.remisoft.gateway.config.GatewayIpUtils;
import com.remisoft.gateway.config.GatewayMetrics;
import com.remisoft.common.core.trace.TraceIdGenerator;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Mono;

/**
 * 网关访问日志全局过滤器（P0-2 + P0-8 敏感参数脱敏）
 *
 * <p>记录每个经过网关的请求的结构化访问日志，对标大厂网关（阿里云 API 网关 / Netflix Zuul）的 access log。
 *
 * <h3>日志字段</h3>
 * <ul>
 *   <li>{@code traceId} — 链路追踪 ID</li>
 *   <li>{@code method} — HTTP 方法</li>
 *   <li>{@code path} — 请求路径</li>
 *   <li>{@code query} — 查询参数（P0-8: 敏感参数脱敏 + 截断防日志膨胀）</li>
 *   <li>{@code clientIp} — 客户端 IP（P0-3: 穿透可信代理链）</li>
 *   <li>{@code routeId} — 命中的路由 ID</li>
 *   <li>{@code targetUri} — 目标服务 URI</li>
 *   <li>{@code status} — HTTP 响应状态码</li>
 *   <li>{@code latencyMs} — 请求耗时（毫秒）</li>
 *   <li>{@code userId} — 用户 ID（鉴权后填充）</li>
 *   <li>{@code userAgent} — 客户端 User-Agent（截断）</li>
 * </ul>
 *
 * <h3>P0-8: 敏感信息脱敏</h3>
 * <p>访问日志严格禁止记录以下敏感信息：
 * <ul>
 *   <li>Authorization 头（Bearer Token / Basic Auth）— 不记录</li>
 *   <li>查询参数 {@code token} / {@code access_token} / {@code password} — 脱敏为 {@code ***}</li>
 *   <li>查询参数 {@code secret} / {@code apiKey} — 脱敏为 {@code ***}</li>
 * </ul>
 * 这样可避免 JWT 通过 WebSocket 的 {@code ?token=...} 查询参数泄漏到日志文件，
 * 同时满足等保三级 / GDPR 等数据保护要求。
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
 * @since 1.0.0
 * @author remi-team
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AccessLogGlobalFilter implements GlobalFilter, Ordered {

    /** 查询参数最大记录长度 */
    private static final int MAX_QUERY_LENGTH = 200;

    /** User-Agent 最大记录长度 */
    private static final int MAX_UA_LENGTH = 200;

    /** P0-8: 敏感查询参数（小写匹配，值脱敏为 ***） */
    private static final Set<String> SENSITIVE_QUERY_PARAMS = Set.of(
            "token", "access_token", "refresh_token",
            "password", "passwd", "pwd",
            "secret", "client_secret",
            "apikey", "api_key",
            "authorization",
            "code"  // OAuth2 授权码
    );

    /** P0-8: 脱敏占位符 */
    private static final String MASKED_VALUE = "***";

    /** exchange attribute key: 请求开始时间戳 */
    private static final String ATTR_START_TIME = "__gateway_start_time";

    /** exchange attribute key: traceId */
    private static final String ATTR_TRACE_ID = "__gateway_trace_id";

    /** P3-14: 网关自定义指标 */
    private final GatewayMetrics gatewayMetrics;

    /**
     * 记录结构化访问日志（在响应完成后异步输出）。
     *
     * <p>先于过滤器链记录请求开始时间与 traceId，并通过 {@code doFinally} 在响应完成后
     * 输出 JSON 访问日志（含敏感参数脱敏、客户端真实 IP、路由、状态码、耗时、指标上报）。
     * 顺序 {@code HIGHEST_PRECEDENCE + 1}，确保被拒绝的请求也被记录。
     *
     * @param exchange 服务器 Web 交换上下文
     * @param chain    网关过滤器链
     * @return 完成信号 Mono
     */
    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        long startTime = System.currentTimeMillis();
        String traceId = exchange.getRequest().getHeaders().getFirst(GatewayConstants.HEADER_TRACE_ID);
        if (traceId == null || traceId.isBlank()) {
            traceId = TraceIdGenerator.generateTraceId();
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
     * P2-2: 输出结构化 JSON 访问日志（替代管道分隔格式，便于 Loki/ELK 采集查询）
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
        String query = sanitizeQuery(request);
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

        // P2-2: 结构化 JSON 日志（便于 Loki 标签查询和 ELK 采集）
        Map<String, Object> logData = new LinkedHashMap<>();
        logData.put("traceId", safeTraceId(traceId));
        logData.put("method", method);
        logData.put("path", path);
        logData.put("query", query);
        logData.put("clientIp", clientIp);
        logData.put("status", status);
        logData.put("latencyMs", duration);
        logData.put("routeId", routeId);
        logData.put("targetUri", targetUri);
        logData.put("userId", userId != null ? userId : "-");
        logData.put("userAgent", userAgent != null ? userAgent : "-");

        String jsonLog = RemiJson.toJson(logData);

        if (status >= 500) {
            log.error(jsonLog);
        } else if (status >= 400) {
            log.warn(jsonLog);
        } else {
            log.info(jsonLog);
        }
    }

    /**
     * P0-8: 查询参数脱敏 + 截断
     *
     * <p>对敏感查询参数（token / password / secret 等）的值替换为 {@code ***}，
     * 防止 JWT / OAuth2 授权码 / 密码等泄漏到 access log 文件。
     *
     * <p>同时保持查询字符串结构（key=value&key=value），便于日志排查；
     * 总长度超过 {@link #MAX_QUERY_LENGTH} 时截断并标记。
     *
     * @param request 服务器 HTTP 请求
     * @return 脱敏后的查询字符串，无查询参数时返回 "-"
     */
    private String sanitizeQuery(ServerHttpRequest request) {
        MultiValueMap<String, String> queryParams = request.getQueryParams();
        if (queryParams == null || queryParams.isEmpty()) {
            return "-";
        }

        // 使用 LinkedHashMap 保持参数顺序（便于日志排查）
        Map<String, String> sanitized = new LinkedHashMap<>();
        for (Map.Entry<String, List<String>> entry : queryParams.entrySet()) {
            String key = entry.getKey();
            String firstValue = entry.getValue() != null && !entry.getValue().isEmpty()
                    ? entry.getValue().get(0) : "";
            // 敏感参数（小写匹配）的值替换为 ***
            if (SENSITIVE_QUERY_PARAMS.contains(key.toLowerCase())) {
                sanitized.put(key, MASKED_VALUE);
            } else {
                sanitized.put(key, firstValue);
            }
        }

        // 重建查询字符串
        StringBuilder sb = new StringBuilder();
        for (Map.Entry<String, String> entry : sanitized.entrySet()) {
            if (sb.length() > 0) {
                sb.append('&');
            }
            sb.append(entry.getKey()).append('=').append(entry.getValue());
        }

        // 截断防日志膨胀
        String result = sb.toString();
        if (result.length() > MAX_QUERY_LENGTH) {
            result = result.substring(0, MAX_QUERY_LENGTH) + "...";
        }
        return result;
    }

    /**
     * 提取客户端真实 IP（P0-3：复用 GatewayIpUtils 的可信代理链校验）
     *
     * <p>不直接信任 {@code X-Forwarded-For}，先校验直连 IP 是否为可信代理。
     * 仅当直连 IP 是可信代理（本地回环或内网私有地址）时，才使用 X-Forwarded-For / X-Real-IP。
     *
     * @param request 服务器 HTTP 请求
     * @return 客户端 IP
     */
    private String extractClientIp(ServerHttpRequest request) {
        return GatewayIpUtils.getClientIp(request);
    }

    /**
     * traceId 安全输出（确保非 null）
     *
     * @param traceId 链路追踪 ID
     * @return 非 null 的 traceId
     */
    private String safeTraceId(String traceId) {
        return traceId != null ? traceId : TraceIdGenerator.generateTraceId();
    }

    /**
     * 过滤器执行顺序：{@code HIGHEST_PRECEDENCE + 1}。
     *
     * <p>早于鉴权（+10）、IP 黑名单（+3）等过滤器，确保所有请求（含被拒绝的）都能被访问日志记录。
     *
     * @return 顺序值
     */
    @Override
    public int getOrder() {
        return Ordered.HIGHEST_PRECEDENCE + 1;
    }
}
