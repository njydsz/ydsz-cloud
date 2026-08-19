package com.njydsz.userinfo.web.filter;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

import com.njydsz.common.json.YdszJson;
import com.njydsz.userinfo.domain.enums.UserInfoExceptionCode;
import com.njydsz.userinfo.domain.scim.ScimError;
import com.njydsz.userinfo.server.config.ScimProperties;

/**
 * SCIM 2.0 Bearer Token 认证过滤器。
 *
 * <p>拦截 {@code /scim/v2/**} 路径的所有请求，校验 HTTP Authorization 头中的 Bearer Token。
 * 认证失败时返回 SCIM 标准错误格式（RFC 7644 Section 3.12），不进入后续 Filter 链。
 *
 * <p><b>认证流程：</b>
 *
 * <ol>
 *   <li>检查请求路径是否为 SCIM 端点（{@code /scim/v2/**}）
 *   <li>非 SCIM 路径直接放行
 *   <li>读取 Authorization 头，校验格式为 "Bearer &lt;token&gt;"
 *   <li>比对 Token 与配置的 {@code ydsz.userinfo.scim.auth-token}
 *   <li>校验通过放行，失败返回 401 + SCIM 标准错误体
 * </ol>
 *
 * <p><b>优先级：</b>{@link Ordered#HIGHEST_PRECEDENCE} + 20，在 TraceIdFilter 之后、
 * ApiSignatureFilter 之前执行，确保 SCIM 请求的认证先于其他过滤器。
 *
 * <p><b>安全设计：</b>
 *
 * <ul>
 *   <li>Token 比较使用 {@link java.security.MessageDigest#isEqual} 防时序攻击
 *   <li>认证失败不暴露具体原因（Token 无效 vs 缺失统一返回 "authentication failed"）
 *   <li>SCIM 使用独立认证体系，不依赖 ydsz 主系统的 Session/Token
 * </ul>
 *
 * @author ydsz-team
 * @since 1.6.0
 */
@Slf4j
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 20)
@RequiredArgsConstructor
public class ScimAuthFilter extends OncePerRequestFilter {

  /** SCIM 端点路径前缀。 */
  private static final String SCIM_PATH_PREFIX = "/scim/v2";

  /** Bearer Token 前缀。 */
  private static final String BEARER_PREFIX = "Bearer ";

  /** SCIM 错误响应 Schema。 */
  private static final List<String> ERROR_SCHEMA =
      List.of("urn:ietf:params:scim:api:messages:2.0:Error");

  private final ScimProperties scimProperties;

  @Override
  protected void doFilterInternal(
      HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
      throws ServletException, IOException {

    // 仅对 SCIM 路径进行认证
    if (!isScimPath(request)) {
      filterChain.doFilter(request, response);
      return;
    }

    // SCIM 功能未启用时直接拒绝
    if (!scimProperties.isEnabled()) {
      writeScimError(response, "403", "SCIM service is disabled", null);
      return;
    }

    // 校验 Authorization 头
    String authHeader = request.getHeader("Authorization");
    if (!StringUtils.hasText(authHeader) || !authHeader.startsWith(BEARER_PREFIX)) {
      log.warn("SCIM auth: missing or invalid Authorization header, uri={}",
          request.getRequestURI());
      writeScimError(response, "401", "Missing or invalid Authorization header", null);
      return;
    }

    String token = authHeader.substring(BEARER_PREFIX.length()).trim();
    if (!isTokenValid(token)) {
      log.warn("SCIM auth: invalid Bearer Token, uri={}", request.getRequestURI());
      writeScimError(response, "401", "Authentication failed", null);
      return;
    }

    filterChain.doFilter(request, response);
  }

  /**
   * 判断请求路径是否为 SCIM 端点。
   *
   * @param request HTTP 请求
   * @return true 表示是 SCIM 路径
   */
  private boolean isScimPath(HttpServletRequest request) {
    String path = request.getRequestURI();
    return path.startsWith(SCIM_PATH_PREFIX);
  }

  /**
   * 校验 Bearer Token 是否有效。
   *
   * <p>使用 {@link java.security.MessageDigest#isEqual} 进行常量时间比较，防止时序攻击。
   *
   * @param token 客户端提供的 Token
   * @return true 表示 Token 有效
   */
  private boolean isTokenValid(String token) {
    String expectedToken = scimProperties.getAuthToken();
    if (expectedToken == null || expectedToken.isEmpty()) {
      return false;
    }
    return java.security.MessageDigest.isEqual(
        token.getBytes(StandardCharsets.UTF_8),
        expectedToken.getBytes(StandardCharsets.UTF_8));
  }

  /**
   * 写入 SCIM 标准错误响应。
   *
   * @param response HTTP 响应
   * @param status HTTP 状态码（字符串）
   * @param detail 错误描述
   * @param scimType SCIM 错误类型（可选）
   */
  private void writeScimError(
      HttpServletResponse response, String status, String detail, String scimType)
      throws IOException {
    response.setStatus(Integer.parseInt(status));
    response.setContentType("application/scim+json");
    response.setCharacterEncoding("UTF-8");

    ScimError error = ScimError.builder()
        .schemas(ERROR_SCHEMA)
        .status(status)
        .detail(detail)
        .scimType(scimType)
        .build();

    response.getWriter().write(YdszJson.toJson(error));
  }
}
