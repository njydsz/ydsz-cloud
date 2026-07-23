package com.njydsz.common.base.filter;

import java.io.IOException;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.jspecify.annotations.NonNull;
import org.slf4j.MDC;
import org.springframework.web.filter.OncePerRequestFilter;

import com.njydsz.common.core.constant.TraceConstants;
import com.njydsz.common.core.context.RequestContext;
import com.njydsz.common.core.trace.TraceIdGenerator;

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
 * <p><b>安全校验：</b>
 * 对请求头中的 traceId 进行长度限制（最大 64 字符）和字符集校验
 * （仅允许 {@code [a-zA-Z0-9_-]}），防止日志注入和日志膨胀。
 *
 * <p>执行顺序：HIGH_PRECEDENCE + 10，确保在业务逻辑之前执行
 *
 * @author ydsz-team
 * @since 1.0.0
 */
public class TraceFilter extends OncePerRequestFilter {

    private static final String TRACE_ID_HEADER = TraceConstants.TRACE_ID_HEADER;
    private static final String TRACE_ID_MDC_KEY = TraceConstants.MDC_TRACE_ID_KEY;

    /** traceId 最大长度 */
    private static final int MAX_TRACE_ID_LENGTH = 64;

    /** traceId 合法字符正则（仅允许字母、数字、连字符、下划线） */
    private static final String TRACE_ID_PATTERN = "^[a-zA-Z0-9_-]+$";

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
     * <p>优先从请求头提取，但对传入的 traceId 进行安全校验：
     * <ul>
     *   <li>长度不超过 64 字符</li>
     *   <li>仅允许字母、数字、连字符、下划线</li>
     * </ul>
     * 校验失败时重新生成，防止日志注入和日志膨胀。
     *
     * @param request HTTP 请求
     * @return traceId
     */
    private String extractOrGenerateTraceId(HttpServletRequest request) {
        // 优先从请求头提取
        String traceId = request.getHeader(TRACE_ID_HEADER);

        // 安全校验：长度和字符集
        if (traceId == null
                || traceId.isEmpty()
                || traceId.length() > MAX_TRACE_ID_LENGTH
                || !traceId.matches(TRACE_ID_PATTERN)) {
            traceId = TraceIdGenerator.generate();
        }

        return traceId;
    }
}
