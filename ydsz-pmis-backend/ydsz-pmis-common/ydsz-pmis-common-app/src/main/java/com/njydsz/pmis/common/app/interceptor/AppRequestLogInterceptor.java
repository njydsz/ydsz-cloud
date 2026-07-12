package com.njydsz.pmis.common.app.interceptor;

import com.njydsz.pmis.common.app.config.AppTraceProperties;
import com.njydsz.pmis.common.app.util.RequestIdGenerator;
import com.njydsz.pmis.common.base.interceptor.BaseRequestLogInterceptor;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * App 端请求日志拦截器
 *
 * <p>继承 {@link BaseRequestLogInterceptor}，使用 App 端的 RequestId 生成策略。
 *
 * @author ydsz-pmis-team
 * @since 1.0.0
 */
@Slf4j
@Component
public class AppRequestLogInterceptor extends BaseRequestLogInterceptor {

    /**
     * 构造 App 端请求日志拦截器
     *
     * @param traceProperties App 端追踪配置属性
     */
    public AppRequestLogInterceptor(AppTraceProperties traceProperties) {
        super(traceProperties);
    }

    @Override
    protected String resolveRequestId(HttpServletRequest request) {
        String requestId = request.getHeader("X-Request-Id");
        if (requestId == null || requestId.isBlank()) {
            requestId = RequestIdGenerator.generateId();
        }
        return requestId;
    }

    @Override
    protected org.slf4j.Logger getLogger() {
        return log;
    }
}
