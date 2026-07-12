package com.njydsz.pmis.common.base.filter;

import com.njydsz.pmis.common.base.config.BaseTraceProperties;
import com.njydsz.pmis.common.core.constant.HeaderConstants;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.lang.NonNull;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * 请求ID响应头过滤器（Web/App 共享）
 *
 * <p>子类覆盖 {@link #resolveRequestId(HttpServletRequest)} 提供不同的 ID 来源。
 *
 * @author Marvin Lee
 * @email limw1888@126.com
 * @version 3.5.0
 */
@Slf4j
public abstract class BaseRequestIdResponseFilter extends OncePerRequestFilter {

    protected static final String HEADER_REQUEST_ID = HeaderConstants.X_REQUEST_ID;

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
     */
    protected abstract String resolveRequestId(HttpServletRequest request);

    /**
     * 请求结束后清理（默认空实现，子类可覆盖）
     */
    protected void afterFilter(HttpServletRequest request, HttpServletResponse response) {
        // 默认空实现
    }
}
