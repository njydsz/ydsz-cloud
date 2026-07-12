package com.njydsz.pmis.common.app.interceptor;

import com.njydsz.pmis.common.app.config.AppTraceProperties;
import com.njydsz.pmis.common.app.util.RequestIdGenerator;
import com.njydsz.pmis.common.base.interceptor.BaseRequestLogInterceptor;
import com.njydsz.pmis.common.core.constant.HeaderConstants;
import com.njydsz.pmis.common.util.auth.RequestHolder;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.Logger;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

/**
 * App 端请求日志拦截器
 *
 * <p>继承 {@link BaseRequestLogInterceptor}，打印 App 请求的入参、耗时、状态码等日志。
 * 与管理端 / Web 端共享拦截器逻辑，差异在于 RequestId 的获取来源（优先从
 * {@link RequestHolder} 中复用上游写入的 RequestId）。
 *
 * <p><b>线程安全性：</b>无状态拦截器，线程安全。
 *
 * @author Marvin Lee
 * @email limw1888@126.com
 * @version 3.5.0
 * @since 1.0.0
 */
@Slf4j
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class AppRequestLogInterceptor extends BaseRequestLogInterceptor {

    /** 请求追踪 ID 请求头名称 */
    private static final String REQUEST_ID_HEADER = HeaderConstants.X_REQUEST_ID;

    /**
     * 构造方法
     *
     * @param traceProperties App 端 Trace 配置属性
     */
    public AppRequestLogInterceptor(AppTraceProperties traceProperties) {
        super(traceProperties);
    }

    /**
     * 解析当前请求的 RequestId
     *
     * <p>优先从 {@link RequestHolder} 中获取上游过滤器写入的值，缺失时调用
     * {@link RequestIdGenerator#generateId()} 兜底生成。
     *
     * @param request 当前 HTTP 请求
     * @return 请求追踪 ID
     */
    @Override
    protected String resolveRequestId(HttpServletRequest request) {
        String requestId = RequestHolder.getExtraHeader(REQUEST_ID_HEADER);
        if (requestId == null || requestId.isBlank()) {
            requestId = RequestIdGenerator.generateId();
        }
        return requestId;
    }

    /**
     * 返回当前拦截器使用的日志器
     *
     * @return 拦截器持有的 {@link Logger} 实例
     */
    @Override
    protected Logger getLogger() {
        return log;
    }
}
