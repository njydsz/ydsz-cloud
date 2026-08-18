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

import com.njydsz.userinfo.server.metrics.UserInfoMetrics;
import com.njydsz.userinfo.server.trace.TraceContext;

/**
 * 用户中心 HTTP 请求耗时统计过滤器。
 *
 * <p>对所有经过 Controller 的请求记录耗时指标 {@code ydsz_userinfo_http_request_duration_ms}， 按 URI（归一化路径模板）和
 * HTTP 方法维度分组，便于在 Grafana 中查看接口 P50/P90/P99 延迟分布。
 *
 * <p><b>URI 归一化：</b>将 PathVariable（如 {@code /api/v1/user/123}）归一化为模板路径 （如 {@code /api/v1/user/{id}}），避免不同
 * ID 产生大量时间序列。
 *
 * <p><b>过滤器优先级：</b>设置为 {@link Ordered#LOWEST_PRECEDENCE} - 100，在认证过滤器之后执行， 确保只统计进入 Controller 的请求。
 *
 * @author ydsz-team
 * @since 1.0.0
 */
@Slf4j
@Component
@Order(Ordered.LOWEST_PRECEDENCE - 100)
@RequiredArgsConstructor
public class UserInfoMetricsFilter extends OncePerRequestFilter {

  private final UserInfoMetrics userInfoMetrics;

  @Override
  protected void doFilterInternal(
      HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
      throws ServletException, IOException {

    long startTime = System.currentTimeMillis();
    String method = request.getMethod();
    String uri = normalizeUri(request.getRequestURI());

    try {
      filterChain.doFilter(request, response);
    } finally {
      long durationMs = System.currentTimeMillis() - startTime;
      int status = response.getStatus();

      // P1-10: 指标标签携带 traceId，便于按链路聚合排障
      String traceId = TraceContext.getTraceId();
      userInfoMetrics.recordTimer(
          "http_request_duration_ms",
          durationMs,
          "method", method,
          "uri", uri,
          "status", String.valueOf(status),
          "traceId", traceId != null ? traceId : "none");

      userInfoMetrics.recordHttpCount(
          "http_requests_total",
          "method", method,
          "uri", uri,
          "status", String.valueOf(status),
          "traceId", traceId != null ? traceId : "none");
    }
  }

  /**
   * 归一化 URI：将数字 ID 替换为 {id}，避免高基数时间序列。
   *
   * @param uri 原始 URI
   * @return 归一化后的 URI
   */
  private String normalizeUri(String uri) {
    if (uri == null) {
      return "unknown";
    }
    return uri.replaceAll("/\\d+", "/{id}").replaceAll("/[0-9a-fA-F-]{36}", "/{id}");
  }

  @Override
  protected boolean shouldNotFilter(HttpServletRequest request) {
    String uri = request.getRequestURI();
    return uri.startsWith("/actuator");
  }
}
