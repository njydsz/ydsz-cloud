package com.njydsz.pmis.gateway.filter;

import com.njydsz.pmis.common.api.R;
import com.njydsz.pmis.common.constant.CommonConstants;
import com.njydsz.pmis.common.token.JwtTokenProvider;
import io.jsonwebtoken.Claims;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.UUID;

/**
 * 认证全局过滤器
 *
 * <p>核心职责:
 * <ol>
 *   <li>提取 Authorization 头中的 JWT</li>
 *   <li>使用 JwtTokenProvider 验证签名 + 解析 Claims</li>
 *   <li>检查 Token 黑名单 (Redis)</li>
 *   <li>将 userId/username/roles/permissions 写入 X-User-* 头透传给下游</li>
 * </ol>
 *
 * @author ydsz-pmis-team
 * @since 1.0.0
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AuthGlobalFilter implements GlobalFilter, Ordered {

    /** Token 黑名单前缀 (与 auth 服务保持一致) */
    private static final String TOKEN_BLACKLIST_PREFIX = "pmis:token:blacklist:";
    // BLACKLISTED reserved for future blacklist check implementation

    /** 白名单(不校验 Token) */
    private static final List<String> WHITE_LIST = List.of(
            "/api/v1/auth/login",
            "/api/v1/auth/refresh",
            "/api/v1/auth/captcha",
            "/api/v1/auth/register",
            "/api/v1/health"
    );

    private final JwtTokenProvider jwtTokenProvider;
    private final ReactiveStringRedisTemplate redisTemplate;

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        ServerHttpRequest request = exchange.getRequest();
        String path = request.getURI().getPath();

        // 链路追踪 ID
        final String traceId;
        String traceIdTmp = request.getHeaders().getFirst(CommonConstants.HEADER_TRACE_ID);
        if (traceIdTmp == null || traceIdTmp.isEmpty()) {
            traceId = UUID.randomUUID().toString().replace("-", "").substring(0, 16);
        } else {
            traceId = traceIdTmp;
        }

        // 跨域预检直接放行
        if ("OPTIONS".equalsIgnoreCase(request.getMethod().name())) {
            return chain.filter(exchange.mutate()
                    .request(r -> r.header(CommonConstants.HEADER_TRACE_ID, traceId))
                    .build());
        }

        // 白名单直接放行
        if (isWhiteList(path)) {
            return chain.filter(exchange.mutate()
                    .request(r -> r.header(CommonConstants.HEADER_TRACE_ID, traceId))
                    .build());
        }

        // 提取 Token
        String authHeader = request.getHeaders().getFirst("Authorization");
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            return unauthorized(exchange, traceId, "缺少认证 Token");
        }
        String jwt = authHeader.substring(7);

        // 验证 Token
        if (!jwtTokenProvider.validateToken(jwt)) {
            return unauthorized(exchange, traceId, "Token 无效或已过期");
        }

        // 黑名单检查
        return redisTemplate.hasKey(TOKEN_BLACKLIST_PREFIX + jwt)
                .flatMap(blacklisted -> {
                    if (Boolean.TRUE.equals(blacklisted)) {
                        return unauthorized(exchange, traceId, "Token 已失效,请重新登录");
                    }
                    // 解析 Claims
                    Claims claims;
                    try {
                        claims = jwtTokenProvider.parseClaims(jwt);
                    } catch (Exception e) {
                        log.warn("[AuthFilter] 解析 JWT 失败: {}", e.getMessage());
                        return unauthorized(exchange, traceId, "Token 解析失败");
                    }

                    String type = claims.get("type", String.class);
                    if (!"access".equals(type)) {
                        return unauthorized(exchange, traceId, "非访问 Token");
                    }

                    Long userId = Long.parseLong(claims.getSubject());
                    String username = claims.get("username", String.class);
                    @SuppressWarnings("unchecked")
                    List<String> roles = (List<String>) claims.get("roles");
                    @SuppressWarnings("unchecked")
                    List<String> permissions = (List<String>) claims.get("permissions");

                    // 透传用户信息
                    ServerHttpRequest mutated = request.mutate()
                            .header(CommonConstants.HEADER_TRACE_ID, traceId)
                            .header(CommonConstants.HEADER_USER_ID, String.valueOf(userId))
                            .header(CommonConstants.HEADER_USERNAME, username == null ? "" : username)
                            .header("X-User-Roles", roles == null ? "" : String.join(",", roles))
                            .header("X-User-Permissions", permissions == null ? "" : String.join(",", permissions))
                            .header("Authorization", authHeader)
                            .build();

                    return chain.filter(exchange.mutate().request(mutated).build());
                });
    }

    private boolean isWhiteList(String path) {
        return WHITE_LIST.stream().anyMatch(path::startsWith);
    }

    private Mono<Void> unauthorized(ServerWebExchange exchange, String traceId, String msg) {
        ServerHttpResponse response = exchange.getResponse();
        response.setStatusCode(HttpStatus.UNAUTHORIZED);
        response.getHeaders().setContentType(MediaType.APPLICATION_JSON);
        response.getHeaders().add(CommonConstants.HEADER_TRACE_ID, traceId);

        R<Void> body = R.failed(20001, msg);
        body.setTraceId(traceId);
        byte[] bytes = com.alibaba.fastjson2.JSON.toJSONString(body).getBytes(StandardCharsets.UTF_8);

        DataBuffer buffer = response.bufferFactory().wrap(bytes);
        return response.writeWith(Mono.just(buffer));
    }

    @Override
    public int getOrder() {
        return Ordered.HIGHEST_PRECEDENCE + 10;
    }
}
