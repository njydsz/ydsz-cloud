package com.njydsz.pmis.common.filter;

import com.njydsz.pmis.common.constant.CommonConstants;
import com.njydsz.pmis.common.util.TraceIdUtil;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.UUID;

/**
 * 链路追踪 ID 过滤器
 *
 * <p>从 Header 读取或生成 traceId，写入 MDC 与响应头。
 *
 * @author ydsz-pmis-team
 * @since 1.0.0
 */
@Slf4j
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class TraceIdFilter extends OncePerRequestFilter {

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        try {
            String traceId = request.getHeader(CommonConstants.HEADER_TRACE_ID);
            if (traceId == null || traceId.isEmpty()) {
                traceId = UUID.randomUUID().toString().replace("-", "").substring(0, 16);
            }
            TraceIdUtil.set(traceId);
            response.setHeader(CommonConstants.HEADER_TRACE_ID, traceId);
            chain.doFilter(request, response);
        } finally {
            TraceIdUtil.clear();
        }
    }
}
