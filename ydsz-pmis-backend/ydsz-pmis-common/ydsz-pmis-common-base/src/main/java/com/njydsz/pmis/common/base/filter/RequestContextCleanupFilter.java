package com.njydsz.pmis.common.base.filter;

import java.io.IOException;

import jakarta.servlet.Filter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;

import org.slf4j.MDC;

import com.njydsz.pmis.common.core.context.RequestContext;

/**
 * 请求上下文清理过滤器（Web/App 共享）
 *
 * <p>在请求结束时清理 {@link RequestContext} 和 {@link MDC}，防止线程泄漏。
 *
 * @author Marvin Lee
 * @email limw1888@126.com
 * @version 3.5.0
 */
public class RequestContextCleanupFilter implements Filter {

    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
            throws IOException, ServletException {
        try {
            chain.doFilter(request, response);
        } finally {
            RequestContext.clear();
            MDC.clear();
        }
    }
}
