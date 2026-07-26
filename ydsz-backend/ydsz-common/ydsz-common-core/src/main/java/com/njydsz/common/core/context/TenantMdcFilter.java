package com.njydsz.common.core.context;

import jakarta.servlet.Filter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import org.slf4j.MDC;

import java.io.IOException;

/**
 * 租户 MDC 过滤器
 *
 * <p>在请求处理前将 tenantId 写入 MDC（Mapped Diagnostic Context），
 * 使得日志输出中自动包含租户标识，便于按租户维度排查问题。
 *
 * <p><b>使用方式：</b>
 * 在 WebSecurityConfiguration 或 WebMvcConfiguration 中注册此过滤器，
 * 优先级应高于业务过滤器（如 WebAuthFilter）。
 *
 * <p><b>日志配置示例（logback-spring.xml）：</b>
 * <pre>{@code
 * <pattern>%d{yyyy-MM-dd HH:mm:ss.SSS} [%thread] [%X{tenantId}] %-5level %logger{36} - %msg%n</pattern>
 * }</pre>
 *
 * @author ydsz-team
 * @since 1.0.0
 */
public class TenantMdcFilter implements Filter {

    /** MDC 键名：租户 ID */
    public static final String MDC_TENANT_ID = "tenantId";
    /** MDC 键名：用户 ID */
    public static final String MDC_USER_ID = "userId";
    /** MDC 键名：链路追踪 ID */
    public static final String MDC_TRACE_ID = "traceId";

    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
            throws IOException, ServletException {
        try {
            // 从 RequestContext 读取租户信息并写入 MDC
            String tenantId = RequestContext.getTenantId();
            if (tenantId != null) {
                MDC.put(MDC_TENANT_ID, tenantId);
            }

            String userId = RequestContext.getUserId();
            if (userId != null) {
                MDC.put(MDC_USER_ID, userId);
            }

            String traceId = RequestContext.getTraceId();
            if (traceId != null) {
                MDC.put(MDC_TRACE_ID, traceId);
            }

            chain.doFilter(request, response);
        } finally {
            // 请求结束后清理 MDC，防止线程复用导致数据串扰
            MDC.remove(MDC_TENANT_ID);
            MDC.remove(MDC_USER_ID);
            MDC.remove(MDC_TRACE_ID);
        }
    }
}
