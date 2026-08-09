package com.njydsz.common.web.filter;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import com.njydsz.common.base.filter.BaseRequestIdResponseFilter;
import com.njydsz.common.util.id.TracerUtils;
import com.njydsz.common.web.config.WebTraceProperties;

/**
 * Web 端 Trace ID 响应头过滤器
 *
 * <p>继承 {@link BaseRequestIdResponseFilter}，在响应头中注入 TraceId，
 * 便于前端进行问题排查和日志关联。
 *
 * <p><b>工作流程：</b>
 * <ol>
 *   <li>从 {@link TracerUtils} 获取或生成 TraceId</li>
 *   <li>将 TraceId 写入响应头（{@code X-Trace-Id}）</li>
 *   <li>过滤器结束后清理 ThreadLocal 中的 TraceId</li>
 * </ol>
 *
 * @author ydsz-team
 * @see BaseRequestIdResponseFilter
 * @see TracerUtils
 * @since 1.0.0
 */
public class TraceIdResponseFilter extends BaseRequestIdResponseFilter {

    public TraceIdResponseFilter(WebTraceProperties traceProperties) {
        super(traceProperties);
    }

    @Override
    public String resolveRequestId(HttpServletRequest request) {
        return TracerUtils.getOrCreateTraceId();
    }

    @Override
    protected void afterFilter(HttpServletRequest request, HttpServletResponse response) {
        TracerUtils.clear();
    }
}
