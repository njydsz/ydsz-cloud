package com.njydsz.common.base.filter;

import java.io.IOException;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.filter.OncePerRequestFilter;

import com.njydsz.common.core.response.BaseResponse;

/**
 * 请求体大小限制过滤器。
 *
 * <p>在请求到达 Controller 之前检查 Content-Length， 超过配置的阈值时直接返回 413 错误，避免大请求占用过多内存。
 *
 * <p>注意：此过滤器仅基于 Content-Length header 进行预检查， 对于 chunked 编码的请求需要在 ContentCachingFilter 中二次校验。
 *
 * @author ydsz-team
 * @since 1.0.0
 */
public class RequestBodySizeLimitFilter extends OncePerRequestFilter {

  private static final Logger LOG = LoggerFactory.getLogger(RequestBodySizeLimitFilter.class);

  private final long maxBodySize;

  public RequestBodySizeLimitFilter(long maxBodySize) {
    this.maxBodySize = maxBodySize;
  }

  @Override
  protected void doFilterInternal(
      HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
      throws ServletException, IOException {
    // 仅对可能携带请求体的方法进行校验
    if (isBodyRequest(request)) {
      long contentLength = request.getContentLengthLong();
      if (contentLength > maxBodySize) {
        loggerReject(request, contentLength);
        rejectRequest(response);
        return;
      }
    }
    filterChain.doFilter(request, response);
  }

  /** 判断请求是否可能携带请求体。 */
  private boolean isBodyRequest(HttpServletRequest request) {
    String method = request.getMethod().toUpperCase();
    return "POST".equals(method) || "PUT".equals(method) || "PATCH".equals(method);
  }

  /** 输出拒绝日志。 */
  private void loggerReject(HttpServletRequest request, long contentLength) {
    if (LOG.isDebugEnabled()) {
      LOG.debug(
          "请求体过大被拒绝 | uri={} | contentLength={} | maxBodySize={}",
          request.getRequestURI(),
          contentLength,
          maxBodySize);
    }
  }

  /** 返回 413 错误。 */
  private void rejectRequest(HttpServletResponse response) throws IOException {
    response.setStatus(HttpServletResponse.SC_REQUEST_ENTITY_TOO_LARGE);
    response.setContentType("application/json;charset=UTF-8");
    BaseResponse<?> body =
        BaseResponse.error(
            "REQUEST_BODY_TOO_LARGE", "请求体过大，最大允许 " + (maxBodySize / 1024 / 1024) + "MB");
    response.getWriter().write(body.toString());
  }
}
