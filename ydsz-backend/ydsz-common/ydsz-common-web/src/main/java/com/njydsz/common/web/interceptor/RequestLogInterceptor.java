package com.njydsz.common.web.interceptor;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.slf4j.Logger;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;

import com.njydsz.common.base.interceptor.BaseRequestLogInterceptor;
import com.njydsz.common.util.id.TracerUtils;
import com.njydsz.common.web.config.WebTraceProperties;
import com.njydsz.common.web.metrics.WebMetrics;

import lombok.extern.slf4j.Slf4j;

/**
 * Web 端请求日志拦截器
 *
 * <p>继承 {@link BaseRequestLogInterceptor}，在请求进入时记录请求方法、路径、参数等信息，
 * 在请求结束时记录响应状态和耗时，便于问题排查和性能监控。
 * 同时将请求指标埋点到 {@link WebMetrics}（可选依赖）。
 *
 * @author ydsz-team
 * @see BaseRequestLogInterceptor
 * @see WebMetrics
 * @since 1.0.0
 */
@Slf4j
@Order(Ordered.HIGHEST_PRECEDENCE)
public class RequestLogInterceptor extends BaseRequestLogInterceptor {

    private static final String REQUEST_START_TIME_ATTR = "requestStartTimeNanos";

    private final WebMetrics webMetrics;

    public RequestLogInterceptor(WebTraceProperties traceProperties) {
        this(traceProperties, null);
    }

    public RequestLogInterceptor(WebTraceProperties traceProperties, WebMetrics webMetrics) {
        super(traceProperties);
        this.webMetrics = webMetrics;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        request.setAttribute(REQUEST_START_TIME_ATTR, System.nanoTime());
        return true;
    }

    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response,
                                Object handler, Exception ex) {
        Long startNanos = (Long) request.getAttribute(REQUEST_START_TIME_ATTR);
        if (startNanos != null) {
            long durationNanos = System.nanoTime() - startNanos;
            String method = request.getMethod();
            int status = response.getStatus();
            if (webMetrics != null) {
                webMetrics.recordRequest(method, status, durationNanos);
            }
        }
    }

    @Override
    public String resolveRequestId(HttpServletRequest request) {
        return TracerUtils.getTraceId();
    }

    @Override
    protected Logger getLogger() {
        return log;
    }
}
