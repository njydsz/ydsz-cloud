package com.njydsz.pmis.common.base.interceptor;

import com.njydsz.pmis.common.base.config.BaseTraceProperties;
import com.njydsz.pmis.common.util.string.StringUtils;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;
import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.web.servlet.ModelAndView;

import java.util.concurrent.ThreadLocalRandom;
import com.njydsz.pmis.common.util.http.ServletUtils;

/**
 * 请求日志拦截器（Web/App 共享）
 *
 * <p>子类覆盖 {@link #resolveRequestId(HttpServletRequest)} 提供不同的 ID 来源，
 * 覆盖 {@link #getLogger()} 提供不同的日志实例。
 *
 * @author Marvin Lee
 * @email limw1888@126.com
 * @version 3.5.0
 */
public abstract class BaseRequestLogInterceptor implements HandlerInterceptor {

    private static final String REQUEST_START_TIME = "requestStartTime";

    private final BaseTraceProperties traceProperties;

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

        String fullUri = StringUtils.isNotBlank(queryString) ? uri + "?" + queryString : uri;

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
     */
    protected abstract String resolveRequestId(HttpServletRequest request);

    /**
     * 子类覆盖此方法提供具体的日志实例
     */
    protected abstract Logger getLogger();

    protected String getClientIp(HttpServletRequest request) {
        return ServletUtils.getClientIp(request);
    }

    protected String truncateUserAgent(String userAgent) {
        if (StringUtils.isBlank(userAgent)) {
            return "-";
        }
        if (userAgent.length() > 100) {
            return userAgent.substring(0, 100) + "...";
        }
        return userAgent;
    }
}
