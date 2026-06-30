package com.njydsz.pmis.gateway.filter;

import com.njydsz.pmis.common.api.R;
import com.njydsz.pmis.common.constant.CommonConstants;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.core.io.buffer.DataBuffer;
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
 * <p>从请求头提取 JWT Token，解析后写入 X-User-* 头透传给下游服务。
 * 白名单路径不校验。
 *
 * @author ydsz-pmis-team
 * @since 1.0.0
 */
@Slf4j
@Component
public class AuthGlobalFilter implements GlobalFilter, Ordered {

    /** 白名单（不校验 Token） */
    private static final List<String> WHITE_LIST = List.of(
            "/api/v1/auth/login",
            "/api/v1/auth/refresh",
            "/api/v1/auth/captcha",
            "/api/v1/auth/register",
            "/api/v1/health"
    );

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        ServerHttpRequest request = exchange.getRequest();
        String path = request.getURI().getPath();

        // 链路追踪 ID
        String traceId = request.getHeaders().getFirst(CommonConstants.HEADER_TRACE_ID);
        if (traceId == null || traceId.isEmpty()) {
            traceId = UUID.randomUUID().toString().replace("-", "").substring(0, 16);
        }

        // 跨域预检直接放行
        if ("OPTIONS".equalsIgnoreCase(request.getMethodValue())) {
            return chain.filter(exchange);
        }

        // 白名单直接放行
        if (isWhiteList(path)) {
            return chain.filter(exchange.mutate()
                    .request(r -> r.header(CommonConstants.HEADER_TRACE_ID, traceId))
                    .build());
        }

        // 提取 Token
        String token = request.getHeaders().getFirst("Authorization");
        if (token == null || !token.startsWith("Bearer ")) {
            return unauthorized(exchange, "缺少认证 Token");
        }
        String jwt = token.substring(7);

        // TODO: 解析 JWT，提取 userId、username、deptId 等
        // 实际生产中应在此处验证 JWT 签名、过期时间、是否在黑名单中
        // 此处为脚手架，假设解析成功
        Long userId = 1L;
        String username = "admin";
        Long deptId = 1L;

        // 透传用户信息
        ServerHttpRequest mutated = request.mutate()
                .header(CommonConstants.HEADER_TRACE_ID, traceId)
                .header(CommonConstants.HEADER_USER_ID, String.valueOf(userId))
                .header(CommonConstants.HEADER_USERNAME, username)
                .header(CommonConstants.HEADER_USER_DEPT, String.valueOf(deptId))
                .header("Authorization", token)
                .build();

        return chain.filter(exchange.mutate().request(mutated).build());
    }

    private boolean isWhiteList(String path) {
        return WHITE_LIST.stream().anyMatch(path::startsWith);
    }

    private Mono<Void> unauthorized(ServerWebExchange exchange, String msg) {
        ServerHttpResponse response = exchange.getResponse();
        response.setStatusCode(HttpStatus.UNAUTHORIZED);
        response.getHeaders().setContentType(MediaType.APPLICATION_JSON);

        R<Void> body = R.failed(20001, msg);
        body.setTraceId(exchange.getRequest().getHeaders().getFirst(CommonConstants.HEADER_TRACE_ID));
        byte[] bytes = com.alibaba.fastjson2.JSON.toJSONString(body).getBytes(StandardCharsets.UTF_8);

        DataBuffer buffer = response.bufferFactory().wrap(bytes);
        return response.writeWith(Mono.just(buffer));
    }

    @Override
    public int getOrder() {
        return Ordered.HIGHEST_PRECEDENCE + 10;
    }
}
