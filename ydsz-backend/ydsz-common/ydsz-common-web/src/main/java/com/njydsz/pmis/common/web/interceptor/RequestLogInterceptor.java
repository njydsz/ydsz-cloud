package com.njydsz.common.web.interceptor;

import jakarta.servlet.http.HttpServletRequest;

import org.slf4j.Logger;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import com.njydsz.common.base.interceptor.BaseRequestLogInterceptor;
import com.njydsz.common.util.id.TracerUtils;
import com.njydsz.common.web.config.WebTraceProperties;

import lombok.extern.slf4j.Slf4j;

/**
 * Web 端请求日志拦截器
 *
 * <p>继承 {@link BaseRequestLogInterceptor}，在请求进入时记录请求方法、路径、参数等信息，
 * 在请求结束时记录响应状态和耗时，便于问题排查和性能监控。
 *
 * <p><b>日志格式示例：</b>
 * <pre>
 * 【Web端】请求开始 | GET /api/users | 参数: page=1&size=10
 * 【Web端】请求结束 | GET /api/users | 状态: 200 | 耗时: 45ms
 * </pre>
 *
 * @author ydsz-team
 * @since 1.0.0
 * 
 * @see BaseRequestLogInterceptor
 */
@Slf4j
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class RequestLogInterceptor extends BaseRequestLogInterceptor {

    public RequestLogInterceptor(WebTraceProperties traceProperties) {
        super(traceProperties);
    }

    /**
     * 解析请求 ID（TraceId）
     *
     * @param request HTTP 请求
     * @return TraceId 字符串
     */
    @Override
    protected String resolveRequestId(HttpServletRequest request) {
        return TracerUtils.getTraceId();
    }

    /**
     * 获取日志记录器
     *
     * @return Logger 实例
     */
    @Override
    protected Logger getLogger() {
        return log;
    }
}
