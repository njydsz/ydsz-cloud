package com.njydsz.userinfo.web.filter;

import java.io.IOException;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import com.njydsz.common.core.constant.HeaderConstants;
import com.njydsz.userinfo.server.auth.CrossDomainTokenService;
import com.njydsz.userinfo.server.config.CrossDomainSsoProperties;

/**
 * 跨域 SSO 过滤器。
 *
 * <p>在请求入口处处理跨域 Token 传递逻辑，与现有认证过滤器（{@code BaseAuthFilter}）协同工作：
 * 本过滤器负责 CORS 预检放行和跨域 Token 提取，认证过滤器负责 Token 校验与用户上下文注入。
 *
 * <p><b>处理流程：</b>
 *
 * <ol>
 *   <li>检查请求来源 Origin 是否在可信域列表中</li>
 *   <li>OPTIONS 预检请求直接放行并添加 CORS 响应头</li>
 *   <li>对于跨域请求，优先从 Authorization Header 取 Token，其次从跨域 Cookie 取 Token</li>
 *   <li>将提取到的 Token 写入请求属性，供下游认证过滤器使用</li>
 * </ol>
 *
 * <p><b>过滤器优先级：</b>{@link Ordered#HIGHEST_PRECEDENCE} + 50，在 {@link TraceIdFilter} 之后、
 * 认证过滤器之前执行，确保跨域 Token 先被提取再被校验。
 *
 * <p><b>兼容性：</b>同域请求（无 Origin 头或 Origin 与当前域一致）不受影响，直接放行。
 *
 * @author ydsz-team
 * @since 26.09.01
 * @see CrossDomainTokenService 跨域 Token 服务
 * @see CrossDomainSsoProperties 跨域 SSO 配置
 */
@Slf4j
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 50)
@RequiredArgsConstructor
public class CrossDomainSsoFilter extends OncePerRequestFilter {

  /** 请求属性名：跨域 SSO 提取到的 Token */
  public static final String CROSS_DOMAIN_TOKEN_ATTR = "CROSS_DOMAIN_SSO_TOKEN";

  /** Bearer Token 前缀长度（"Bearer " 共 7 个字符） */
  private static final int BEARER_PREFIX_LENGTH = 7;

  private final CrossDomainSsoProperties ssoProperties;
  private final CrossDomainTokenService crossDomainTokenService;

  @Override
  protected void doFilterInternal(
      HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
      throws ServletException, IOException {

    // 未启用跨域 SSO 时直接放行
    if (!ssoProperties.isEnabled()) {
      filterChain.doFilter(request, response);
      return;
    }

    String origin = request.getHeader("Origin");

    // 跨域请求处理
    if (origin != null && !origin.isBlank()
        && crossDomainTokenService.isCrossDomainRequest(request)) {
      handleCrossDomainRequest(request, response, filterChain, origin);
      return;
    }

    // 同域请求直接放行
    filterChain.doFilter(request, response);
  }

  /**
   * 处理跨域请求：CORS 预检放行 + Token 提取。
   *
   * @param request HTTP 请求
   * @param response HTTP 响应
   * @param filterChain 过滤器链
   * @param origin 请求来源 Origin
   * @throws IOException IO 异常
   * @throws ServletException Servlet 异常
   */
  private void handleCrossDomainRequest(
      HttpServletRequest request,
      HttpServletResponse response,
      FilterChain filterChain,
      String origin)
      throws IOException, ServletException {

    // 校验 Origin 是否在白名单中
    if (!crossDomainTokenService.isTrustedDomain(origin, ssoProperties.getTrustedDomains())) {
      log.warn("Cross-domain SSO request from untrusted origin rejected: {}", origin);
      response.sendError(HttpServletResponse.SC_FORBIDDEN, "Untrusted origin");
      return;
    }

    // OPTIONS 预检请求：添加 CORS 头后直接放行
    if ("OPTIONS".equalsIgnoreCase(request.getMethod())) {
      crossDomainTokenService.addCorsHeaders(response, origin, ssoProperties.getTrustedDomains());
      response.setStatus(HttpServletResponse.SC_OK);
      log.debug("CORS preflight request passed for origin: {}", origin);
      return;
    }

    // 非预检请求：提取跨域 Token 并写入请求属性
    String token = extractCrossDomainToken(request);
    if (token != null) {
      request.setAttribute(CROSS_DOMAIN_TOKEN_ATTR, token);
      log.debug("Cross-domain SSO token extracted and set as request attribute");
    }

    // 添加 CORS 响应头（必须在 filterChain 之前设置，确保异常时也有 CORS 头）
    crossDomainTokenService.addCorsHeaders(response, origin, ssoProperties.getTrustedDomains());

    filterChain.doFilter(request, response);
  }

  /**
   * 从跨域请求中提取 Token。
   *
   * <p>优先级：Authorization Header > 跨域 Cookie > postMessage 回调参数。
   * 提取到的 Token 写入请求属性 {@link #CROSS_DOMAIN_TOKEN_ATTR}，供下游认证过滤器读取。
   *
   * @param request HTTP 请求
   * @return Token 字符串，未找到时返回 null
   */
  private String extractCrossDomainToken(HttpServletRequest request) {
    // 1. 优先从 Authorization Header 取 Token
    String authHeader = request.getHeader(HeaderConstants.AUTHORIZATION);
    if (authHeader != null && authHeader.startsWith("Bearer ")) {
      String token = authHeader.substring(BEARER_PREFIX_LENGTH);
      if (!token.isBlank()) {
        log.debug("Token extracted from Authorization header");
        return token;
      }
    }

    // 2. 从跨域 Cookie 取 Token
    String cookieToken = crossDomainTokenService.extractTokenFromCookie(
        request, ssoProperties.getCookieName());
    if (cookieToken != null) {
      log.debug("Token extracted from cross-domain cookie");
      return cookieToken;
    }

    // 3. 从 postMessage 回调参数取 Token
    String postMessageToken = crossDomainTokenService.extractTokenFromPostMessage(request);
    if (postMessageToken != null) {
      log.debug("Token extracted from postMessage callback");
      return postMessageToken;
    }

    log.debug("No cross-domain SSO token found in request");
    return null;
  }

  @Override
  protected boolean shouldNotFilter(HttpServletRequest request) {
    // 排除 actuator 探活路径
    String uri = request.getRequestURI();
    return uri != null && uri.startsWith("/actuator");
  }
}
