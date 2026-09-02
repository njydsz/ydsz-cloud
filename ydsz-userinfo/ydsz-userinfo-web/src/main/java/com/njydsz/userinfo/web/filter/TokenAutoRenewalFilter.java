package com.njydsz.userinfo.web.filter;

import java.io.IOException;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import com.njydsz.common.auth.model.UserInfo;
import com.njydsz.common.auth.token.TokenService;
import com.njydsz.common.core.constant.HeaderConstants;
import com.njydsz.userinfo.server.config.UserInfoProperties;

/**
 * Token 自动续签过滤器（P1-2）。
 *
 * <p>在响应返回前检查 access_token 剩余有效期，当剩余有效期低于配置的阈值百分比时，
 * 自动签发新的 access_token 并在响应头 {@code X-Access-Token} 中返回，前端检测到该响应头后
 * 替换本地存储的 Token。
 *
 * <p><b>续签条件：</b>
 *
 * <ul>
 *   <li>请求携带有效的 access_token</li>
 *   <li>剩余有效期 ＜ TTL × thresholdPercent</li>
 *   <li>Token 未被加入黑名单</li>
 * </ul>
 *
 * <p><b>安全约束：</b>
 *
 * <ul>
 *   <li>仅签发 access_token（不签发 refresh_token，refresh_token 续签保持现有 /refresh 端点）</li>
 *   <li>续签仅在响应头返回，不修改请求上下文</li>
 *   <li>续签失败不阻塞原请求（降级静默，仅日志告警）</li>
 * </ul>
 *
 * <p><b>Nginx/网关配置示例：</b>
 *
 * <pre>
 * add_header X-Access-Token $upstream_http_x_access_token always;
 * </pre>
 *
 * @author ydsz-team
 * @since 26.09.01
 */
@Slf4j
@Component
@Order(200)
@RequiredArgsConstructor
public class TokenAutoRenewalFilter extends OncePerRequestFilter {

  private final TokenService tokenService;
  private final UserInfoProperties userInfoProperties;

  /** Bearer Token 前缀长度（"Bearer " 共 7 个字符） */
  private static final int BEARER_PREFIX_LENGTH = 7;

  /** 新 Token 响应头名称 */
  public static final String NEW_TOKEN_HEADER = "X-Access-Token";

  /** 是否启用自动续签的响应头（便于前端判断服务端是否支持） */
  public static final String RENEWAL_ENABLED_HEADER = "X-Token-Renewal";

  @Override
  protected void doFilterInternal(
      HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
      throws ServletException, IOException {

    // 先执行请求链
    filterChain.doFilter(request, response);

    // 检查是否启用自动续签
    if (!userInfoProperties.isTokenAutoRenewalEnabled()) {
      return;
    }

    // 续签 attempt
    tryRenewToken(request, response);
  }

  /**
   * 尝试续签 Token。
   *
   * <p>续签逻辑：
   * 1. 从 Authorization 头提取 access_token
   * 2. 检查剩余有效期是否低于阈值
   * 3. 如果低于阈值，签发新 Token（保持原用户信息）并通过响应头返回
   *
   * @param request  HTTP 请求
   * @param response HTTP 响应
   */
  private void tryRenewToken(HttpServletRequest request, HttpServletResponse response) {
    try {
      String authorization = request.getHeader(HeaderConstants.AUTHORIZATION);
      if (authorization == null || !authorization.startsWith("Bearer ")) {
        return;
      }

      String accessToken = authorization.substring(BEARER_PREFIX_LENGTH);
      if (accessToken.isBlank()) {
        return;
      }

      // 检查 Token 有效性
      if (!tokenService.validateAccessToken(accessToken)) {
        return;
      }

      // 获取剩余有效期
      long remainingTtl = tokenService.getAccessTokenRemainingTtl(accessToken);
      long totalTtl = userInfoProperties.getTokenTtlSeconds();

      // 计算续签阈值（剩余有效期 < 总TTL × 阈值百分比）
      long threshold = (long) (totalTtl * userInfoProperties.getTokenAutoRenewalThresholdPercent() / 100.0);

      if (remainingTtl > 0 && remainingTtl < threshold) {
        // 需要续签
        UserInfo userInfo = tokenService.parseAccessToken(accessToken);
        if (userInfo != null) {
          String newAccessToken = tokenService.issueAccessToken(userInfo);
          response.setHeader(NEW_TOKEN_HEADER, newAccessToken);
          log.debug("Token 自动续签完成: userId={}, remainingTtl={}s", userInfo.getUserId(), remainingTtl);
        }
      }
    } catch (Exception e) {
      // 续签失败不阻塞原请求
      log.warn("Token 自动续签异常（不影响原请求）: {}", e.getMessage());
    }
  }

  @Override
  protected boolean shouldNotFilter(HttpServletRequest request) {
    // 不对登录/刷新/验证码等端点执行续签
    String path = request.getRequestURI();
    return path.contains("/auth/login")
        || path.contains("/auth/refresh")
        || path.contains("/captcha")
        || path.contains("/oauth2/token")
        || path.contains("/.well-known");
  }
}
