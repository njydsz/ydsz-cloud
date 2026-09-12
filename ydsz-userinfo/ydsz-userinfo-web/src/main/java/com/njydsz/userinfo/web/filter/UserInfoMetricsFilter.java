package com.njydsz.userinfo.web.filter;

import java.io.IOException;
import java.util.regex.Pattern;

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

import com.njydsz.common.util.id.TracerUtils;
import com.njydsz.userinfo.server.metrics.UserInfoMetrics;

/**
 * 用户中心 HTTP 请求耗时统计过滤器。
 *
 * <p>对所有经过 Controller 的请求记录耗时指标 {@code ydsz_userinfo_http_request_duration_ms}， 按 URI（归一化路径模板）和
 * HTTP 方法维度分组，便于在 Grafana 中查看接口 P50/P90/P99 延迟分布。
 *
 * <p><b>URI 归一化：</b>将 PathVariable（如 {@code /api/user/123}）归一化为模板路径 （如 {@code /api/user/{id}}），避免不同
 * ID 产生大量时间序列。
 *
 * <p><b>过滤器优先级：</b>设置为 {@link Ordered#LOWEST_PRECEDENCE} - 100，在认证过滤器之后执行， 确保只统计进入 Controller 的请求。
 *
 * <p><b>P1-2 整改（26.09.12）：</b>traceId 改由 ydsz-common-util 的 {@link TracerUtils} 提供，
 * 与 common-web 的 {@code TraceIdResponseFilter} 共用同一 ThreadLocal 上下文，消除自建
 * {@code TraceContext} 导致的"响应头 traceId 与指标 traceId 不一致"问题（见《云顶编码规范》
 * §33.2 链路追踪能力必须复用 common）。
 *
 * @author ydsz-team
 * @since 26.09.01
 */
@Slf4j
@Component
@Order(Ordered.LOWEST_PRECEDENCE - 100)
@RequiredArgsConstructor
public class UserInfoMetricsFilter extends OncePerRequestFilter {

  /** 数字 ID 路径段（URI 归一化用，预编译避免每次请求重复编译正则，符合规范 §21.1） */
  private static final Pattern NUMERIC_SEGMENT_PATTERN = Pattern.compile("/\\d+");

  /** 36 位 UUID 路径段（URI 归一化用，预编译避免每次请求重复编译正则，符合规范 §21.1） */
  private static final Pattern UUID_SEGMENT_PATTERN = Pattern.compile("/[0-9a-fA-F-]{36}");

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
      String traceId = TracerUtils.getTraceId();
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
   * 归一化 URI：将数字 ID 与 UUID 替换为 {id}，避免高基数时间序列。
   *
   * @param uri 原始 URI
   * @return 归一化后的 URI；入参为 null 时返回 {@code unknown}
   */
  private String normalizeUri(String uri) {
    if (uri == null) {
      return "unknown";
    }
    String normalized = NUMERIC_SEGMENT_PATTERN.matcher(uri).replaceAll("/{id}");
    return UUID_SEGMENT_PATTERN.matcher(normalized).replaceAll("/{id}");
  }

  @Override
  protected boolean shouldNotFilter(HttpServletRequest request) {
    String uri = request.getRequestURI();
    return uri.startsWith("/actuator");
  }
}
