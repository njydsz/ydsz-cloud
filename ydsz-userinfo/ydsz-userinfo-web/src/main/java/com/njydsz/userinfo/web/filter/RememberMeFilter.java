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

import com.njydsz.common.core.context.RequestContext;
import com.njydsz.userinfo.server.auth.RememberMeService;
import com.njydsz.userinfo.server.config.RememberMeProperties;

/**
 * Remember-Me 滑动续期过滤器。
 *
 * <p>在请求处理链中检查 Remember-Me 状态，实现滑动过期功能：
 *
 * <ol>
 *   <li>如果当前请求已认证（RequestContext 中有 userId），检查是否需要滑动续期</li>
 *   <li>如果当前请求未认证但存在 Remember-Me Cookie，尝试自动登录（预留扩展点）</li>
 *   <li>滑动续期：更新 Redis 中的 session TTL 和 Token 过期时间</li>
 * </ol>
 *
 * <p><b>过滤器优先级：</b>{@link Ordered#HIGHEST_PRECEDENCE} + 40，在 {@link
 * com.njydsz.userinfo.web.filter.CrossDomainSsoFilter} 之后、认证过滤器之前执行，
 * 确保跨域 Token 先被提取再执行滑动续期判断。
 *
 * <p><b>兼容性：</b>Remember-Me 功能关闭时直接放行所有请求，不影响现有认证流程。
 *
 * @author ydsz-team
 * @since 26.09.01
 * @see RememberMeService Remember-Me 服务
 * @see RememberMeProperties Remember-Me 配置
 */
@Slf4j
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 40)
@RequiredArgsConstructor
public class RememberMeFilter extends OncePerRequestFilter {

  private final RememberMeProperties rememberMeProperties;
  private final RememberMeService rememberMeService;

  @Override
  protected void doFilterInternal(
      HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
      throws ServletException, IOException {

    // 未启用 Remember-Me 时直接放行
    if (!rememberMeProperties.isEnabled()) {
      filterChain.doFilter(request, response);
      return;
    }

    try {
      // 如果当前请求已认证，检查是否需要滑动续期
      String userId = RequestContext.getUserId();
      if (userId != null && !userId.isBlank()) {
        handleAuthenticatedRequest(request, response, userId);
      } else {
        handleUnauthenticatedRequest(request, response);
      }
    } catch (Exception e) {
      // 滑动续期失败不影响主流程，仅记录日志
      log.warn("Remember-Me filter error: {}", e.getMessage());
    }

    filterChain.doFilter(request, response);
  }

  /**
   * 处理已认证请求：检查并执行滑动续期。
   *
   * @param request HTTP 请求
   * @param response HTTP 响应
   * @param userId 当前用户 ID
   */
  private void handleAuthenticatedRequest(
      HttpServletRequest request, HttpServletResponse response, String userId) {
    // 从请求属性中获取当前 access_token（由上游认证过滤器设置）
    String accessToken = (String) request.getAttribute("ACCESS_TOKEN_ATTR");
    if (accessToken == null || accessToken.isBlank()) {
      return;
    }

    // 检查是否需要滑动续期
    if (rememberMeService.shouldSlidingExtend(accessToken)) {
      rememberMeService.extendSession(accessToken);
      log.debug("Remember-Me sliding extension executed for userId={}", userId);
    }
  }

  /**
   * 处理未认证请求：检查 Remember-Me Cookie 并尝试自动登录。
   *
   * <p>当前版本仅做日志记录，后续可扩展为自动签发新 Token。
   *
   * @param request HTTP 请求
   * @param response HTTP 响应
   */
  private void handleUnauthenticatedRequest(HttpServletRequest request, HttpServletResponse response) {
    String cookieUserId = rememberMeService.resolveUserIdFromCookie(request);
    if (cookieUserId != null && !cookieUserId.isBlank()) {
      log.debug("Remember-Me cookie found for unauthenticated request, userId={}", cookieUserId);
      // 预留扩展点：自动登录（签发新 Token）
      // 当前版本不做自动登录，仅记录日志
    }
  }

  @Override
  protected boolean shouldNotFilter(HttpServletRequest request) {
    // Remember-Me 功能关闭时放行所有请求
    if (!rememberMeProperties.isEnabled()) {
      return true;
    }
    // 排除 actuator 探活路径
    String uri = request.getRequestURI();
    return uri != null && uri.startsWith("/actuator");
  }
}
