package com.njydsz.gateway.filter;

import java.util.Map;
import java.util.Set;

import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import com.njydsz.common.auth.service.ReactiveTokenBlacklistService;
import com.njydsz.common.core.trace.TraceIdGenerator;
import com.njydsz.common.safe.config.SecurityHeaderConfigurer;
import com.njydsz.common.safe.config.SecurityHeaderProperties;
import com.njydsz.common.sentry.SentryObservation;
import com.njydsz.common.sentry.domain.AlertEvent;
import com.njydsz.common.sentry.domain.AlertSeverity;
import com.njydsz.gateway.config.CachedJwtValidator;
import com.njydsz.gateway.config.GatewayConstants;
import com.njydsz.gateway.config.GatewayErrorCode;
import com.njydsz.gateway.config.GatewayErrorWriter;
import com.njydsz.gateway.config.GatewayFilterOrder;
import com.njydsz.common.auth.security.InternalHeaderSigner;
import com.njydsz.gateway.config.PathGuard;

/**
 * 认证全局过滤器。
 *
 * <p>核心职责:
 *
 * <ol>
 *   <li>路径规范化：拦截 {@code ..}、{@code //} 等路径穿越攻击
 *   <li>剥离客户端伪造的内部头：所有 {@code X-User-*} / {@code X-Internal-*} 头在透传前必须先删除客户端传入的值
 *   <li>提取 Authorization 头中的 JWT 并校验
 *   <li>检查 Token 黑名单（Redis + Bloom Filter）
 *   <li>将 userId/username/roles/permissions 写入 X-User-* 头透传给下游
 *   <li>注入 {@code X-Internal-Sig} 签名头，下游可校验（防伪造）
 * </ol>
 *
 * <h3>JWT 密钥与内部签名密钥分离</h3>
 *
 * <p>{@code internalSignSecret} 使用独立配置项 {@code ydsz.gateway.internal-sign-secret}，与 JWT 密钥隔离。
 *
 * @since 1.0.0
 * @author ydsz-team
 */
@Slf4j
@Component
@lombok.RequiredArgsConstructor
@ConditionalOnProperty(
    prefix = "ydsz.gateway.filter",
    name = "auth",
    havingValue = "true",
    matchIfMissing = true)
public class AuthGlobalFilter implements GlobalFilter, Ordered {

  /** Token 黑名单服务 */
  private final ReactiveTokenBlacklistService tokenBlacklistService;

  /** 白名单(不校验 Token) */
  private static final Set<String> WHITE_LIST =
      PathGuard.whiteList(
          "/auth/login",
          "/auth/refresh",
          "/auth/captcha",
          "/auth/register",
          "/health",
          "/actuator/health",
          "/actuator/health/liveness",
          "/actuator/health/readiness",
          "/actuator/info",
          "/workflow/third-party/dingtalk/callback",
          "/workflow/third-party/feishu/callback",
          "/workflow/third-party/wecom/callback");

  /** 内部头签名密钥最小长度 */
  private static final int MIN_INTERNAL_SECRET_LENGTH = 32;

  /** JWT 校验结果缓存 */
  private final CachedJwtValidator cachedJwtValidator;

  /** 安全响应头配置（common-safe 统一配置） */
  private final SecurityHeaderProperties securityHeaderProperties;

  /** 内部头签名密钥（独立配置，禁止复用 JWT 密钥） */
  @Value("${ydsz.gateway.internal-sign-secret:}")
  private String internalSignSecret;

  /**
   * 启动时校验内部头签名密钥强度。
   *
   * <p>HMAC-SHA256 安全要求密钥长度 ≥ 32 字节（256 bit）。
   */
  @PostConstruct
  private void validateSecret() {
    if (internalSignSecret == null || internalSignSecret.isEmpty()) {
      log.error("[AuthFilter] 内部头签名密钥未配置 (ydsz.gateway.internal-sign-secret)，"
          + "下游服务将无法验证内部头签名，存在身份伪造风险");
      return;
    }
    if (internalSignSecret.length() < MIN_INTERNAL_SECRET_LENGTH) {
      log.error("[AuthFilter] 内部头签名密钥长度不足 (current={}, min={})",
          internalSignSecret.length(), MIN_INTERNAL_SECRET_LENGTH);
    }
    String source = System.getenv("YDSZ_GATEWAY_INTERNAL_SIGN_SECRET") != null
        ? "YDSZ_GATEWAY_INTERNAL_SIGN_SECRET"
        : (System.getenv("JWT_SECRET") != null ? "JWT_SECRET(fallback)" : "nacos-config");
    log.info("[AuthFilter] 内部头签名密钥已加载, source={}, length={}", source, internalSignSecret.length());
  }

  /**
   * 核心过滤逻辑：路径规范化 → 链路追踪 → 白名单放行 → Token 校验 → 黑名单检查 → 剥离伪造头 → 注入签名头 → 用户信息透传。
   *
   * @param exchange 服务器 Web 交换上下文
   * @param chain 网关过滤器链
   * @return 完成信号 Mono
   */
  @Override
  public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
    ServerHttpRequest request = exchange.getRequest();
    String rawPath = request.getURI().getPath();

    // 路径规范化，拦截 .. / // / %2e%2e 等穿越攻击
    String path = PathGuard.sanitize(rawPath);
    if (path == null) {
      String existingTraceId = request.getHeaders().getFirst(GatewayConstants.HEADER_TRACE_ID);
      SentryObservation.alert(AlertEvent.builder()
          .name("gateway.auth.path_traversal")
          .severity(AlertSeverity.P1)
          .summary("拒绝路径穿越攻击")
          .description("客户端尝试路径穿越攻击，已被网关拦截")
          .category("security")
          .labels(Map.of("raw_path", rawPath, "trace_id", existingTraceId != null ? existingTraceId : "n/a"))
          .build());
      log.warn("[AuthFilter] 拒绝路径穿越攻击 rawPath={}", rawPath);
      return rejectPathTraversal(exchange);
    }

    // WebSocket 请求已由 WebSocketAuthFilter 认证，跳过
    if (Boolean.TRUE.equals(exchange.getAttribute(WebSocketAuthFilter.ATTR_WS_AUTHENTICATED))) {
      return chain.filter(exchange);
    }

    // 链路追踪 ID — 优先复用 W3CTraceContextFilter（order=0）已注入的 traceId
    String existingTraceId = request.getHeaders().getFirst(GatewayConstants.HEADER_TRACE_ID);
    final String traceId = (existingTraceId != null && !existingTraceId.isBlank())
        ? existingTraceId
        : TraceIdGenerator.generateSortableTraceId();

    // 统一写入 traceId 到响应头
    exchange.getResponse().getHeaders().add(GatewayConstants.HEADER_TRACE_ID, traceId);

    // 跨域预检直接放行
    if ("OPTIONS".equalsIgnoreCase(request.getMethod().name())) {
      return withSecurityHeaders(exchange, chain.filter(exchange.mutate().request(r -> {
        stripInternalHeaders(r);
        r.header(GatewayConstants.HEADER_TRACE_ID, traceId);
        String acceptLang = request.getHeaders().getFirst("Accept-Language");
        if (acceptLang != null && !acceptLang.isEmpty()) {
          r.header("Accept-Language", acceptLang);
        }
      }).build()));
    }

    // 白名单直接放行
    if (PathGuard.matchWhiteList(path, WHITE_LIST)) {
      return withSecurityHeaders(exchange, chain.filter(exchange.mutate().request(r -> {
        stripInternalHeaders(r);
        r.header(GatewayConstants.HEADER_TRACE_ID, traceId);
        String acceptLang = request.getHeaders().getFirst("Accept-Language");
        if (acceptLang != null && !acceptLang.isEmpty()) {
          r.header("Accept-Language", acceptLang);
        }
      }).build()));
    }

    // 提取 Token
    String authHeader = request.getHeaders().getFirst("Authorization");
    if (authHeader == null || !authHeader.startsWith("Bearer ")) {
      return unauthorized(exchange, traceId, GatewayErrorCode.UNAUTHORIZED, "error.UNAUTHORIZED");
    }
    String jwt = authHeader.substring(7);

    // 验证 Token + 解析 UserInfo（P0-C1：验签发布到 boundedElastic，避免阻塞 Netty EventLoop）
    return cachedJwtValidator
        .validateAndParseReactive(jwt)
        .flatMap(userInfo -> {
          if (userInfo == null) {
            return unauthorized(exchange, traceId, GatewayErrorCode.TOKEN_INVALID, "error.TOKEN_INVALID");
          }
          // 黑名单检查委托给 ReactiveTokenBlacklistService
          return tokenBlacklistService.isBlacklisted(jwt).flatMap(blacklisted -> {
            if (Boolean.TRUE.equals(blacklisted)) {
              cachedJwtValidator.invalidate(jwt);
              return unauthorized(exchange, traceId, GatewayErrorCode.TOKEN_EXPIRED, "error.TOKEN_EXPIRED");
            }

            String userIdStr = userInfo.getUserId() != null ? userInfo.getUserId() : "";
            String usernameStr = userInfo.getUsername() != null ? userInfo.getUsername() : "";
            String rolesStr = userInfo.getRoleCode() != null ? userInfo.getRoleCode() : "";
            String tenantIdStr = userInfo.getTenantId() != null ? userInfo.getTenantId() : "";
            String permsStr = "";

            // 生成内部头签名（防伪造）
            String sig =
                InternalHeaderSigner.sign(
                    internalSignSecret, traceId, userIdStr, usernameStr, rolesStr, permsStr);

            // 透传用户信息（先剥离客户端伪造的内部头，再注入网关值）
            final String acceptLang = request.getHeaders().getFirst("Accept-Language");
            ServerHttpRequest mutated = request.mutate().headers(h -> {
              stripInternalHeaders(h);
              h.set(GatewayConstants.HEADER_TRACE_ID, traceId);
              h.set(GatewayConstants.HEADER_TENANT_ID, tenantIdStr);
              h.set(GatewayConstants.HEADER_USER_ID, userIdStr);
              h.set(GatewayConstants.HEADER_USERNAME, usernameStr);
              h.set(GatewayConstants.HEADER_USER_ROLES, rolesStr);
              h.set(GatewayConstants.HEADER_USER_PERMISSIONS, permsStr);
              h.set(GatewayConstants.HEADER_INTERNAL_SIG, sig);
              h.set("Authorization", authHeader);
              h.set("Accept-Language", acceptLang != null && !acceptLang.isEmpty() ? acceptLang : "zh-CN");
            }).build();

            return withSecurityHeaders(exchange, chain.filter(exchange.mutate().request(mutated).build()));
          });
        });
  }

  /**
   * 剥离客户端可能伪造的内部头（Consumer 风格）。
   *
   * @param headers HttpHeaders builder
   */
  private void stripInternalHeaders(HttpHeaders headers) {
    for (String name : PathGuard.internalHeaders()) {
      headers.remove(name);
    }
  }

  /**
   * 剥离客户端可能伪造的内部头（Builder 风格）。
   *
   * @param r ServerHttpRequest.Builder
   */
  private void stripInternalHeaders(ServerHttpRequest.Builder r) {
    for (String name : PathGuard.internalHeaders()) {
      r.headers(h -> h.remove(name));
    }
  }

  /**
   * 返回 400 拒绝路径穿越响应（P0-D1：统一错误响应写出器）。
   *
   * @param exchange 服务器 Web 交换上下文
   * @return 完成信号 Mono
   */
  private Mono<Void> rejectPathTraversal(ServerWebExchange exchange) {
    String traceId = exchange.getRequest().getHeaders().getFirst(GatewayConstants.HEADER_TRACE_ID);
    return GatewayErrorWriter.write(
        exchange,
        HttpStatus.BAD_REQUEST,
        GatewayErrorCode.PATH_TRAVERSAL,
        GatewayErrorCode.PATH_TRAVERSAL.getMessageKey(),
        traceId);
  }

  /**
   * 返回 401 未授权响应（P0-D1：统一错误响应写出器）。
   *
   * @param exchange 服务器 Web 交换上下文
   * @param traceId 链路追踪 ID
   * @param errorCode 认证错误码
   * @param msg 错误消息（i18n key）
   * @return 完成信号 Mono
   */
  private Mono<Void> unauthorized(
      ServerWebExchange exchange, String traceId, GatewayErrorCode errorCode, String msg) {
    return GatewayErrorWriter.write(exchange, HttpStatus.UNAUTHORIZED, errorCode, msg, traceId);
  }

  /**
   * 注入安全响应头。
   *
   * <p>委托给 common-safe {@link SecurityHeaderConfigurer}，消除 Gateway 与 common-safe 的重复实现。
   *
   * @param exchange 服务器 Web 交换上下文
   * @param result 下游过滤器链执行结果
   * @return 注入安全头后的完成信号 Mono
   */
  private Mono<Void> withSecurityHeaders(ServerWebExchange exchange, Mono<Void> result) {
    return result.then(Mono.fromRunnable(() -> {
      if (!securityHeaderProperties.isEnabled()) {
        return;
      }
      SecurityHeaderConfigurer.applyWebFluxHeaders(exchange.getResponse(), securityHeaderProperties);
    }));
  }

  @Override
  public int getOrder() {
    return GatewayFilterOrder.AUTH.getOrder();
  }
}
