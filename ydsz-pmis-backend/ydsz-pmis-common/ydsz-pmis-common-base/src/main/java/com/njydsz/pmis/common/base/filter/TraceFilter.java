package com.njydsz.pmis.common.base.filter;

import java.io.IOException;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.jspecify.annotations.NonNull;
import org.slf4j.MDC;
import org.springframework.web.filter.OncePerRequestFilter;

import com.njydsz.pmis.common.core.context.RequestContext;
import com.njydsz.pmis.common.core.trace.TraceIdGenerator;

/**
 * 链路追踪过滤器
 *
 * <p>功能说明：
 * <ul>
 *   <li>生成或提取 traceId</li>
 *   <li>将 traceId 注入 MDC，供日志框架使用</li>
 *   <li>将 traceId 存入 RequestContext</li>
 *   <li>在响应头中返回 traceId</li>
 * </ul>
 *
 * <p>执行顺序：HIGH_PRECEDENCE + 10，确保在业务逻辑之前执行
 *
 * @author ydsz-pmis-team
 * @since 1.0.0
 * 
 */
public class TraceFilter extends OncePerRequestFilter {

    private static final String TRACE_ID_HEADER = "X-Trace-Id";
    private static final String TRACE_ID_MDC_KEY = "traceId";

    @Override
    protected void doFilterInternal(@NonNull HttpServletRequest request,
                                    @NonNull HttpServletResponse response,
                                    @NonNull FilterChain filterChain) throws ServletException, IOException {
        try {
            // 提取或生成 traceId
            String traceId = extractOrGenerateTraceId(request);

            // 注入 MDC
            MDC.put(TRACE_ID_MDC_KEY, traceId);

            // 存入 RequestContext
            RequestContext.setTraceId(traceId);

            // 设置响应头
            response.setHeader(TRACE_ID_HEADER, traceId);

            // 继续处理
            filterChain.doFilter(request, response);
        } finally {
            // 清理 MDC（由 RequestContextCleanupFilter 统一清理）
            MDC.remove(TRACE_ID_MDC_KEY);
        }
    }

    /**
     * 提取或生成 traceId
     *
     * @param request HTTP 请求
     * @return traceId
     */
    private String extractOrGenerateTraceId(HttpServletRequest request) {
        // 优先从请求头提取
        String traceId = request.getHeader(TRACE_ID_HEADER);

        // 如果请求头中没有，则生成新的
        if (traceId == null || traceId.isEmpty()) {
            traceId = TraceIdGenerator.generate();
        }

        return traceId;
    }
}
