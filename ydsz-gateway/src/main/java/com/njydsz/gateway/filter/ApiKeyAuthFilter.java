package com.njydsz.gateway.filter;

import java.util.Map;
import java.util.Set;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.util.AntPathMatcher;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;
import com.njydsz.common.core.code.BaseResultCode;
import com.njydsz.common.core.response.BaseResponse;
import com.njydsz.common.core.trace.TraceIdGenerator;
import com.njydsz.common.json.YdszJson;
import com.njydsz.common.sentry.SentryObservation;
import com.njydsz.common.sentry.domain.AlertEvent;
import com.njydsz.common.sentry.domain.AlertSeverity;
import com.njydsz.gateway.config.GatewayConstants;
import com.njydsz.gateway.config.GatewayFilterOrder;

/**
 * P1-3: API Key 认证过滤器
 *
 * <p>支持通过 API Key 认证的外部系统接入，对标主流竞品（Kong key-auth、APISIX key-auth）。
 *
 * <h3>认证流程</h3>
 * <ol>
 *   <li>检查请求是否需要 API Key 认证（路径匹配 API 路径白名单）</li>
 *   <li>从请求头 {@code X-API-Key} 或查询参数 {@code api_key} 提取 API Key</li>
 *   <li>验证 API Key 是否在配置的白名单中</li>
 *   <li>注入 API Key 标识到下游请求头</li>
 * </ol>
 *
 * <h3>配置方式</h3>
 * <pre>
 * ydsz:
 *   gateway:
 *     api-key:
 *       enabled: true
 *       keys: "key1,key2,key3"     # 逗号分隔的有效 API Key
 *       protected-paths: "/api/project/**,/api/workflow/**"  # 需要 API Key 的路径
 * </pre>
 *
 * <p>当 JWT Bearer Token 认证失败时，此过滤器提供备选认证方式。
 * 两者互补：内部用户用 JWT，外部系统用 API Key。
 *
 * @since 1.0.0
 * @author ydsz-team
 */
@Slf4j
@Component
@ConditionalOnProperty(prefix = "ydsz.gateway.filter", name = "api-key-auth", havingValue = "true", matchIfMissing = true)
public class ApiKeyAuthFilter implements GlobalFilter, Ordered {

    private static final String HEADER_API_KEY = "X-API-Key";
    private static final String QUERY_API_KEY = "api_key";
    private static final String HEADER_API_KEY_USER = "X-API-Key-User";

    private final AntPathMatcher pathMatcher = new AntPathMatcher();

    @Value("${ydsz.gateway.api-key.enabled:false}")
    private boolean enabled;

    @Value("${ydsz.gateway.api-key.keys:}")
    private String validKeys;

    @Value("${ydsz.gateway.api-key.protected-paths:}")
    private String protectedPaths;

    /**
     * API Key 认证过滤器：为外部系统提供 JWT 之外的备选认证方式。
     *
     * <p>未启用 / 非受保护路径 / 已有 JWT 身份（X-User-Id 存在）时直接放行；
     * 否则从 {@code X-API-Key} 头或 {@code api_key} 查询参数提取并校验，
     * 通过则注入 {@code X-API-Key-User} 标识后放行，缺失 / 无效分别返回 401 / 403。
     *
     * @param exchange 服务器 Web 交换上下文
     * @param chain    网关过滤器链
     * @return 放行或拒绝（401 / 403）的完成信号 Mono
     */
    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        if (!enabled) {
            return chain.filter(exchange);
        }

        ServerHttpRequest request = exchange.getRequest();
        String path = request.getURI().getPath();

        // 仅对受保护路径检查 API Key
        if (!isProtectedPath(path)) {
            return chain.filter(exchange);
        }

        // 如果已有 JWT 认证（X-User-Id 存在），跳过 API Key 检查
        String userId = request.getHeaders().getFirst(GatewayConstants.HEADER_USER_ID);
        if (userId != null && !userId.isEmpty()) {
            return chain.filter(exchange);
        }

        // 提取 API Key
        String apiKey = extractApiKey(request);
        if (apiKey == null || apiKey.isEmpty()) {
            return rejectMissingApiKey(exchange);
        }

        // 验证 API Key
        if (!isValidApiKey(apiKey)) {
            return rejectInvalidApiKey(exchange, apiKey);
        }

        // 注入 API Key 标识到下游请求头
        ServerHttpRequest mutated = request.mutate()
                .header(HEADER_API_KEY_USER, "apikey:" + maskApiKey(apiKey))
                .build();

        if (log.isDebugEnabled()) {
            log.debug("[ApiKeyAuth] 路径 {} API Key 认证通过 ({})", path, maskApiKey(apiKey));
        }

        return chain.filter(exchange.mutate().request(mutated).build());
    }

    /**
     * 从请求头或查询参数提取 API Key
     */
    private String extractApiKey(ServerHttpRequest request) {
        // 优先从请求头提取
        String headerKey = request.getHeaders().getFirst(HEADER_API_KEY);
        if (headerKey != null && !headerKey.isEmpty()) {
            return headerKey;
        }
        // 从查询参数提取
        String queryKey = request.getQueryParams().getFirst(QUERY_API_KEY);
        if (queryKey != null && !queryKey.isEmpty()) {
            return queryKey;
        }
        return null;
    }

    /**
     * 验证 API Key 是否有效
     */
    private boolean isValidApiKey(String apiKey) {
        if (validKeys == null || validKeys.isBlank()) {
            return false;
        }
        Set<String> keySet = Set.of(validKeys.split(","));
        return keySet.contains(apiKey.trim());
    }

    /**
     * 检查路径是否需要 API Key 认证
     */
    private boolean isProtectedPath(String path) {
        if (protectedPaths == null || protectedPaths.isBlank()) {
            return false;
        }
        String[] patterns = protectedPaths.split(",");
        for (String pattern : patterns) {
            if (pathMatcher.match(pattern.trim(), path)) {
                return true;
            }
        }
        return false;
    }

    /**
     * 返回 401 未提供 API Key
     */
    private Mono<Void> rejectMissingApiKey(ServerWebExchange exchange) {
        ServerHttpResponse response = exchange.getResponse();
        response.setStatusCode(HttpStatus.UNAUTHORIZED);
        response.getHeaders().setContentType(MediaType.APPLICATION_JSON);

        String traceId = TraceIdGenerator.generateSortableTraceId();
        BaseResponse<Void> body = BaseResponse.error(BaseResultCode.UNAUTHORIZED, "API Key 缺失，请提供 X-API-Key 或 api_key 参数");
        body.assignTraceId(traceId);
        response.getHeaders().add(GatewayConstants.HEADER_TRACE_ID, traceId);

        byte[] bytes = YdszJson.toJsonBytes(body);
        DataBuffer buffer = response.bufferFactory().wrap(bytes);

        // P1-3: API Key 缺失 → sentry 告警收敛（P2 安全事件）
        SentryObservation.alert(AlertEvent.builder()
                .name("gateway.api_key.missing")
                .severity(AlertSeverity.P2)
                .summary("API Key 缺失")
                .description("请求未提供有效的 API Key")
                .category("security")
                .labels(Map.of("path", exchange.getRequest().getURI().getPath()))
                .build());
        log.warn("[ApiKeyAuth] API Key 缺失 path={}", exchange.getRequest().getURI().getPath());
        return response.writeWith(Mono.just(buffer));
    }

    /**
     * 返回 403 API Key 无效
     */
    private Mono<Void> rejectInvalidApiKey(ServerWebExchange exchange, String apiKey) {
        ServerHttpResponse response = exchange.getResponse();
        response.setStatusCode(HttpStatus.FORBIDDEN);
        response.getHeaders().setContentType(MediaType.APPLICATION_JSON);

        String traceId = TraceIdGenerator.generateSortableTraceId();
        BaseResponse<Void> body = BaseResponse.error(BaseResultCode.FORBIDDEN, "API Key 无效");
        body.assignTraceId(traceId);
        response.getHeaders().add(GatewayConstants.HEADER_TRACE_ID, traceId);

        byte[] bytes = YdszJson.toJsonBytes(body);
        DataBuffer buffer = response.bufferFactory().wrap(bytes);

        // P1-3: API Key 无效 → sentry 告警收敛（P1 安全事件）
        SentryObservation.alert(AlertEvent.builder()
                .name("gateway.api_key.invalid")
                .severity(AlertSeverity.P1)
                .summary("API Key 无效")
                .description("提供了无效的 API Key，可能是伪造或过期")
                .category("security")
                .labels(Map.of("api_key_masked", maskApiKey(apiKey),
                        "path", exchange.getRequest().getURI().getPath()))
                .build());
        log.warn("[ApiKeyAuth] API Key 无效 key={} path={}",
                maskApiKey(apiKey), exchange.getRequest().getURI().getPath());
        return response.writeWith(Mono.just(buffer));
    }

    /**
     * API Key 脱敏
     */
    private String maskApiKey(String apiKey) {
        if (apiKey == null || apiKey.length() <= 8) {
            return "***";
        }
        return apiKey.substring(0, 4) + "***" + apiKey.substring(apiKey.length() - 4);
    }

    /**
     * 过滤器顺序：在 AuthGlobalFilter 之后执行，作为 JWT 认证的备选方案
     *
     * @return 顺序值
     */
    @Override
    public int getOrder() {
        return GatewayFilterOrder.API_KEY_AUTH.getOrder();
    }
}
