package com.njydsz.common.app.filter;

import jakarta.servlet.http.HttpServletRequest;

import com.njydsz.common.app.config.AppTraceProperties;
import com.njydsz.common.app.util.RequestIdGenerator;
import com.njydsz.common.base.filter.BaseRequestIdResponseFilter;
import com.njydsz.common.util.auth.RequestHolder;

/**
 * App 端请求 ID 响应头过滤器
 *
 * <p>继承 {@link BaseRequestIdResponseFilter}，将当前请求的 RequestId 写入响应头
 * {@code X-Request-Id}，便于客户端关联服务端日志。
 *
 * <p>优先复用鉴权阶段在 {@link RequestHolder} 中缓存的 RequestId，
 * 缺失时再调用 {@link RequestIdGenerator#generateId()} 兜底生成。
 *
 * <p><b>线程安全性：</b>无状态过滤器，线程安全。
 *
 * @author ydsz-team
 * @since 1.0.0
 */
public class AppRequestIdResponseFilter extends BaseRequestIdResponseFilter {

    /**
     * 构造方法
     *
     * @param traceProperties App 端 Trace 配置属性
     */
    public AppRequestIdResponseFilter(AppTraceProperties traceProperties) {
        super(traceProperties);
    }

    /**
     * 解析当前请求的 RequestId
     *
     * <p>优先从 {@link RequestHolder} 中获取上游过滤器写入的值，
     * 缺失时调用 {@link RequestIdGenerator#generateId()} 兜底生成。
     *
     * @param request 当前 HTTP 请求
     * @return 请求追踪 ID
     */
    @Override
    public String resolveRequestId(HttpServletRequest request) {
        String requestId = RequestHolder.getExtraHeader(HEADER_REQUEST_ID);
        if (requestId == null || requestId.isBlank()) {
            requestId = RequestIdGenerator.generateId();
        }
        return requestId;
    }
}
