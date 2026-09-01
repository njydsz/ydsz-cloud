package com.njydsz.common.safe.filter;

import java.io.IOException;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.jspecify.annotations.NonNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.Ordered;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * 统一请求体缓存过滤器
 *
 * <p>在过滤器链最前端将请求体读取到内存，包装为 {@link CachedBodyHttpServletRequestWrapper} 后传递给下游过滤器。后续 XSS / SQL 注入 /
 * API 签名等过滤器可直接复用已缓存的请求体， 消除各自独立读取和包装导致的重复 I/O 与内存拷贝。
 *
 * <p>仅对包含请求体的请求（POST/PUT/PATCH 等）执行缓存，GET 等无 Body 请求直接放行。
 *
 * <p><b>设计考量：</b>
 *
 * <ul>
 *   <li>优先级设为 {@link Ordered#HIGHEST_PRECEDENCE}，确保在安全过滤器链中第一个执行
 *   <li>请求体大小限制为 10MB，超过此阈值不缓存（避免大文件上传场景内存溢出）
 *   <li>包装后的请求通过 {@link CachedBodyHttpServletRequestWrapper#getCachedBody()} 提供字节访问
 * </ul>
 *
 * @author ydsz-team
 * @since 26.09.01
 */
public class SafeRequestBodyCacheFilter extends OncePerRequestFilter {

  private static final Logger LOG = LoggerFactory.getLogger(SafeRequestBodyCacheFilter.class);

  /** 请求体最大缓存大小（10MB） */
  private static final int MAX_CACHEABLE_BODY_SIZE = 10 * 1024 * 1024;

  @Override
  protected void doFilterInternal(
      @NonNull HttpServletRequest request,
      @NonNull HttpServletResponse response,
      @NonNull FilterChain filterChain)
      throws ServletException, IOException {

    // 仅对有请求体的请求类型执行缓存
    if (!shouldCacheBody(request)) {
      filterChain.doFilter(request, response);
      return;
    }

    // 已包装过的请求直接放行（避免重复包装）
    if (request instanceof CachedBodyHttpServletRequestWrapper) {
      filterChain.doFilter(request, response);
      return;
    }

    // 检查 Content-Length 是否超过阈值
    int contentLength = request.getContentLength();
    if (contentLength > MAX_CACHEABLE_BODY_SIZE) {
      LOG.debug("请求体过大，跳过缓存 | URI={}, size={}", request.getRequestURI(), contentLength);
      filterChain.doFilter(request, response);
      return;
    }

    try {
      CachedBodyHttpServletRequestWrapper wrappedRequest =
          new CachedBodyHttpServletRequestWrapper(request, request.getInputStream().readAllBytes());
      filterChain.doFilter(wrappedRequest, response);
    } catch (IOException e) {
      LOG.warn("统一请求体缓存读取失败 | URI={}", request.getRequestURI(), e);
      // 读取失败时降级为原始请求，不影响后续过滤器
      filterChain.doFilter(request, response);
    }
  }

  /**
   * 判断是否应该缓存请求体
   *
   * @param request HTTP 请求
   * @return 需要缓存返回 true
   */
  private boolean shouldCacheBody(HttpServletRequest request) {
    String method = request.getMethod();
    if (method == null) {
      return false;
    }
    return switch (method.toUpperCase()) {
      case "POST", "PUT", "PATCH", "DELETE" -> true;
      default -> false;
    };
  }
}
