package com.njydsz.common.queue.trace;

import java.io.IOException;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import com.njydsz.common.core.constant.HeaderConstants;
import com.njydsz.common.util.id.TracerUtils;
import com.njydsz.common.util.string.StringUtils;

import lombok.extern.slf4j.Slf4j;

/**
 * 消息队列链路追踪过滤器
 *
 * <p>从 HTTP 请求头中提取 traceId 并注入到当前线程的 MDC 与 RequestContext，
 * 确保 REST API 调用消息发布者时，traceId 能够全链路传递。
 *
 * <p><b>支持的请求头：</b>
 * <ul>
 *   <li>{@code traceparent} - W3C TraceContext 标准头（优先）</li>
 *   <li>{@code X-Trace-Id} - 自定义追踪ID头（兼容旧系统）</li>
 * </ul>
 *
 * <p><b>工作流程：</b>
 * <ol>
 *   <li>从请求头解析 traceId</li>
 *   <li>注入到 MDC 和 RequestContext</li>
 *   <li>执行后续过滤链</li>
 *   <li>请求结束后清理 MDC 中的 traceId</li>
 * </ol>
 *
 * @author ydsz-team
 * @since 1.0.0
 */
@Slf4j
public class MessageTraceFilter implements jakarta.servlet.Filter {

    /**
     * W3C traceparent 头名称
     */
    private static final String HEADER_TRACEPARENT = "traceparent";

    @Override
    public void doFilter(jakarta.servlet.ServletRequest request,
                         jakarta.servlet.ServletResponse response,
                         FilterChain chain) throws IOException, ServletException {
        HttpServletRequest httpRequest = (HttpServletRequest) request;
        HttpServletResponse httpResponse = (HttpServletResponse) response;

        try {
            // 从请求头提取 traceId 并注入上下文
            extractAndInjectTraceId(httpRequest);

            // 将 traceId 写入响应头，便于前端排查
            String traceId = TracerUtils.getTraceId();
            if (StringUtils.isNotEmpty(traceId)) {
                httpResponse.setHeader(HeaderConstants.TRACE_ID_HEADER, traceId);
            }

            chain.doFilter(request, response);
        } finally {
            // 清理 MDC 中的 traceId，避免线程复用污染
            TracerUtils.clear();
        }
    }

    /**
     * 从请求头提取 traceId 并注入到 MDC 和 RequestContext
     *
     * <p>优先使用 W3C traceparent 头，其次使用 X-Trace-Id 头。
     * 如果都不存在，则自动生成新的 traceId。
     *
     * @param request HTTP 请求
     */
    private void extractAndInjectTraceId(HttpServletRequest request) {
        // 优先尝试 W3C traceparent 头
        String traceparent = request.getHeader(HEADER_TRACEPARENT);
        if (StringUtils.isNotEmpty(traceparent)) {
            if (TracerUtils.injectTraceparent(traceparent)) {
                log.debug("[MessageTrace] 从 W3C traceparent 注入 traceId: {}", TracerUtils.getTraceId());
                return;
            }
            log.debug("[MessageTrace] W3C traceparent 格式非法: {}", traceparent);
        }

        // 其次尝试 X-Trace-Id 头
        String traceId = request.getHeader(HeaderConstants.TRACE_ID_HEADER);
        if (StringUtils.isNotEmpty(traceId)) {
            TracerUtils.setTraceId(traceId);
            log.debug("[MessageTrace] 从 X-Trace-Id 注入 traceId: {}", traceId);
            return;
        }

        // 无请求头时自动生成
        traceId = TracerUtils.getOrCreateTraceId();
        log.debug("[MessageTrace] 自动生成 traceId: {}", traceId);
    }
}
