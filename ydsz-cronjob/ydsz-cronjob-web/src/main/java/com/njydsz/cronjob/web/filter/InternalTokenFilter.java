package com.njydsz.cronjob.web.filter;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import com.njydsz.cronjob.domain.constants.CronjobConstants;
import com.njydsz.cronjob.server.config.CronjobProperties;

/**
 * 内部通信鉴权过滤器（P0-1 安全加固）。
 *
 * <p>拦截 {@code /api/v1/cronjob/internal/**} 节点间派发端点，校验请求头
 * {@code X-Ydsz-Internal-Token} 与配置 {@code ydsz.cronjob.remote.access-token} 是否一致。
 *
 * <h3>行为约定</h3>
 *
 * <ul>
 *   <li>配置 token 为空且 {@code ydsz.cronjob.remote.allow-empty-token=false}（默认）：fail-closed，直接拒绝返回 401
 *   <li>配置 token 为空且 allow-empty-token=true：放行（仅限可信内网开发环境显式开启）
 *   <li>配置 token 非空：强制校验，令牌不匹配或缺失返回 401
 *   <li>非 internal 路径：直接放行，不参与过滤
 * </ul>
 *
 * <p>令牌比较使用 {@link MessageDigest#isEqual(byte[], byte[])} 常量时间比较，防止时序侧信道。
 *
 * @author ydsz-team
 * @since 1.0.0
 */
@Slf4j
@Component
public class InternalTokenFilter extends OncePerRequestFilter {

  /** 鉴权失败响应体（统一 JSON 结构，便于调用方解析） */
  private static final String UNAUTHORIZED_BODY =
      "{\"code\":401,\"data\":null,\"message\":\"unauthorized: invalid internal token\"}";

  /** 401 状态码 */
  private static final int HTTP_UNAUTHORIZED = 401;

  private final CronjobProperties cronjobProperties;

  /**
   * 构造内部通信鉴权过滤器。
   *
   * @param cronjobProperties 调度配置（读取 remote.access-token）
   */
  public InternalTokenFilter(CronjobProperties cronjobProperties) {
    this.cronjobProperties = cronjobProperties;
  }

  @Override
  protected boolean shouldNotFilter(HttpServletRequest request) {
    // 仅拦截内部派发端点，其余路径（对外 API/OpenAPI/静态资源）不参与
    String path = request.getRequestURI();
    return !path.startsWith(CronjobConstants.INTERNAL_API_PREFIX + "/");
  }

  @Override
  protected void doFilterInternal(
      HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
      throws ServletException, IOException {
    String expectedToken = cronjobProperties.getRemote().getAccessToken();
    // 未配置令牌：默认 fail-closed 拒绝（云顶安全规范），仅 allow-empty-token=true 时放行（可信内网开发环境）
    if (expectedToken == null || expectedToken.isBlank()) {
      if (cronjobProperties.getRemote().isAllowEmptyToken()) {
        filterChain.doFilter(request, response);
        return;
      }
      log.warn("[InternalTokenFilter] 未配置 access-token 且未开启 allow-empty-token, 拒绝内部请求: uri={} from={}",
          request.getRequestURI(), request.getRemoteAddr());
      reject(response);
      return;
    }
    String providedToken = request.getHeader(CronjobConstants.INTERNAL_TOKEN_HEADER);
    if (providedToken == null || providedToken.isBlank()) {
      log.warn("[InternalTokenFilter] 内部请求缺少令牌: uri={} from={}",
          request.getRequestURI(), request.getRemoteAddr());
      reject(response);
      return;
    }
    if (!constantTimeEquals(providedToken, expectedToken)) {
      log.warn("[InternalTokenFilter] 内部请求令牌校验失败: uri={} from={}",
          request.getRequestURI(), request.getRemoteAddr());
      reject(response);
      return;
    }
    filterChain.doFilter(request, response);
  }

  /**
   * 常量时间比较两个令牌（防止时序侧信道猜测令牌）。
   *
   * @param provided 请求携带的令牌
   * @param expected 配置的期望令牌
   * @return true 相等
   */
  private boolean constantTimeEquals(String provided, String expected) {
    byte[] providedBytes = provided.getBytes(StandardCharsets.UTF_8);
    byte[] expectedBytes = expected.getBytes(StandardCharsets.UTF_8);
    return MessageDigest.isEqual(providedBytes, expectedBytes);
  }

  /**
   * 输出 401 拒绝响应。
   *
   * @param response HTTP 响应
   * @throws IOException 响应写出失败
   */
  private void reject(HttpServletResponse response) throws IOException {
    response.setStatus(HTTP_UNAUTHORIZED);
    response.setContentType("application/json; charset=UTF-8");
    response.getWriter().write(UNAUTHORIZED_BODY);
  }
}
