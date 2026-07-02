package com.njydsz.pmis.gateway.filter;

import com.njydsz.pmis.common.api.Result;
import com.njydsz.pmis.common.constant.CommonConstants;
import com.njydsz.pmis.common.token.JwtTokenProvider;
import io.jsonwebtoken.Claims;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.context.MessageSource;
import org.springframework.core.Ordered;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.beans.factory.ObjectProvider;
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
import java.util.Locale;
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
 *   <li>透传 Accept-Language 头，确保下游服务可基于其进行 i18n 消息解析</li>
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

    /** 默认 Accept-Language（当请求未携带时使用） */
    private static final String DEFAULT_ACCEPT_LANGUAGE = "zh-CN";

    /** 白名单(不校验 Token) */
    private static final List<String> WHITE_LIST = List.of(
            "/api/v1/auth/login",
            "/api/v1/auth/refresh",
            "/api/v1/auth/captcha",
            "/api/v1/auth/register",
            "/api/v1/health"
    );

    /** 支持的 Locale 列表（用于 Accept-Language 协商） */
    private static final List<Locale> SUPPORTED_LOCALES = List.of(Locale.SIMPLIFIED_CHINESE, Locale.US);

    /** JWT Token 生成与校验工具 */
    private final JwtTokenProvider jwtTokenProvider;
    /** Redis 响应式模板（用于 Token 黑名单检查） */
    private final ReactiveStringRedisTemplate redisTemplate;
    /** 国际化消息源（可选注入，WebFlux 环境下通过 ObjectProvider 安全获取） */
    private final ObjectProvider<MessageSource> messageSourceProvider;

    /**
     * 核心过滤逻辑：链路追踪 → Accept-Language 提取 → 白名单放行 → Token 校验 → 黑名单检查 → 用户信息透传
     *
     * @param exchange 服务器 Web 交换上下文
     * @param chain    网关过滤器链
     * @return 完成信号 Mono
     */
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

        // Accept-Language 提取（默认 zh-CN），透传给下游服务用于 i18n
        String acceptLanguage = request.getHeaders().getFirst("Accept-Language");
        if (acceptLanguage == null || acceptLanguage.isEmpty()) {
            acceptLanguage = DEFAULT_ACCEPT_LANGUAGE;
        }
        final String finalAcceptLanguage = acceptLanguage;

        // 统一写入 traceId 到响应头，确保所有响应（成功/失败/OPTIONS/白名单）都携带链路追踪 ID
        exchange.getResponse().getHeaders().add(CommonConstants.HEADER_TRACE_ID, traceId);

        // 跨域预检直接放行
        if ("OPTIONS".equalsIgnoreCase(request.getMethod().name())) {
            return chain.filter(exchange.mutate()
                    .request(r -> r
                            .header(CommonConstants.HEADER_TRACE_ID, traceId)
                            .header("Accept-Language", finalAcceptLanguage))
                    .build());
        }

        // 白名单直接放行
        if (isWhiteList(path)) {
            return chain.filter(exchange.mutate()
                    .request(r -> r
                            .header(CommonConstants.HEADER_TRACE_ID, traceId)
                            .header("Accept-Language", finalAcceptLanguage))
                    .build());
        }

        // 提取 Token
        String authHeader = request.getHeaders().getFirst("Authorization");
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            return unauthorized(exchange, traceId, 20001, "error.auth.token_missing", finalAcceptLanguage);
        }
        String jwt = authHeader.substring(7);

        // 验证 Token
        if (!jwtTokenProvider.validateToken(jwt)) {
            return unauthorized(exchange, traceId, 20003, "error.auth.token_invalid", finalAcceptLanguage);
        }

        // 黑名单检查
        return redisTemplate.hasKey(TOKEN_BLACKLIST_PREFIX + jwt)
                .flatMap(blacklisted -> {
                    if (Boolean.TRUE.equals(blacklisted)) {
                        return unauthorized(exchange, traceId, 20002, "error.auth.token_expired", finalAcceptLanguage);
                    }
                    // 解析 Claims
                    Claims claims;
                    try {
                        claims = jwtTokenProvider.parseClaims(jwt);
                    } catch (Exception e) {
                        log.warn("[AuthFilter] 解析 JWT 失败: {}", e.getMessage());
                        return unauthorized(exchange, traceId, 20003, "error.auth.token_parse_failed", finalAcceptLanguage);
                    }

                    String type = claims.get("type", String.class);
                    if (!"access".equals(type)) {
                        return unauthorized(exchange, traceId, 20003, "error.auth.not_access_token", finalAcceptLanguage);
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
                            .header("Accept-Language", finalAcceptLanguage)
                            .header(CommonConstants.HEADER_USER_ID, String.valueOf(userId))
                            .header(CommonConstants.HEADER_USERNAME, username == null ? "" : username)
                            .header("X-User-Roles", roles == null ? "" : String.join(",", roles))
                            .header("X-User-Permissions", permissions == null ? "" : String.join(",", permissions))
                            .header("Authorization", authHeader)
                            .build();

                    return chain.filter(exchange.mutate().request(mutated).build());
                });
    }

    /**
     * 判断请求路径是否在白名单中
     *
     * @param path 请求路径
     * @return true 表示在白名单中（无需鉴权），false 表示需要鉴权
     */
    private boolean isWhiteList(String path) {
        return WHITE_LIST.stream().anyMatch(path::startsWith);
    }

    /**
     * 返回 401 未授权响应
     *
     * <p>使用 error code + i18n message key 解析本地化消息。
     * 当 MessageSource 不可用时，回退到 message key 作为消息内容。
     *
     * @param exchange       服务器 Web 交换上下文
     * @param traceId        链路追踪 ID
     * @param code           业务错误码
     * @param messageKey     国际化消息 key
     * @param acceptLanguage Accept-Language 请求头值
     * @return 完成信号 Mono
     */
    private Mono<Void> unauthorized(ServerWebExchange exchange, String traceId, int code, String messageKey, String acceptLanguage) {
        ServerHttpResponse response = exchange.getResponse();
        response.setStatusCode(HttpStatus.UNAUTHORIZED);
        response.getHeaders().setContentType(MediaType.APPLICATION_JSON);
        // traceId 已在 filter 开头统一写入响应头，此处无需重复设置

        Locale locale = resolveLocale(acceptLanguage);
        String msg = resolveMessage(messageKey, locale);

        Result<Void> body = Result.failed(code, msg);
        body.setTraceId(traceId);
        byte[] bytes = com.alibaba.fastjson2.JSON.toJSONString(body).getBytes(StandardCharsets.UTF_8);

        DataBuffer buffer = response.bufferFactory().wrap(bytes);
        return response.writeWith(Mono.just(buffer));
    }

    /**
     * 根据 Accept-Language 头解析 Locale
     *
     * <p>使用 {@link Locale.LanguageRange} 进行 RFC 4647 语言标签匹配，
     * 在支持的语言（简体中文、英文 US）中选择最佳匹配；未匹配时回退到简体中文。
     *
     * @param acceptLanguage Accept-Language 头值
     * @return 解析后的 Locale
     */
    private Locale resolveLocale(String acceptLanguage) {
        if (acceptLanguage == null || acceptLanguage.isEmpty()) {
            return Locale.SIMPLIFIED_CHINESE;
        }
        try {
            List<Locale.LanguageRange> ranges = Locale.LanguageRange.parse(acceptLanguage);
            return Locale.filter(ranges, SUPPORTED_LOCALES).stream()
                    .findFirst()
                    .orElse(Locale.SIMPLIFIED_CHINESE);
        } catch (IllegalArgumentException e) {
            // Accept-Language 格式非法时回退到默认中文
            return Locale.SIMPLIFIED_CHINESE;
        }
    }

    /**
     * 使用 MessageSource 解析国际化消息
     *
     * <p>当 MessageSource 不可用（如未配置）时，回退到 message key 本身作为消息内容。
     *
     * @param messageKey 消息 key
     * @param locale     目标 Locale
     * @return 解析后的本地化消息
     */
    private String resolveMessage(String messageKey, Locale locale) {
        MessageSource ms = messageSourceProvider.getIfAvailable();
        if (ms == null) {
            return messageKey;
        }
        return ms.getMessage(messageKey, null, messageKey, locale);
    }

    /**
     * 过滤器执行顺序（高优先级，确保最先执行鉴权）
     *
     * @return 过滤器顺序值
     */
    @Override
    public int getOrder() {
        return Ordered.HIGHEST_PRECEDENCE + 10;
    }
}
