package com.njydsz.pmis.common.app.filter;

import com.njydsz.pmis.common.app.config.AppTraceProperties;
import com.njydsz.pmis.common.app.util.RequestIdGenerator;
import com.njydsz.pmis.common.base.filter.BaseRequestIdResponseFilter;
import jakarta.servlet.http.HttpServletRequest;

/**
 * App 端请求 ID 响应头过滤器
 *
 * <p>继承 {@link BaseRequestIdResponseFilter}，将当前请求的 RequestId 写入响应头
 * {@code X-Request-Id}，便于客户端关联服务端日志。
 *
 * @author ydsz-pmis-team
 * @since 1.0.0
 */
public class AppRequestIdResponseFilter extends BaseRequestIdResponseFilter {

    public AppRequestIdResponseFilter(AppTraceProperties traceProperties) {
        super(traceProperties);
    }

    @Override
    protected String resolveRequestId(HttpServletRequest request) {
        String requestId = request.getHeader(HEADER_REQUEST_ID);
        if (requestId == null || requestId.isBlank()) {
            requestId = RequestIdGenerator.generateId();
        }
        return requestId;
    }
}
