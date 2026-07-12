package com.njydsz.pmis.common.base.filter;

import com.njydsz.pmis.common.context.RequestContext;
import com.njydsz.pmis.common.util.TraceIdUtil;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.MDC;
import org.springframework.lang.NonNull;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

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
 * @author ydsz-pmis-team
 * @since 1.0.0
 */
public class TraceFilter extends OncePerRequestFilter {

    private static final String TRACE_ID_HEADER = "X-Trace-Id";
    private static final String TRACE_ID_MDC_KEY = "traceId";

    @Override
    protected void doFilterInternal(@NonNull HttpServletRequest request,
                                    @NonNull HttpServletResponse response,
                                    @NonNull FilterChain filterChain) throws ServletException, IOException {
        try {
            String traceId = extractOrGenerateTraceId(request);
            MDC.put(TRACE_ID_MDC_KEY, traceId);
            RequestContext.setTraceId(traceId);
            response.setHeader(TRACE_ID_HEADER, traceId);
            filterChain.doFilter(request, response);
        } finally {
            MDC.remove(TRACE_ID_MDC_KEY);
        }
    }

    private String extractOrGenerateTraceId(HttpServletRequest request) {
        String traceId = request.getHeader(TRACE_ID_HEADER);
        if (traceId == null || traceId.isEmpty()) {
            traceId = TraceIdUtil.generate();
        }
        return traceId;
    }
}
