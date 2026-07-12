package com.njydsz.pmis.gateway.filter;

import com.njydsz.pmis.common.constant.CommonConstants;
import com.njydsz.pmis.common.token.JwtTokenProvider;
import com.njydsz.pmis.common.util.TraceIdUtil;
import com.njydsz.pmis.gateway.config.CachedJwtValidator;
import io.jsonwebtoken.Claims;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.util.List;

/**
 * WebSocket 认证过滤器（P2-12）
 *
 * <p>WebSocket 握手时 Token 通常通过查询参数或 Sec-WebSocket-Protocol 传递，
 * 而非标准的 Authorization 头。本过滤器为 WebSocket 路径提供独立认证策略。
 *
 * <h3>Token 提取优先级</h3>
 * <ol>
 *   <li>查询参数 {@code token}（最常用，前端 WebSocket 构造时拼接）</li>
 *   <li>查询参数 {@code access_token}（OAuth2 风格）</li>
 *   <li>Sec-WebSocket-Protocol 头（协议升级前最后一个 Token 项）</li>
 *   <li>Authorization 头（标准方式，部分客户端支持）</li>
 * </ol>
 *
 * <h3>认证流程</h3>
 * <ol>
 *   <li>仅对 WebSocket 升级请求（Upgrade: websocket）生效</li>
 *   <li>提取 Token → 校验 → 注入 X-User-* 内部头</li>
 *   <li>校验失败返回 401（在握手阶段拒绝，不建立连接）</li>
 * </ol>
 *
 * <h3>执行顺序</h3>
 * <p>{@code HIGHEST_PRECEDENCE + 8}，在 {@link AuthGlobalFilter}(+10) 之前执行，
 * WebSocket 请求由本过滤器处理并标记为已认证，{@link AuthGlobalFilter} 检测到标记后跳过。
 *
 * @author ydsz-pmis-team
 * @since 2.2.0
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class WebSocketAuthFilter implements GlobalFilter, Ordered {

    /** WebSocket 路径前缀 */
    private static final String WS_PATH_PREFIX = "/ws";

    /** WebSocket 升级请求头标识 */
    private static final String HEADER_UPGRADE = "Upgrade";
    private static final String UPGRADE_WEBSOCKET = "websocket";

    /** exchange attribute key: WebSocket 已认证标记 */
    public static final String ATTR_WS_AUTHENTICATED = "__ws_authenticated";

    /** JWT 缓存校验器 */
    private final CachedJwtValidator cachedJwtValidator;

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        ServerHttpRequest request = exchange.getRequest();
        String path = request.getURI().getPath();

        // 仅处理 WebSocket 路径
        if (!path.startsWith(WS_PATH_PREFIX)) {
            return chain.filter(exchange);
        }

        // 仅处理 WebSocket 升级请求
        String upgradeHeader = request.getHeaders().getFirst(HEADER_UPGRADE);
        if (upgradeHeader == null || !UPGRADE_WEBSOCKET.equalsIgnoreCase(upgradeHeader)) {
            // 非 WebSocket 升级请求（可能是 HTTP 请求到 /ws 路径），交给后续过滤器
            return chain.filter(exchange);
        }

        // 提取 Token
        String jwt = extractToken(request);
        if (jwt == null || jwt.isBlank()) {
            log.warn("[WsAuth] WebSocket 握手缺少 Token path={}", path);
            return chain.filter(exchange); // 交给 AuthGlobalFilter 返回 401
        }

        // 校验 Token（使用 Caffeine 缓存）
        Claims claims = cachedJwtValidator.validateAndParse(jwt);
        if (claims == null) {
            log.warn("[WsAuth] WebSocket 握手 Token 无效 path={}", path);
            return chain.filter(exchange); // 交给 AuthGlobalFilter 返回 401
        }

        String type = claims.get("type", String.class);
        if (!"access".equals(type)) {
            log.warn("[WsAuth] WebSocket 握手 Token 类型错误 type={}", type);
            return chain.filter(exchange);
        }

        // 提取用户信息
        String userId = claims.getSubject();
        String username = claims.get("username", String.class);
        @SuppressWarnings("unchecked")
        List<String> roles = (List<String>) claims.get("roles");
        @SuppressWarnings("unchecked")
        List<String> permissions = (List<String>) claims.get("permissions");

        String userIdStr = String.valueOf(userId);
        String usernameStr = username == null ? "" : username;
        String rolesStr = roles == null ? "" : String.join(",", roles);
        String permsStr = permissions == null ? "" : String.join(",", permissions);

        String traceId = TraceIdUtil.generate();

        // 注入用户信息头 + 标记已认证
        ServerHttpRequest mutated = request.mutate()
                .headers(h -> {
                    h.set(CommonConstants.HEADER_TRACE_ID, traceId);
                    h.set(CommonConstants.HEADER_USER_ID, userIdStr);
                    h.set(CommonConstants.HEADER_USERNAME, usernameStr);
                    h.set(CommonConstants.HEADER_USER_ROLES, rolesStr);
                    h.set(CommonConstants.HEADER_USER_PERMISSIONS, permsStr);
                })
                .build();

        exchange.getAttributes().put(ATTR_WS_AUTHENTICATED, true);
        exchange.getResponse().getHeaders().add(CommonConstants.HEADER_TRACE_ID, traceId);

        log.info("[WsAuth] WebSocket 认证成功 userId={} path={}", userIdStr, path);

        return chain.filter(exchange.mutate().request(mutated).build());
    }

    /**
     * 从 WebSocket 请求中提取 JWT Token
     *
     * <p>提取优先级：查询参数 token → access_token → Sec-WebSocket-Protocol → Authorization
     *
     * @param request 服务器 HTTP 请求
     * @return JWT Token，未找到返回 null
     */
    private String extractToken(ServerHttpRequest request) {
        // 1. 查询参数 token
        String token = request.getQueryParams().getFirst("token");
        if (token != null && !token.isBlank()) {
            return token;
        }

        // 2. 查询参数 access_token
        token = request.getQueryParams().getFirst("access_token");
        if (token != null && !token.isBlank()) {
            return token;
        }

        // 3. Sec-WebSocket-Protocol 头（部分客户端通过此头传递 Token）
        String protocol = request.getHeaders().getFirst("Sec-WebSocket-Protocol");
        if (protocol != null && !protocol.isBlank()) {
            // 取最后一个协议项作为 Token
            String[] parts = protocol.split(",");
            String last = parts[parts.length - 1].trim();
            if (!last.isEmpty()) {
                return last;
            }
        }

        // 4. Authorization 头（标准方式）
        String authHeader = request.getHeaders().getFirst("Authorization");
        if (authHeader != null && authHeader.startsWith("Bearer ")) {
            return authHeader.substring(7);
        }

        return null;
    }

    @Override
    public int getOrder() {
        return Ordered.HIGHEST_PRECEDENCE + 8;
    }
}
