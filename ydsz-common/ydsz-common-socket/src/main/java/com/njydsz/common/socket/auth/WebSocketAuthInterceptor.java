package com.njydsz.common.socket.auth;

import java.util.List;
import java.util.Map;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.http.server.ServletServerHttpRequest;
import org.springframework.util.StringUtils;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.server.HandshakeInterceptor;

import com.njydsz.common.auth.model.UserInfo;
import com.njydsz.common.auth.token.TokenService;
import com.njydsz.common.safe.util.ClientIpResolver;
import com.njydsz.common.socket.audit.WebSocketAuditService;
import com.njydsz.common.socket.config.WebSocketProperties;
import com.njydsz.common.socket.constant.WebSocketConstants;
import com.njydsz.common.socket.ratelimit.ConnectionLimiter;

/**
 * WebSocket 握手鉴权拦截器（通用版）。
 *
 * <p>支持两种鉴权模式：
 *
 * <ol>
 *   <li><b>JWT Token</b>：从查询参数 {@code token} 或请求头 {@code Authorization: Bearer xxx} 提取 JWT，通过
 *       {@link TokenService} 校验合法性（默认模式）
 *   <li><b>网关透传</b>（P1-5）：浏览器无法在 WebSocket 升级请求中设置自定义头， 因此网关认证后注入 {@code X-User-Id} / {@code
 *       X-Username} / {@code X-Gateway-Secret}。 后端验证共享密钥或 IP 白名单后信任这些头部
 * </ol>
 *
 * <p>鉴权流程：优先尝试 JWT → JWT 缺失/无效时回退网关透传 → 均失败则返回 401。
 *
 * <p>与旧版（message-server 中的 {@code WebSocketAuthHandshakeInterceptor}）不同， 本类依赖 {@link TokenService}
 * 接口而非具体的 {@code JwtTokenProvider}， 可适配任何 TokenService 实现，解耦更彻底。
 *
 * @author ydsz-team
 * @since 1.0.0
 */
@Slf4j
@RequiredArgsConstructor
public class WebSocketAuthInterceptor implements HandshakeInterceptor {

  private final TokenService tokenService;
  private final ConnectionLimiter connectionLimiter;
  private final WebSocketAuditService auditService;
  private final WebSocketProperties properties;

  @Override
  public boolean beforeHandshake(
      ServerHttpRequest request,
      ServerHttpResponse response,
      WebSocketHandler wsHandler,
      Map<String, Object> attributes) {
    // ① 优先尝试 JWT Token 鉴权
    String token = extractToken(request);
    if (StringUtils.hasText(token)) {
      return authenticateByJwt(request, response, attributes, token);
    }
    // ② JWT 缺失时，尝试网关透传认证（P1-5）
    if (authenticateByGateway(request, response, attributes)) {
      return true;
    }
    // ③ 均失败则拒绝
    log.warn("[WS-Auth] 握手拒绝: 无有效 JWT 且网关认证未通过, remote={}", request.getRemoteAddress());
    response.setStatusCode(HttpStatus.UNAUTHORIZED);
    return false;
  }

  /**
   * JWT Token 鉴权。
   *
   * @param request HTTP 请求
   * @param response HTTP 响应
   * @param attributes 握手属性
   * @param token JWT token
   * @return 是否通过
   */
  private boolean authenticateByJwt(
      ServerHttpRequest request,
      ServerHttpResponse response,
      Map<String, Object> attributes,
      String token) {
    UserInfo userInfo = tokenService.parseAccessToken(token);
    if (userInfo == null || !StringUtils.hasText(userInfo.getUserId())) {
      log.warn("[WS-Auth] JWT 无效或过期, remote={}", request.getRemoteAddress());
      response.setStatusCode(HttpStatus.UNAUTHORIZED);
      return false;
    }
    return completeAuthentication(
        request, response, attributes, userInfo.getUserId(), userInfo.getUsername());
  }

  /**
   * 网关透传认证（P1-5）。
   *
   * <p>验证 {@code X-Gateway-Secret} 共享密钥或来源 IP 白名单，通过后信任 {@code X-User-Id} / {@code X-Username} 头部。
   *
   * @param request HTTP 请求
   * @param response HTTP 响应
   * @param attributes 握手属性
   * @return 是否通过
   */
  private boolean authenticateByGateway(
      ServerHttpRequest request, ServerHttpResponse response, Map<String, Object> attributes) {
    if (!isGatewayAuthAvailable()) {
      return false;
    }
    // 安全校验：共享密钥或 IP 白名单
    if (!verifyGatewayTrust(request)) {
      log.warn("[WS-Auth] 网关认证失败: 共享密钥不匹配且 IP 不在白名单, remote={}", request.getRemoteAddress());
      response.setStatusCode(HttpStatus.UNAUTHORIZED);
      return false;
    }
    String userId = request.getHeaders().getFirst(WebSocketConstants.WS_GATEWAY_USER_ID_HEADER);
    String username = request.getHeaders().getFirst(WebSocketConstants.WS_GATEWAY_USERNAME_HEADER);
    if (!StringUtils.hasText(userId)) {
      log.warn("[WS-Auth] 网关透传缺少 X-User-Id 头");
      response.setStatusCode(HttpStatus.UNAUTHORIZED);
      return false;
    }
    return completeAuthentication(
        request, response, attributes, userId, username != null ? username : userId);
  }

  /**
   * 完成认证：连接数检查 + 写入属性 + 审计。
   *
   * @param request HTTP 请求
   * @param response HTTP 响应
   * @param attributes 握手属性
   * @param userId 用户 ID
   * @param username 用户名
   * @return 是否通过（连接数检查）
   */
  private boolean completeAuthentication(
      ServerHttpRequest request,
      ServerHttpResponse response,
      Map<String, Object> attributes,
      String userId,
      String username) {
    // 连接数限制检查（P2-1）
    if (connectionLimiter != null && !connectionLimiter.allowConnection(userId)) {
      log.warn("[WS-Auth] 握手拒绝: 连接数超限, userId={}", userId);
      response.setStatusCode(HttpStatus.TOO_MANY_REQUESTS);
      return false;
    }
    attributes.put(WebSocketConstants.WS_ATTR_USER_ID, userId);
    attributes.put(WebSocketConstants.WS_ATTR_USERNAME, username);
    log.info("[WS-Auth] 握手成功: userId={}, username={}", userId, username);
    // 审计连接建立（P2-5）
    if (auditService != null) {
      auditService.auditConnect(userId, null, resolveRemoteIp(request));
    }
    return true;
  }

  /**
   * 判断网关透传认证是否可用（配置了共享密钥或 IP 白名单）。
   *
   * @return 是否可用
   */
  private boolean isGatewayAuthAvailable() {
    var auth = properties.getAuth();
    if (auth == null) {
      return false;
    }
    return StringUtils.hasText(auth.getGatewaySecret())
        || (auth.getTrustedIps() != null && !auth.getTrustedIps().isEmpty());
  }

  /**
   * 验证请求是否来自受信任的网关。
   *
   * <p>满足以下任一条件即通过：
   *
   * <ul>
   *   <li>请求头 {@code X-Gateway-Secret} 与配置的共享密钥一致
   *   <li>请求来源 IP 在 {@code trustedIps} 白名单内
   * </ul>
   *
   * @param request HTTP 请求
   * @return 是否受信任
   */
  private boolean verifyGatewayTrust(ServerHttpRequest request) {
    var auth = properties.getAuth();
    if (auth == null) {
      return false;
    }
    // 校验共享密钥
    if (StringUtils.hasText(auth.getGatewaySecret())) {
      String provided = request.getHeaders().getFirst(WebSocketConstants.WS_GATEWAY_SECRET_HEADER);
      if (auth.getGatewaySecret().equals(provided)) {
        return true;
      }
    }
    // 校验 IP 白名单
    List<String> trustedIps = auth.getTrustedIps();
    if (trustedIps != null && !trustedIps.isEmpty()) {
      String remoteIp = resolveRemoteIp(request);
      if (remoteIp != null && trustedIps.contains(remoteIp)) {
        return true;
      }
    }
    return false;
  }

  /**
   * 解析客户端真实 IP（含代理穿透）。
   *
   * @param request HTTP 请求
   * @return 客户端 IP 字符串
   */
  private String resolveRemoteIp(ServerHttpRequest request) {
    if (request instanceof ServletServerHttpRequest servletRequest) {
      return ClientIpResolver.getClientIp(servletRequest.getServletRequest());
    }
    return request.getRemoteAddress() != null
        ? request.getRemoteAddress().getAddress().getHostAddress()
        : "unknown";
  }

  @Override
  public void afterHandshake(
      ServerHttpRequest request,
      ServerHttpResponse response,
      WebSocketHandler wsHandler,
      Exception exception) {
    // 握手后无需处理
  }

  /**
   * 从请求中提取 JWT token：优先查询参数 {@code token}，回退请求头 {@code Authorization}。
   *
   * @param request HTTP 请求
   * @return token 字符串，无则返回 null
   */
  private String extractToken(ServerHttpRequest request) {
    // ① 优先从查询参数提取（WebSocket 浏览器不支持自定义请求头）
    if (request instanceof ServletServerHttpRequest servletRequest) {
      String param =
          servletRequest.getServletRequest().getParameter(WebSocketConstants.WS_TOKEN_PARAM);
      if (StringUtils.hasText(param)) {
        return param.trim();
      }
    }
    // ② 回退到 Authorization 请求头（Bearer xxx）
    String header = request.getHeaders().getFirst(WebSocketConstants.WS_TOKEN_HEADER);
    if (StringUtils.hasText(header)) {
      String trimmed = header.trim();
      if (trimmed.startsWith("Bearer ")) {
        return trimmed.substring("Bearer ".length()).trim();
      }
      return trimmed;
    }
    return null;
  }
}
