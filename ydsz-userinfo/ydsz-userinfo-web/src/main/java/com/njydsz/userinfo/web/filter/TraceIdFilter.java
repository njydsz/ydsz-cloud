package com.njydsz.userinfo.web.filter;

import java.io.IOException;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import com.njydsz.userinfo.server.trace.TraceContext;

/**
 * 链路追踪 ID 过滤器（P1-10）。
 *
 * <p>在请求入口处：从请求头 {@code X-Trace-Id} 读取上游 traceId（网关/上游服务传入），
 * 不存在则本地生成 UUID；写入 {@link TraceContext}（同步 SLF4J MDC），
 * 并在响应头 {@code X-Trace-Id} 回写，便于全链路关联与问题定位。
 *
 * <p><b>优先级：</b>{@link Ordered#HIGHEST_PRECEDENCE} + 20，在业务过滤器之前执行，
 * 确保日志/指标/审计均可引用 traceId。
 *
 * @author ydsz-team
 * @since 26.09.01
 * @see TraceContext 链路上下文
 */
@Slf4j
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 20)
public class TraceIdFilter extends OncePerRequestFilter {

  @Override
  protected void doFilterInternal(
      HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
      throws ServletException, IOException {
    try {
      // 从请求头读取上游 traceId，不存在则生成
      String incoming = request.getHeader(TraceContext.TRACE_ID_HEADER);
      String traceId = TraceContext.getOrGenerate(incoming);
      TraceContext.setTraceId(traceId);
      // 响应头回写，便于客户端/下游关联
      response.setHeader(TraceContext.TRACE_ID_HEADER, traceId);
      filterChain.doFilter(request, response);
    } finally {
      TraceContext.clear();
    }
  }

  @Override
  protected boolean shouldNotFilter(HttpServletRequest request) {
    // 排除 actuator 探活路径，避免污染链路与产生无效 traceId
    String uri = request.getRequestURI();
    return uri != null && uri.startsWith("/actuator");
  }
}
