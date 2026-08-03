package com.njydsz.common.base.filter;

import java.io.IOException;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.jspecify.annotations.NonNull;
import org.springframework.web.filter.OncePerRequestFilter;

import com.njydsz.common.base.config.BaseTraceProperties;
import com.njydsz.common.base.interceptor.RequestIdResolver;
import com.njydsz.common.core.constant.HeaderConstants;

import lombok.extern.slf4j.Slf4j;

/**
 * 请求ID响应头过滤器（Web/App 共享）
 *
 * <p>子类覆盖 {@link #resolveRequestId(HttpServletRequest)} 提供不同的 ID 来源。
 *
 * @author ydsz-team
 * @since 1.0.0
 */
@Slf4j
public abstract class BaseRequestIdResponseFilter extends OncePerRequestFilter implements RequestIdResolver {

    protected static final String HEADER_REQUEST_ID = HeaderConstants.TRACE_ID_HEADER;

    private final BaseTraceProperties traceProperties;

    protected BaseRequestIdResponseFilter(BaseTraceProperties traceProperties) {
        this.traceProperties = traceProperties;
    }

    @Override
    protected void doFilterInternal(@NonNull HttpServletRequest request,
                                     @NonNull HttpServletResponse response,
                                     @NonNull FilterChain filterChain) throws ServletException, IOException {
        try {
            if (traceProperties.isResponseHeaderEnabled()) {
                String requestId = resolveRequestId(request);
                if (requestId != null && !requestId.isBlank()) {
                    response.setHeader(HEADER_REQUEST_ID, requestId);
                    log.debug("请求ID [{}] 已添加到请求 {} 的响应头中", requestId, request.getRequestURI());
                }
            }
            filterChain.doFilter(request, response);
        } finally {
            afterFilter(request, response);
        }
    }

    /**
     * 子类覆盖此方法提供具体的请求 ID 解析逻辑
     *
     * @param request HTTP 请求
     * @return 请求 ID
     */
    @Override
    public abstract String resolveRequestId(HttpServletRequest request);

    /**
     * 请求结束后清理（默认空实现，子类可覆盖）
     */
    protected void afterFilter(HttpServletRequest request, HttpServletResponse response) {
        // 默认空实现
    }
}
