package com.njydsz.pmis.common.exception.observability;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.lang.NonNull;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * TraceId 注入过滤器
 *
 * <p>在请求入口处自动从 header 提取或生成 traceId，写入 MDC 和响应 header。
 * 配合 logback 的 {@code %X{traceId}} 配置可在所有日志中自动携带 traceId。
 *
 * <p><b>处理流程：</b>
 * <ol>
 *   <li>从请求 header 提取 traceId（{@code X-Trace-Id} / {@code X-B3-TraceId}）</li>
 *   <li>若未提取到则生成新的 traceId</li>
 *   <li>写入 SLF4J MDC，注入响应 header</li>
 *   <li>请求结束后清理 MDC（线程池复用）</li>
 * </ol>
 *
 * <p><b>激活条件：</b>需在 {@code AutoConfiguration.imports} 中注册，
 * 或在业务系统中通过 {@code @Component} 引入。
 *
 * @author Marvin Lee
 * @email limw1888@126.com
 * @version 3.5.0
 * @since 3.0.0
 */
public class TraceContextFilter extends OncePerRequestFilter {

    @Override
    protected void doFilterInternal(@NonNull HttpServletRequest request,
                                    @NonNull HttpServletResponse response,
                                    @NonNull FilterChain filterChain) throws ServletException, IOException {
        String existing = request.getHeader(TraceContext.HEADER_TRACE_ID);
        if (existing == null || existing.isEmpty()) {
            existing = request.getHeader(TraceContext.HEADER_B3_TRACE_ID);
        }
        String traceId = TraceContext.extractOrGenerate(existing);
        String spanId = TraceContext.generate();

        TraceContext.setContext(traceId, spanId);
        response.setHeader(TraceContext.HEADER_TRACE_ID, traceId);
        try {
            filterChain.doFilter(request, response);
        } finally {
            TraceContext.clear();
        }
    }
}
