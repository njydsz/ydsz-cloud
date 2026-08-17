package com.njydsz.gateway.filter;

import java.util.Set;
import java.util.stream.Collectors;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import com.njydsz.common.auth.model.UserInfo;
import com.njydsz.common.core.trace.TraceIdGenerator;
import com.njydsz.gateway.config.CachedJwtValidator;
import com.njydsz.gateway.config.GatewayConstants;
import com.njydsz.gateway.config.GatewayErrorCode;
import com.njydsz.gateway.config.GatewayErrorWriter;
import com.njydsz.gateway.config.GatewayFilterOrder;
import com.njydsz.gateway.config.GatewayIpUtils;
import com.njydsz.gateway.config.InternalHeaderSigner;
import com.njydsz.gateway.config.PathGuard;
import com.njydsz.gateway.config.WebSocketConnectionLimiter;

/**
 * WebSocket 认证过滤器（P2-12 + P0-1 安全加固 + P0-4 Origin 校验）
 *
 * <p>WebSocket 握手时 Token 通常通过查询参数或 Sec-WebSocket-Protocol 传递， 而非标准的 Authorization 头。本过滤器为 WebSocket
 * 路径提供独立认证策略。
 *
 * <h3>Token 提取优先级</h3>
 *
 * <ol>
 *   <li>查询参数 {@code token}（最常用，前端 WebSocket 构造时拼接）
 *   <li>查询参数 {@code access_token}（OAuth2 风格）
 *   <li>Sec-WebSocket-Protocol 头（协议升级前最后一个 Token 项）
 *   <li>Authorization 头（标准方式，部分客户端支持）
 * </ol>
 *
 * <h3>认证流程</h3>
 *
 * <ol>
 *   <li>仅对 WebSocket 升级请求（Upgrade: websocket）生效
 *   <li>P0-4: Origin 校验（防 CSRF / 跨域 WebSocket 劫持）
 *   <li>提取 Token → 校验 → 注入 X-User-* 内部头
 *   <li>P0-1: 注入 X-Internal-Sig 签名头（与 AuthGlobalFilter 一致，下游可统一校验）
 *   <li>校验失败返回 401（在握手阶段拒绝，不建立连接）
 * </ol>
 *
 * <h3>P0-1 安全加固</h3>
 *
 * <p>历史版本仅注入用户信息头，未注入签名头，导致 WebSocket 请求可被下游伪造。 本版本复用 {@link InternalHeaderSigner} 生成 HMAC 签名，与
 * AuthGlobalFilter 共用密钥与算法。
 *
 * <p>WebSocket 不受 SOP / CORS 约束，攻击者可通过构造恶意页面发起 WebSocket 跨域连接 携带受害者 Cookie/Token。本版本校验 Origin
 * 是否在允许列表中，仅放行可信域。
 *
 * <h3>执行顺序</h3>
 *
 * <p>{@code HIGHEST_PRECEDENCE + 8}，在 {@link AuthGlobalFilter}(+10) 之前执行， WebSocket
 * 请求由本过滤器处理并标记为已认证，{@link AuthGlobalFilter} 检测到标记后跳过。
 *
 * @since 1.0.0
 * @author ydsz-team
 */
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(
    prefix = "ydsz.gateway.filter",
    name = "websocket-auth",
    havingValue = "true",
    matchIfMissing = true)
public class WebSocketAuthFilter implements GlobalFilter, Ordered {

  /** WebSocket 路径前缀 */
  private static final String WS_PATH_PREFIX = "/ws";

  /** WebSocket 升级请求头标识 */
  private static final String HEADER_UPGRADE = "Upgrade";

  private static final String UPGRADE_WEBSOCKET = "websocket";

  /** Origin 请求头 */
  private static final String HEADER_ORIGIN = "Origin";

  /** exchange attribute key: WebSocket 已认证标记 */
  public static final String ATTR_WS_AUTHENTICATED = "__ws_authenticated";

  /** JWT 缓存校验器 */
  private final CachedJwtValidator cachedJwtValidator;

  /** WebSocket 连接数限制器 */
  private final WebSocketConnectionLimiter connectionLimiter;

  /**
   * P3-7: 内部头签名密钥（与 {@link AuthGlobalFilter} 共用配置项）。
   *
   * <p>WebSocket 与 HTTP 请求的签名密钥必须一致，否则下游服务无法统一校验。 P3-7: 移除对 {@code ydsz.jwt.secret} 的回退依赖，必须独立配置。
   */
  @Value("${ydsz.gateway.internal-sign-secret:}")
  private String internalSignSecret;

  /**
   * P0-4: 允许的 WebSocket Origin 列表。
   *
   * <p>逗号分隔，支持通配符（如 {@code https://*.ydsz.example.com}）。 配置为空时表示不启用 Origin 校验（仅限 dev 环境，prod 必须配置）。
   */
  @Value("${ydsz.gateway.websocket.allowed-origins:}")
  private String allowedOriginsConfig;

  /**
   * WebSocket 独立认证过滤器：处理升级握手中的 Token 校验与内部头注入。
   *
   * <p>仅对 {@code /ws} 前缀且 {@code Upgrade: websocket} 的请求生效；先做 Origin 校验（P0-4 防跨域劫持）， 再从查询参数 /
   * Sec-WebSocket-Protocol / Authorization 提取 Token 并校验， 通过后注入 X-User-* 与签名头（X-Internal-Sig），标记已认证后放行。
   *
   * @param exchange 服务器 Web 交换上下文
   * @param chain 网关过滤器链
   * @return 放行或拒绝（401 / 403）的完成信号 Mono
   */
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

    // P0-4: Origin 校验（防 WebSocket 跨域劫持）
    if (!checkOrigin(request)) {
      log.warn("[WsAuth] WebSocket Origin 校验失败 path={}", path);
      return rejectWebSocket(exchange, HttpStatus.FORBIDDEN, GatewayErrorCode.ORIGIN_FORBIDDEN);
    }

    // 提取 Token
    String jwt = extractToken(request);
    if (jwt == null || jwt.isBlank()) {
      log.warn("[WsAuth] WebSocket 握手缺少 Token path={}", path);
      return rejectWebSocket(exchange, HttpStatus.UNAUTHORIZED, GatewayErrorCode.UNAUTHORIZED);
    }

    // 校验 Token（P0-C1：验签发布到 boundedElastic，避免阻塞 Netty EventLoop）
    return cachedJwtValidator
        .validateAndParseReactive(jwt)
        .flatMap(userInfo -> {
          if (userInfo == null) {
            log.warn("[WsAuth] WebSocket 握手 Token 无效 path={}", path);
            return rejectWebSocket(
                exchange, HttpStatus.UNAUTHORIZED, GatewayErrorCode.TOKEN_INVALID);
          }

          // 提取用户信息
          String userIdStr = userInfo.getUserId() != null ? userInfo.getUserId() : "";
          String usernameStr = userInfo.getUsername() != null ? userInfo.getUsername() : "";
          String rolesStr = userInfo.getRoleCode() != null ? userInfo.getRoleCode() : "";
          String permsStr = "";

          // P0-9: traceId 统一由网关生成（不信任客户端传入的 X-Trace-Id）
          String traceId = TraceIdGenerator.generateSortableTraceId();

          // P2-F4: WebSocket 连接数限制检查（用户 + IP 维度）
          String clientIp = GatewayIpUtils.getClientIp(request);
          return connectionLimiter
              .tryAcquire(userIdStr, clientIp)
              .flatMap(
                  allowed -> {
                    if (!allowed) {
                      log.warn("[WsAuth] WebSocket 连接数超限拒绝 userId={} path={}", userIdStr, path);
                      return rejectWebSocket(
                          exchange, HttpStatus.SERVICE_UNAVAILABLE, GatewayErrorCode.SERVICE_UNAVAILABLE);
                    }

                    // 生成签名（与 AuthGlobalFilter 一致）
                    String sig =
                        InternalHeaderSigner.sign(
                            internalSignSecret, traceId, userIdStr, usernameStr, rolesStr, permsStr);

                    // 注入用户信息头 + 签名头（先剥离客户端伪造的内部头）
                    ServerHttpRequest mutated = request.mutate().headers(h -> {
                      // P0-1: 先剥离客户端伪造的内部头（与 AuthGlobalFilter 一致）
                      for (String name : PathGuard.internalHeaders()) {
                        h.remove(name);
                      }
                      // 注入网关签发的内部头
                      h.set(GatewayConstants.HEADER_TRACE_ID, traceId);
                      h.set(GatewayConstants.HEADER_USER_ID, userIdStr);
                      h.set(GatewayConstants.HEADER_USERNAME, usernameStr);
                      h.set(GatewayConstants.HEADER_USER_ROLES, rolesStr);
                      h.set(GatewayConstants.HEADER_USER_PERMISSIONS, permsStr);
                      // P0-1: 注入签名头（防伪造）
                      h.set(GatewayConstants.HEADER_INTERNAL_SIG, sig);
                    }).build();

                    exchange.getAttributes().put(ATTR_WS_AUTHENTICATED, true);
                    // P0-9: traceId 统一写入响应头
                    exchange.getResponse().getHeaders().add(GatewayConstants.HEADER_TRACE_ID, traceId);

                    log.info("[WsAuth] WebSocket 认证成功 userId={} path={}", userIdStr, path);

                    return chain.filter(exchange.mutate().request(mutated).build());
                  });
        });
  }

  /**
   * P0-4: 校验 WebSocket Origin 是否在允许列表中
   *
   * <p>WebSocket 不受 SOP 约束，必须显式校验 Origin 防止跨域劫持。
   *
   * @param request 服务器 HTTP 请求
   * @return true=Origin 合法或未配置允许列表（dev 模式放行）；false=Origin 非法
   */
  private boolean checkOrigin(ServerHttpRequest request) {
    // 未配置允许列表：dev 环境放行，prod 环境应在配置中强制要求
    if (allowedOriginsConfig == null || allowedOriginsConfig.isBlank()) {
      log.debug("[WsAuth] 未配置 WebSocket allowed-origins，跳过 Origin 校验");
      return true;
    }

    String origin = request.getHeaders().getFirst(HEADER_ORIGIN);
    // 浏览器发起 WebSocket 必带 Origin；非浏览器客户端（如 curl）不带 Origin，放行交由 Token 校验
    if (origin == null || origin.isBlank()) {
      return true;
    }

    Set<String> allowed = parseAllowedOrigins(allowedOriginsConfig);
    for (String allowedOrigin : allowed) {
      if (matchesOrigin(origin, allowedOrigin)) {
        return true;
      }
    }
    return false;
  }

  /**
   * 解析允许的 Origin 列表（逗号分隔）
   *
   * @param config 原始配置字符串
   * @return Origin 集合
   */
  private Set<String> parseAllowedOrigins(String config) {
    return Set.of(config.split(",")).stream()
        .map(String::trim)
        .filter(s -> !s.isEmpty())
        .collect(Collectors.toSet());
  }

  /**
   * Origin 匹配（支持通配符 *）
   *
   * @param origin 客户端 Origin
   * @param allowed 配置的允许 Origin（可含 *）
   * @return true=匹配
   */
  private boolean matchesOrigin(String origin, String allowed) {
    if (allowed.equals("*")) {
      return true;
    }
    // 简单通配符匹配（如 https://*.example.com）
    if (allowed.contains("*")) {
      String regex = allowed.replace(".", "\\.").replace("*", ".*");
      return origin.matches(regex);
    }
    return allowed.equals(origin);
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

  /**
   * 拒绝 WebSocket 握手（P0-D1：统一错误响应写出器）。
   *
   * <p>WebSocket 握手阶段拒绝，浏览器会触发 onerror 回调，不建立连接。 响应携带统一 JSON body 与 {@code X-Trace-Id}，便于排障。
   *
   * @param exchange 服务器 Web 交换上下文
   * @param status HTTP 状态码（401/403/503）
   * @param errorCode 网关业务错误码
   * @return 完成信号 Mono
   */
  private Mono<Void> rejectWebSocket(
      ServerWebExchange exchange, HttpStatus status, GatewayErrorCode errorCode) {
    String traceId = exchange.getRequest().getHeaders().getFirst(GatewayConstants.HEADER_TRACE_ID);
    return GatewayErrorWriter.write(
        exchange, status, errorCode, errorCode.getMessageKey(), traceId);
  }

  /**
   * 过滤器执行顺序：{@code HIGHEST_PRECEDENCE + 8}。
   *
   * <p>先于主鉴权过滤器（+10）处理 WebSocket 握手；认证成功写入 {@link #ATTR_WS_AUTHENTICATED} 标记，{@link
   * AuthGlobalFilter} 检测到后跳过重复认证。
   *
   * @return 顺序值
   */
  @Override
  public int getOrder() {
    return GatewayFilterOrder.WEBSOCKET_AUTH.getOrder();
  }
}
