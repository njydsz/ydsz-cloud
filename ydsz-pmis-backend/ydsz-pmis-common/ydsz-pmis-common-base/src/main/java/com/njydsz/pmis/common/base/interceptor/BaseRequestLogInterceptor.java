package com.njydsz.pmis.common.base.interceptor;

import com.njydsz.pmis.common.base.config.BaseTraceProperties;
import com.njydsz.pmis.common.util.StringUtils;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.springframework.lang.NonNull;
import org.springframework.lang.Nullable;
import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.web.servlet.ModelAndView;

import java.util.concurrent.ThreadLocalRandom;

/**
 * 请求日志拦截器（Web/App 共享）
 *
 * <p>子类覆盖 {@link #resolveRequestId(HttpServletRequest)} 提供不同的 ID 来源。
 * 覆盖 {@link #getLogger()} 提供不同的日志实例。
 *
 * @author ydsz-pmis-team
 * @since 1.0.0
 */
public abstract class BaseRequestLogInterceptor implements HandlerInterceptor {

    private static final String REQUEST_START_TIME = "requestStartTime";

    private final BaseTraceProperties traceProperties;

    /**
     * 构造请求日志拦截器
     *
     * @param traceProperties 追踪配置属性
     */
    protected BaseRequestLogInterceptor(BaseTraceProperties traceProperties) {
        this.traceProperties = traceProperties;
    }

    @Override
    public boolean preHandle(@NonNull HttpServletRequest request, @NonNull HttpServletResponse response, @NonNull Object handler) {
        if (!traceProperties.isRequestLogEnabled()) {
            return true;
        }

        if (ThreadLocalRandom.current().nextDouble() > traceProperties.getSamplingRate()) {
            return true;
        }

        long startTime = System.currentTimeMillis();
        request.setAttribute(REQUEST_START_TIME, startTime);

        String method = request.getMethod();
        String uri = request.getRequestURI();
        String queryString = request.getQueryString();
        String clientIp = getClientIp(request);
        String userAgent = request.getHeader("User-Agent");
        String requestId = resolveRequestId(request);

        String fullUri = !StringUtils.isEmpty(queryString) ? uri + "?" + queryString : uri;

        if ("INFO".equalsIgnoreCase(traceProperties.getLogLevel())) {
            getLogger().info("[TRACE] {} {} {} | ip={} | ua={}",
                    requestId, method, fullUri, clientIp, truncateUserAgent(userAgent));
        } else {
            getLogger().debug("[TRACE] {} {} {} | ip={} | ua={}",
                    requestId, method, fullUri, clientIp, truncateUserAgent(userAgent));
        }

        return true;
    }

    @Override
    public void postHandle(@NonNull HttpServletRequest request, @NonNull HttpServletResponse response,
                          @NonNull Object handler, @Nullable ModelAndView modelAndView) {
        if (!traceProperties.isRequestLogEnabled()) {
            return;
        }
        Long startTime = (Long) request.getAttribute(REQUEST_START_TIME);
        if (startTime != null) {
            long cost = System.currentTimeMillis() - startTime;
            String requestId = resolveRequestId(request);
            if ("INFO".equalsIgnoreCase(traceProperties.getLogLevel())) {
                getLogger().info("[TRACE] {} completed in {}ms", requestId, cost);
            } else {
                getLogger().debug("[TRACE] {} completed in {}ms", requestId, cost);
            }
        }
    }

    @Override
    public void afterCompletion(@NonNull HttpServletRequest request, @NonNull HttpServletResponse response,
                                @NonNull Object handler, @Nullable Exception ex) {
        if (!traceProperties.isRequestLogEnabled()) {
            return;
        }

        Long startTime = (Long) request.getAttribute(REQUEST_START_TIME);
        long cost = 0L;
        if (startTime != null) {
            cost = System.currentTimeMillis() - startTime;
        }

        String requestId = resolveRequestId(request);
        int status = response.getStatus();
        String uri = request.getRequestURI();

        if (ex != null) {
            getLogger().error("[TRACE] {} {} | status={} | time={}ms | ERROR: {}",
                    requestId, uri, status, cost, ex.getMessage());
        } else {
            if ("INFO".equalsIgnoreCase(traceProperties.getLogLevel())) {
                getLogger().info("[TRACE] {} {} | status={} | time={}ms",
                        requestId, uri, status, cost);
            } else {
                getLogger().debug("[TRACE] {} {} | status={} | time={}ms",
                        requestId, uri, status, cost);
            }
        }
    }

    /**
     * 子类覆盖此方法提供具体的请求 ID 解析逻辑
     *
     * @param request HTTP 请求
     * @return 请求 ID
     */
    protected abstract String resolveRequestId(HttpServletRequest request);

    /**
     * 子类覆盖此方法提供具体的日志实例
     *
     * @return 日志实例
     */
    protected abstract Logger getLogger();

    /**
     * 获取客户端 IP
     *
     * @param request HTTP 请求
     * @return 客户端 IP
     */
    protected String getClientIp(HttpServletRequest request) {
        String ip = request.getHeader("X-Forwarded-For");
        if (ip == null || ip.isEmpty() || "unknown".equalsIgnoreCase(ip)) {
            ip = request.getHeader("X-Real-IP");
        }
        if (ip == null || ip.isEmpty() || "unknown".equalsIgnoreCase(ip)) {
            ip = request.getHeader("Proxy-Client-IP");
        }
        if (ip == null || ip.isEmpty() || "unknown".equalsIgnoreCase(ip)) {
            ip = request.getRemoteAddr();
        }
        if (ip != null && ip.contains(",")) {
            ip = ip.split(",")[0].trim();
        }
        return ip != null ? ip : "";
    }

    /**
     * 截断 User-Agent
     *
     * @param userAgent User-Agent 字符串
     * @return 截断后的 User-Agent
     */
    protected String truncateUserAgent(String userAgent) {
        if (StringUtils.isEmpty(userAgent)) {
            return "-";
        }
        if (userAgent.length() > 100) {
            return userAgent.substring(0, 100) + "...";
        }
        return userAgent;
    }
}
