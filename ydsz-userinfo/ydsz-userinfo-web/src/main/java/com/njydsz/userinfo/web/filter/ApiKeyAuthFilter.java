package com.njydsz.userinfo.web.filter;

import java.io.IOException;
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
import org.springframework.web.filter.OncePerRequestFilter;

import com.njydsz.common.core.response.YdszResponse;
import com.njydsz.common.json.YdszJson;
import com.njydsz.userinfo.domain.entity.ApiKey;
import com.njydsz.userinfo.domain.enums.UserInfoExceptionCode;
import com.njydsz.userinfo.server.service.ApiKeyService;

/**
 * API Key 认证过滤器（P1-2 API Key 授权体系）。
 *
 * <p>对 {@code /api/apikey/verify} 等需要 API Key 认证的路径进行认证。
 * 认证流程：
 *
 * <ol>
 *   <li>从 Authorization 请求头提取 Bearer Token</li>
 *   <li>判断 Token 是否以 {@code ak_} 前缀开头（API Key 专属标识）</li>
 *   <li>调用 {@link ApiKeyService#validateKey} 验证有效性</li>
 *   <li>验证通过后将 ApiKey 注入 Request Attribute，供后续使用</li>
 * </ol>
 *
 * <p><b>优先级：</b>{@link Ordered#HIGHEST_PRECEDENCE} + 40，在 ApiSignatureFilter 之后执行，
 * 避免与内部调用签名校验冲突。
 *
 * <p><b>使用方式：</b>
 *
 * <pre>
 * curl -H "Authorization: Beara kay_xxxxxxxxxxxx" https://api.example.com/api/some-endpoint
 * </pre>
 *
 * <p><b>安全设计：</b>
 *
 * <ul>
 *   <li>仅当 Authorization 头以 {@code ak_} 前缀开头时才进行 API Key 认证</li>
 *   <li>验证失败返回 401 JSON 标准错误响应</li>
 *   <li>非 {@code ak_} 前缀的 Token 跳过此过滤器（由其他认证过滤器处理）</li>
 * </ul>
 *
 * @author ydsz-team
 * @since 26.09.07
 */
@Slf4j
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 40)
@RequiredArgsConstructor
public class ApiKeyAuthFilter extends OncePerRequestFilter {

  /** API Key 前缀标识 */
  private static final String API_KEY_PREFIX = "ak_";

  /** Authorization 请求头前缀 */
  private static final String BEARER_PREFIX = "Bearer ";

  /** Bearer 前缀长度 */
  private static final int BEARER_PREFIX_LENGTH = 7;

  /** API Key 在 Request Attribute 中的 Key */
  public static final String API_KEY_ATTRIBUTE = "CURRENT_API_KEY";

  /** 无需 API Key 认证的路径 */
  private static final List<String> EXCLUDE_PATHS = List.of(
      "/api/apikey",      // API Key 管理端点本身（通过 Session 认证）
      "/api/auth",        // 认证端点
      "/actuator",
      "/swagger-ui",
      "/v3/api-docs"
  );

  private final ApiKeyService apiKeyService;

  @Override
  protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
      FilterChain filterChain) throws ServletException, IOException {
    String authorization = request.getHeader("Authorization");

    // 判断是否为 API Key 认证（以 Bearer ak_ 开头）
    if (authorization != null && authorization.startsWith(BEARER_PREFIX)) {
      String token = authorization.substring(BEARER_PREFIX_LENGTH);
      if (token.startsWith(API_KEY_PREFIX)) {
        // 需要进行 API Key 认证
        try {
          ApiKey apiKey = apiKeyService.validateKey(token);
          request.setAttribute(API_KEY_ATTRIBUTE, apiKey);
        } catch (Exception e) {
          log.warn("API Key 认证失败: {}", e.getMessage());
          writeUnauthorizedResponse(response, e.getMessage());
          return;
        }
      }
    }

    filterChain.doFilter(request, response);
  }

  @Override
  protected boolean shouldNotFilter(HttpServletRequest request) {
    String path = request.getRequestURI();
    return EXCLUDE_PATHS.stream().anyMatch(path::startsWith);
  }

  private void writeUnauthorizedResponse(HttpServletResponse response, String message) throws IOException {
    response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
    response.setContentType("application/json;charset=UTF-8");
    YdszResponse<Void> errorResponse = YdszResponse.error(
        UserInfoExceptionCode.API_KEY_INVALID.getCode(),
        message != null ? message : "API Key 认证失败");
    response.getWriter().write(YdszJson.toJson(errorResponse));
  }
}
