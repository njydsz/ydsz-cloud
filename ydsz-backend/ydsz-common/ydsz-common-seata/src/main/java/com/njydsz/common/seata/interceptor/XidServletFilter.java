package com.njydsz.common.seata.interceptor;

import java.io.IOException;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import com.njydsz.common.seata.api.XidPropagator;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.springframework.web.filter.OncePerRequestFilter;

/**
 * XID 接收过滤器 - 下游服务接收 XID
 *
 * <p>在 HTTP 请求到达时，从请求头 {@code Seata-XID} 解析 XID 并绑定到当前线程，
 * 使下游服务可以接续全局事务。请求完成后自动清除线程绑定。
 *
 * <p><b>P0-6 修复</b>：此前 XID 仅 ThreadLocal 存储，跨服务 HTTP 调用时无法接收。
 *
 * @author ydsz-team
 * @since 1.0.0
 */
public class XidServletFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(XidServletFilter.class);

    private final XidPropagator xidPropagator;

    public XidServletFilter(XidPropagator xidPropagator) {
        this.xidPropagator = xidPropagator;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        String xidHeader = request.getHeader(XidPropagator.XID_HEADER);
        String xid = xidPropagator.deserialize(xidHeader);
        if (xid != null) {
            xidPropagator.bind(xid);
            log.debug("XID received and bound: {}", xid);
        }
        try {
            filterChain.doFilter(request, response);
        } finally {
            if (xid != null) {
                xidPropagator.unbind();
            }
        }
    }
}
