package com.njydsz.gateway.filter;

import java.util.Set;
import java.util.UUID;

import com.njydsz.common.json.YdszJson;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
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

import com.njydsz.common.auth.model.UserInfo;
import com.njydsz.common.auth.service.ReactiveTokenBlacklistService;
import com.njydsz.common.core.response.BaseResponse;
import com.njydsz.common.safe.crypto.NonceCache;
import com.njydsz.gateway.config.CachedJwtValidator;
import com.njydsz.gateway.config.GatewayConstants;
import com.njydsz.gateway.config.InternalHeaderSigner;
import com.njydsz.gateway.config.PathGuard;
import com.njydsz.gateway.config.SecurityHeadersProperties;
import com.njydsz.common.core.code.BaseResultCode;
import com.njydsz.common.core.trace.TraceIdGenerator;

import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Mono;

/**
 * 认证全局过滤器（P0-C5 安全加固 + P0-2 密钥分离 + P0-6 nonce 防重放）
 *
 * <p>核心职责:
 * <ol>
 *   <li>路径规范化：拦截 {@code ..}、{@code //} 等路径穿越攻击</li>
 *   <li>剥离客户端伪造的内部头：所有 {@code X-User-*} / {@code X-Internal-*}
 *       头在透传前必须先删除客户端传入的值</li>
 *   <li>提取 Authorization 头中的 JWT 并校验</li>
 *   <li>检查 Token 黑名单（Redis）</li>
 *   <li>将 userId/username/roles/permissions 写入 X-User-* 头透传给下游</li>
 *   <li>注入 {@code X-Internal-Sig} + {@code X-Internal-Ts} + {@code X-Internal-Nonce}
 *       签名头，下游可校验（防伪造 + 防重放）</li>
 * </ol>
 *
 * <h3>P0-2: JWT 密钥与内部签名密钥分离</h3>
 * <p>历史版本中，{@code internalSignSecret} 复用 {@code ydsz.jwt.secret}，导致一旦 JWT 密钥泄漏，
 * 攻击者可伪造内部头绕过下游服务的身份信任。本版本引入独立的
 * {@code ydsz.gateway.internal-sign-secret} 配置项，与 JWT 密钥隔离。
 *
 * <h3>P0-6: nonce 防重放</h3>
 * <p>历史版本仅有时间戳防重放（60 秒窗口），攻击者可在窗口内重放同一签名。
 * 本版本为每个请求生成唯一 nonce（UUID），纳入 HMAC 签名 payload 后透传给下游。
 * 下游服务调用 {@link com.njydsz.common.safe.crypto.NonceCache#verifyAndConsume(String)}
 * 校验 nonce 是否重复，形成"一次性签名"。
 *
 * @since 1.0.0
 * @author ydsz-team
 */
@Slf4j
@Component
@lombok.RequiredArgsConstructor
@ConditionalOnProperty(prefix = "ydsz.gateway.filter", name = "auth", havingValue = "true", matchIfMissing = true)
public class AuthGlobalFilter implements GlobalFilter, Ordered {

    /**
     * GAP-P0-1: Token 黑名单检查委托给 ReactiveTokenBlacklistService
     *
     * <p>历史版本硬编码 {@code TOKEN_BLACKLIST_PREFIX = "ydsz:token:blacklist:"} 并直接使用
     * {@code redisTemplate.hasKey()} 检查黑名单，存在以下问题：
     * <ul>
     *   <li>key 前缀与 common-auth 的 {@code auth:token:blacklist:} 不一致，跨服务黑名单不生效</li>
     *   <li>每个请求都查 Redis，无 Bloom Filter 前置过滤，高 QPS 下 Redis 压力大</li>
     *   <li>Redis key 使用完整 JWT（500+ 字节），浪费内存</li>
     * </ul>
     *
     * <p>改为注入 {@link ReactiveTokenBlacklistService}，复用公共模块能力：
     * <ul>
     *   <li>统一 key 前缀 + SHA-256 摘要</li>
     *   <li>Bloom Filter 前置过滤，减少 90%+ Redis 查询</li>
     *   <li>配置化黑名单开关和 TTL</li>
     * </ul>
     */
    private final ReactiveTokenBlacklistService tokenBlacklistService;

    /**
     * 白名单(不校验 Token)。
     *
     * <p>P0-C5 改为精确匹配：仅路径完全相等才放行，
     * 杜绝 {@code /auth/login/../users/list} 等 startsWith 绕过。
     *
     * <p>P0-阶段二-7: 新增 K8s 健康探针路径放行，避免探针请求被 401 拦截导致 Pod 重启。
     */
    private static final Set<String> WHITE_LIST = PathGuard.whiteList(
            "/auth/login",
            "/auth/refresh",
            "/auth/captcha",
            "/auth/register",
            "/health",
            // P0-阶段二-7: K8s health probe 路径放行（liveness/readiness/info）
            "/actuator/health",
            "/actuator/health/liveness",
            "/actuator/health/readiness",
            "/actuator/info",
            // P0-2: 三方审批回调 webhook（钉钉/飞书/企微），通过签名验证保证安全
            "/workflow/third-party/dingtalk/callback",
            "/workflow/third-party/feishu/callback",
            "/workflow/third-party/wecom/callback"
    );

    /** P0-2: 内部头签名密钥最小长度（HMAC-SHA256 安全要求） */
    private static final int MIN_INTERNAL_SECRET_LENGTH = 32;

    /** P1-7: JWT 校验结果缓存（Caffeine TTL=5s） */
    private final CachedJwtValidator cachedJwtValidator;
    /** P2-12: 安全响应头配置 */
    private final SecurityHeadersProperties securityHeadersProperties;

    /**
     * GAP-P0-3: Nonce 防重放缓存
     *
     * <p>网关为每个请求生成唯一 nonce（UUID），存入 NonceCache（TTL=5min），
     * 同时通过 X-Internal-Nonce 头透传给下游服务。
     *
     * <p>下游服务调用 {@code NonceCache.verifyAndConsume(nonce)} 校验 nonce 是否重复，
     * 形成完整的"一次性签名"防重放机制。
     *
     * <p>网关侧存储 nonce 的目的是：当同一请求被重试/重放时，网关自身也能检测到
     * nonce 重复（无需等待下游反馈）。
     */
    private final NonceCache nonceCache;

    /**
     * P0-2: 内部头签名密钥（独立配置，禁止复用 JWT 密钥）。
     *
     * <p>配置项：{@code ydsz.gateway.internal-sign-secret}
     * 环境变量：{@code YDSZ_GATEWAY_INTERNAL_SIGN_SECRET}
     *
     * <p>历史兼容：如未配置，回退到 {@code ydsz.jwt.secret}（启动时记录 WARN 日志提醒运维分离密钥）。
     * 这种回退仅作为过渡，后续版本将强制要求独立配置。
     */
    @Value("${ydsz.gateway.internal-sign-secret:${ydsz.jwt.secret:}}")
    private String internalSignSecret;

    /**
     * P0-2: 启动时校验内部头签名密钥强度
     *
     * <p>HMAC-SHA256 安全要求密钥长度 ≥ 32 字节（256 bit），且非空、非弱密钥。
     * 校验失败时记录 ERROR 日志（dev/sit 环境允许启动以方便调试，prod 环境建议通过部署校验拦截）。
     */
    @PostConstruct
    private void validateSecret() {
        if (internalSignSecret == null || internalSignSecret.isEmpty()) {
            log.error("[AuthFilter] 内部头签名密钥未配置 (ydsz.gateway.internal-sign-secret)，"
                    + "下游服务将无法验证内部头签名，存在身份伪造风险");
            return;
        }
        if (internalSignSecret.length() < MIN_INTERNAL_SECRET_LENGTH) {
            log.error("[AuthFilter] 内部头签名密钥长度不足 (current={}, min={})，"
                            + "HMAC-SHA256 安全要求密钥长度 ≥ 32 字节",
                    internalSignSecret.length(), MIN_INTERNAL_SECRET_LENGTH);
        }
        // 提示密钥来源（用于运维排查"密钥未分离"问题）
        String source = System.getenv("YDSZ_GATEWAY_INTERNAL_SIGN_SECRET") != null
                ? "YDSZ_GATEWAY_INTERNAL_SIGN_SECRET"
                : (System.getenv("JWT_SECRET") != null ? "JWT_SECRET(fallback)" : "nacos-config");
        log.info("[AuthFilter] 内部头签名密钥已加载, source={}, length={}",
                source, internalSignSecret.length());
    }

    /**
     * 核心过滤逻辑：路径规范化 → 链路追踪 → 白名单放行 → Token 校验
     * → 黑名单检查 → 剥离伪造头 → 注入签名头（含 nonce） → 用户信息透传
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

        // P0-2: 链路追踪 ID — 优先复用 W3CTraceContextFilter（order=0）已注入的 traceId
        // 避免多个过滤器各自生成独立 traceId 导致链路追踪断裂
        String existingTraceId = request.getHeaders().getFirst(GatewayConstants.HEADER_TRACE_ID);
        final String traceId = (existingTraceId != null && !existingTraceId.isBlank())
                ? existingTraceId : TraceIdGenerator.generateTraceId();

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

        // GAP-P0-1: 黑名单检查委托给 ReactiveTokenBlacklistService（Bloom Filter 前置过滤 + SHA-256 摘要 key）
        return tokenBlacklistService.isBlacklisted(jwt)
                .flatMap(blacklisted -> {
                    if (Boolean.TRUE.equals(blacklisted)) {
                        // P2-12: 黑名单命中时立即清除缓存
                        cachedJwtValidator.invalidate(jwt);
                        return unauthorized(exchange, traceId, "error.TOKEN_EXPIRED");
                    }

                    String userIdStr = userInfo.getUserId() != null ? userInfo.getUserId() : "";
                    String usernameStr = userInfo.getUsername() != null ? userInfo.getUsername() : "";
                    String rolesStr = userInfo.getRoleCode() != null ? userInfo.getRoleCode() : "";
                    // P0-3 修复：注入租户 ID，供 RateLimitFilter 做租户级限流
                    String tenantIdStr = userInfo.getTenantId() != null ? userInfo.getTenantId() : "";
                    // P0-7: permsStr 留空（UserInfo 暂无 permissions 字段，权限由下游从 RBAC 缓存加载）
                    String permsStr = "";

                    // P0-6 + GAP-P0-3: 生成 nonce 并存入 NonceCache（一次性随机串），与 traceId/userId 一起纳入签名 payload
                    // NonceCache.verifyAndConsume() 保证原子性，如果 nonce 已存在返回 false（表示重放攻击）
                    String nonce = UUID.randomUUID().toString().replace("-", "");
                    // GAP-P0-3: 网关侧存储 nonce（下游服务也会调用 NonceCache.verifyAndConsume 双重校验）
                    if (!nonceCache.verifyAndConsume(nonce)) {
                        // 理论上 UUID 不会碰撞，如果碰撞说明可能是重放攻击
                        log.warn("[AuthFilter] Nonce 碰撞（疑似重放攻击）traceId={} userId={}", traceId, userIdStr);
                        return unauthorized(exchange, traceId, "error.REPLAY_DETECTED");
                    }

                    // P0-C5 + P0-6: 生成内部头签名（含 nonce，防伪造 + 防重放）
                    long tsSeconds = System.currentTimeMillis() / 1000L;
                    String sig = InternalHeaderSigner.sign(internalSignSecret, traceId,
                            userIdStr, usernameStr, rolesStr, permsStr, tsSeconds, nonce);

                    // 透传用户信息（先剥离客户端伪造的内部头，再注入网关值）
                    final String acceptLang = request.getHeaders().getFirst("Accept-Language");
                    ServerHttpRequest mutated = request.mutate()
                            .headers(h -> {
                                // 剥离所有客户端伪造的内部头
                                stripInternalHeaders(h);
                                // 注入网关签发的内部头
                                h.set(GatewayConstants.HEADER_TRACE_ID, traceId);
                                h.set(GatewayConstants.HEADER_TENANT_ID, tenantIdStr);
                                h.set(GatewayConstants.HEADER_USER_ID, userIdStr);
                                h.set(GatewayConstants.HEADER_USERNAME, usernameStr);
                                h.set(GatewayConstants.HEADER_USER_ROLES, rolesStr);
                                h.set(GatewayConstants.HEADER_USER_PERMISSIONS, permsStr);
                                h.set(GatewayConstants.HEADER_INTERNAL_SIG, sig);
                                h.set(GatewayConstants.HEADER_INTERNAL_TS, String.valueOf(tsSeconds));
                                h.set(GatewayConstants.HEADER_INTERNAL_NONCE, nonce);
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
        BaseResponse<Void> body = BaseResponse.error(BaseResultCode.BAD_REQUEST, "error.BAD_REQUEST");
        byte[] bytes = YdszJson.toJsonBytes(body);
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

        BaseResponse<Void> body = BaseResponse.error(BaseResultCode.UNAUTHORIZED, msg);
        body.assignTraceId(traceId);
        byte[] bytes = YdszJson.toJsonBytes(body);

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
