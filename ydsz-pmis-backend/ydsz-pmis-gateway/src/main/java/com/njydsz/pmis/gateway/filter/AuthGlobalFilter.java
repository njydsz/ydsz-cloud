ackage com.njydsz.pmis.gateway.filter;

import java.nio.charset.StandardCharsets;
import java.util.Set;

import com.njydsz.pmis.common.json.YdszJson;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;

import com.njydsz.pmis.common.auth.model.UserInfo;
import com.njydsz.pmis.common.core.response.BaseResponse;
import com.njydsz.pmis.common.core.trace.TraceIdGenerator;
import com.njydsz.pmis.gateway.config.CachedJwtValidator;
import com.njydsz.pmis.gateway.config.GatewayConstants;
import com.njydsz.pmis.gateway.config.InternalHeaderSigner;
import com.njydsz.pmis.gateway.config.PathGuard;
import com.njydsz.pmis.gateway.config.SecurityHeadersProperties;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Mono;

/**
 * 认证全局过滤器（P0-C5 安全加固）
 *
 * <p>核心职责:
 * <ol>
 *   <li>路径规范化：拦截 {@code ..}、{@code //} 等路径穿越攻击</li>
 *   <li>剥离客户端伪造的内部头：所有 {@code X-User-*} / {@code X-Internal-*}
 *       头在透传前必须先删除客户端传入的值</li>
 *   <li>提取 Authorization 头中的 JWT 并校验</li>
 *   <li>检查 Token 黑名单（Redis）</li>
 *   <li>将 userId/username/roles/permissions 写入 X-User-* 头透传给下游</li>
 *   <li>注入 {@code X-Internal-Sig} + {@code X-Internal-Ts} 签名头，下游可校验</li>
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

    /**
     * 白名单(不校验 Token)。
     *
     * <p>P0-C5 改为精确匹配：仅路径完全相等才放行，
     * 杜绝 {@code /auth/login/../users/list} 等 startsWith 绕过。
     */
    private static final Set<String> WHITE_LIST = PathGuard.whiteList(
            "/auth/login",
            "/auth/refresh",
            "/auth/captcha",
            "/auth/register",
            "/health",
            // P0-2: 三方审批回调 webhook（钉钉/飞书/企微），通过签名验证保证安全
            "/workflow/third-party/dingtalk/callback",
            "/workflow/third-party/feishu/callback",
            "/workflow/third-party/wecom/callback"
    );

    /** P1-7: JWT 校验结果缓存（Caffeine TTL=5s） */
    private final CachedJwtValidator cachedJwtValidator;
    /** Redis 响应式模板（用于 Token 黑名单检查） */
    private final ReactiveStringRedisTemplate redisTemplate;
    /** P2-12: 安全响应头配置 */
    private final SecurityHeadersProperties securityHeadersProperties;

    /**
     * 内部头签名密钥（复用 JWT 密钥，避免新增配置）。
     *
     * <p>P0-C4 已强制校验：生产环境必须为强随机密钥，弱密钥拒绝启动。
     */
    @Value("${pmis.jwt.secret:}")
    private String internalSignSecret;

    /**
     * 核心过滤逻辑：路径规范化 → 链路追踪 → 白名单放行 → Token 校验
     * → 黑名单检查 → 剥离伪造头 → 注入签名头 → 用户信息透传
     *
     * @param exchange 服务器 Web 交换上下文
     * @param chain    网关过滤器链
     * @return 完成信号 Mono
     */
    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        ServerHttpRequest request = exchange.getRequest();
        String rawPath = request.getURI().getPath();

        // P0-C5: 路径规范化，拦截 .. / // / %2e%2e 等穿越攻击
        String path = PathGuard.sanitize(rawPath);
        if (path == null) {
            log.warn("[AuthFilter] 拒绝路径穿越攻击 rawPath={}", rawPath);
            return rejectPathTraversal(exchange);
        }

        // P2-12: WebSocket 请求已由 WebSocketAuthFilter 认证，跳过
        if (Boolean.TRUE.equals(exchange.getAttribute(WebSocketAuthFilter.ATTR_WS_AUTHENTICATED))) {
            return chain.filter(exchange);
        }

        // 链路追踪 ID（网关层强制重新生成，剥离客户端伪造的 X-Trace-Id）
        final String traceId = TraceIdGenerator.generate();

        // 统一写入 traceId 到响应头，确保所有响应（成功/失败/OPTIONS/白名单）都携带链路追踪 ID
        exchange.getResponse().getHeaders().add(GatewayConstants.HEADER_TRACE_ID, traceId);

        // 跨域预检直接放行（先剥离内部头再透传）
        if ("OPTIONS".equalsIgnoreCase(request.getMethod().name())) {
            return withSecurityHeaders(exchange, chain.filter(exchange.mutate()
                    .request(r -> {
                        stripInternalHeaders(r);
                        r.header(GatewayConstants.HEADER_TRACE_ID, traceId);
                        String acceptLang = request.getHeaders().getFirst("Accept-Language");
                        if (acceptLang != null && !acceptLang.isEmpty()) {
                            r.header("Accept-Language", acceptLang);
                        }
                    })
                    .build()));
        }

        // 白名单直接放行（先剥离内部头，防止白名单请求伪造身份）
        if (PathGuard.matchWhiteList(path, WHITE_LIST)) {
            return withSecurityHeaders(exchange, chain.filter(exchange.mutate()
                    .request(r -> {
                        stripInternalHeaders(r);
                        r.header(GatewayConstants.HEADER_TRACE_ID, traceId);
                        String acceptLang = request.getHeaders().getFirst("Accept-Language");
                        if (acceptLang != null && !acceptLang.isEmpty()) {
                            r.header("Accept-Language", acceptLang);
                        }
                    })
                    .build()));
        }

        // 提取 Token
        String authHeader = request.getHeaders().getFirst("Authorization");
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            return unauthorized(exchange, traceId, "error.UNAUTHORIZED");
        }
        String jwt = authHeader.substring(7);

        // 验证 Token + 解析 UserInfo（P1-7: 使用 Caffeine 缓存）
        UserInfo userInfo = cachedJwtValidator.validateAndParse(jwt);
        if (userInfo == null) {
            return unauthorized(exchange, traceId, "error.TOKEN_INVALID");
        }

        // 黑名单检查
        return redisTemplate.hasKey(TOKEN_BLACKLIST_PREFIX + jwt)
                .flatMap(blacklisted -> {
                    if (Boolean.TRUE.equals(blacklisted)) {
                        // P2-12: 黑名单命中时立即清除缓存
                        cachedJwtValidator.invalidate(jwt);
                        return unauthorized(exchange, traceId, "error.TOKEN_EXPIRED");
                    }

                    String userIdStr = userInfo.getUserId() != null ? userInfo.getUserId() : "";
                    String usernameStr = userInfo.getUsername() != null ? userInfo.getUsername() : "";
                    String rolesStr = userInfo.getRoleCode() != null ? userInfo.getRoleCode() : "";
                    String permsStr = "";

                    // P0-C5: 生成内部头签名（防伪造 + 防重放）
                    long tsSeconds = System.currentTimeMillis() / 1000L;
                    String sig = InternalHeaderSigner.sign(internalSignSecret, traceId,
                            userIdStr, usernameStr, rolesStr, permsStr, tsSeconds);

                    // 透传用户信息（先剥离客户端伪造的内部头，再注入网关值）
                    final String acceptLang = request.getHeaders().getFirst("Accept-Language");
                    ServerHttpRequest mutated = request.mutate()
                            .headers(h -> {
                                // 剥离所有客户端伪造的内部头
                                stripInternalHeaders(h);
                                // 注入网关签发的内部头
                                h.set(GatewayConstants.HEADER_TRACE_ID, traceId);
                                h.set(GatewayConstants.HEADER_USER_ID, userIdStr);
                                h.set(GatewayConstants.HEADER_USERNAME, usernameStr);
                                h.set(GatewayConstants.HEADER_USER_ROLES, rolesStr);
                                h.set(GatewayConstants.HEADER_USER_PERMISSIONS, permsStr);
                                h.set(GatewayConstants.HEADER_INTERNAL_SIG, sig);
                                h.set(GatewayConstants.HEADER_INTERNAL_TS, String.valueOf(tsSeconds));
                                h.set("Authorization", authHeader);
                                h.set("Accept-Language",
                                        acceptLang != null && !acceptLang.isEmpty() ? acceptLang : "zh-CN");
                            })
                            .build();

                    return withSecurityHeaders(exchange, chain.filter(exchange.mutate().request(mutated).build()));
                });
    }

    /**
     * 剥离客户端可能伪造的内部头（Consumer 风格，用于 headers(h -> ...) ）。
     *
     * @param headers HttpHeaders builder
     */
    private void stripInternalHeaders(HttpHeaders headers) {
        for (String name : PathGuard.internalHeaders()) {
            headers.remove(name);
        }
    }

    /**
     * 剥离客户端可能伪造的内部头（Builder 风格，用于 request.mutate().request(r -> ...)）。
     *
     * @param r ServerHttpRequest.Builder
     */
    private void stripInternalHeaders(ServerHttpRequest.Builder r) {
        for (String name : PathGuard.internalHeaders()) {
            r.headers(h -> h.remove(name));
        }
    }

    /**
     * 返回 400 拒绝路径穿越响应
     *
     * @param exchange 服务器 Web 交换上下文
     * @return 完成信号 Mono
     */
    private Mono<Void> rejectPathTraversal(ServerWebExchange exchange) {
        ServerHttpResponse response = exchange.getResponse();
        response.setStatusCode(HttpStatus.BAD_REQUEST);
        response.getHeaders().setContentType(MediaType.APPLICATION_JSON);
        BaseResponse<Void> body = BaseResponse.failed("400", "error.BAD_REQUEST");
        byte[] bytes = YdszJson.toJson(body).getBytes(StandardCharsets.UTF_8);
        DataBuffer buffer = response.bufferFactory().wrap(bytes);
        return response.writeWith(Mono.just(buffer));
    }

    /**
     * 返回 401 未授权响应
     *
     * @param exchange 服务器 Web 交换上下文
     * @param traceId  链路追踪 ID
     * @param msg      错误消息
     * @return 完成信号 Mono
     */
    private Mono<Void> unauthorized(ServerWebExchange exchange, String traceId, String msg) {
        ServerHttpResponse response = exchange.getResponse();
        response.setStatusCode(HttpStatus.UNAUTHORIZED);
        response.getHeaders().setContentType(MediaType.APPLICATION_JSON);
        // traceId 已在 filter 开头统一写入响应头，此处无需重复设置

        BaseResponse<Void> body = BaseResponse.failed("20001", msg);
        body.setTraceId(traceId);
        byte[] bytes = YdszJson.toJson(body).getBytes(StandardCharsets.UTF_8);

        DataBuffer buffer = response.bufferFactory().wrap(bytes);
        return response.writeWith(Mono.just(buffer));
    }

    /**
     * 在响应头中注入 CSRF / 浏览器安全响应头
     *
     * <h3>P2-12 增强项</h3>
     * <p>通过 {@link SecurityHeadersProperties} 可配置化，新增 COOP/COEP/CORP 头。
     *
     * <h3>注入头清单</h3>
     * <ul>
     *   <li>X-Content-Type-Options: nosniff — 阻止 MIME 嗅探</li>
     *   <li>X-Frame-Options: DENY — 阻止点击劫持(Clickjacking)</li>
     *   <li>X-XSS-Protection: 1; mode=block — 启用浏览器 XSS 过滤器</li>
     *   <li>Referrer-Policy: strict-origin-when-cross-origin — 限制 Referrer 泄漏</li>
     *   <li>X-CSRF-Protection: 1 — 声明已启用 CSRF 防护</li>
     *   <li>Content-Security-Policy — 限制脚本/样式/图片/连接来源,防 XSS 注入</li>
     *   <li>Permissions-Policy — 限制浏览器 API 权限(摄像头/麦克风/地理位置等)</li>
     *   <li>P2-12: Cross-Origin-Opener-Policy (COOP) — 防止窗口名攻击</li>
     *   <li>P2-12: Cross-Origin-Embedder-Policy (COEP) — 隔离跨域资源</li>
     *   <li>P2-12: Cross-Origin-Resource-Policy (CORP) — 限制资源跨域访问</li>
     * </ul>
     *
     * <p>通过 chain.filter().then() 在下游链完成后注入,确保所有成功响应均携带安全头。
     *
     * @param exchange 服务器 Web 交换上下文
     * @param result   下游过滤器链执行结果
     * @return 注入安全头后的完成信号 Mono
     */
    private Mono<Void> withSecurityHeaders(ServerWebExchange exchange, Mono<Void> result) {
        return result.then(Mono.fromRunnable(() -> {
            ServerHttpResponse response = exchange.getResponse();

            // 全局开关
            if (!securityHeadersProperties.isEnabled()) {
                return;
            }

            // 基础安全头
            response.getHeaders().add("X-Content-Type-Options", "nosniff");
            response.getHeaders().add("X-Frame-Options", "DENY");
            response.getHeaders().add("X-XSS-Protection", "1; mode=block");
            response.getHeaders().add("Referrer-Policy", "strict-origin-when-cross-origin");
            response.getHeaders().add("X-CSRF-Protection", "1");

            // CSP 策略: 限制脚本/样式/图片/连接来源
            if (securityHeadersProperties.getCsp().isEnabled()) {
                // P3-13: 移除 'unsafe-eval'（生产环境不需要，Vue 模板预编译）
                //         移除 script-src 的 'unsafe-inline'（防 XSS 注入）
                //         保留 style-src 的 'unsafe-inline'（Element Plus 运行时样式注入需要）
                // - script-src: self（仅允许同源脚本）
                // - style-src: self + unsafe-inline(Element Plus 样式注入)
                // - img-src: self + data:(base64) + blob:(URL) + https:(CDN 图片)
                // - connect-src: self + ws/wss(WebSocket) + https(API/Sentry)
                // - font-src: self + data:(字体 base64)
                // - frame-ancestors: none(防点击劫持)
                // - base-uri: self(防 base 标签注入)
                // - form-action: self(防表单提交到外部)
                boolean unsafeEval = securityHeadersProperties.getCsp().isUnsafeEval();
                String scriptSrc = unsafeEval
                        ? "script-src 'self' 'unsafe-inline' 'unsafe-eval'; "
                        : "script-src 'self'; ";
                response.getHeaders().add("Content-Security-Policy",
                    "default-src 'self'; "
                    + scriptSrc
                    + "style-src 'self' 'unsafe-inline'; "
                    + "img-src 'self' data: blob: https:; "
                    + "font-src 'self' data:; "
                    + "connect-src 'self' ws: wss: https:; "
                    + "frame-ancestors 'none'; "
                    + "base-uri 'self'; "
                    + "form-action 'self'");
            }

            // Permissions-Policy: 禁用不需要的浏览器 API
            response.getHeaders().add("Permissions-Policy",
                "camera=(), microphone=(), geolocation=(), payment=(), usb=(), magnetometer=(), gyroscope=()");

            // P2-12: COOP (Cross-Origin-Opener-Policy) — 防止窗口名攻击
            if (securityHeadersProperties.getCoop().isEnabled()) {
                response.getHeaders().add("Cross-Origin-Opener-Policy",
                    securityHeadersProperties.getCoop().getPolicy());
            }

            // P2-12: COEP (Cross-Origin-Embedder-Policy) — 隔离跨域资源
            if (securityHeadersProperties.getCoep().isEnabled()) {
                response.getHeaders().add("Cross-Origin-Embedder-Policy",
                    securityHeadersProperties.getCoep().getPolicy());
            }

            // P2-12: CORP (Cross-Origin-Resource-Policy) — 限制资源跨域访问
            if (securityHeadersProperties.getCorp().isEnabled()) {
                response.getHeaders().add("Cross-Origin-Resource-Policy",
                    securityHeadersProperties.getCorp().getPolicy());
            }

            // HSTS (Strict-Transport-Security) — 强制 HTTPS
            if (securityHeadersProperties.getHsts().isEnabled()) {
                StringBuilder hstsValue = new StringBuilder()
                    .append("max-age=").append(securityHeadersProperties.getHsts().getMaxAge());
                if (securityHeadersProperties.getHsts().isIncludeSubdomains()) {
                    hstsValue.append("; includeSubDomains");
                }
                if (securityHeadersProperties.getHsts().isPreload()) {
                    hstsValue.append("; preload");
                }
                response.getHeaders().add("Strict-Transport-Security", hstsValue.toString());
            }
        }));
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
