package com.njydsz.common.tenant.web;

import java.io.IOException;
import java.util.Set;

import org.slf4j.MDC;

import com.njydsz.common.tenant.TenantContext;
import com.njydsz.common.tenant.TenantContextHolder;
import com.njydsz.common.tenant.config.TenantProperties;
import com.njydsz.common.util.auth.AuthInfoUtils;

import jakarta.servlet.Filter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;

/**
 * 租户上下文 Web 过滤器。
 *
 * <p>在请求入口从 JWT 认证信息解析租户 ID，设置到 {@link TenantContextHolder}
 * 和 MDC 日志上下文。
 *
 * <p><b>解析优先级：</b>
 * <ol>
 *   <li>JWT 认证上下文（AuthInfoUtils.getTenantId()，不可伪造）</li>
 *   <li>Feign 透传 Header（X-Tenant-Id，仅当上游为内部服务时可信）</li>
 * </ol>
 *
 * <p><b>安全清洗：</b>外部请求的 X-Tenant-Id header 在网关层已被清洗，
 * 此处仅当 AuthInfoUtils 不可用时才从 header 恢复（Feign 内部调用场景）。
 *
 * <p>通过 {@code FilterRegistrationBean} 注册，order 设为
 * {@code Ordered.HIGHEST_PRECEDENCE + 100}（在认证 Filter 之后、业务 Filter 之前）。
 *
 * @author ydsz-team
 * @since 1.0.0
 */
@Slf4j
public class TenantContextWebFilter implements Filter {

    private static final String MDC_TENANT_ID = "tenantId";
    private static final String HEADER_TENANT_ID = "X-Tenant-Id";

    private final TenantProperties properties;
    private final Set<String> anonUrls;

    public TenantContextWebFilter(TenantProperties properties) {
        this.properties = properties;
        this.anonUrls = properties.getNormalizedAnonUrls();
    }

    @Override
    public void doFilter(ServletRequest req, ServletResponse res, FilterChain chain)
            throws IOException, ServletException {
        HttpServletRequest request = (HttpServletRequest) req;
        try {
            String requestUri = request.getRequestURI();

            // 1. 匿名 URL → 跳过隔离
            if (isAnonUrl(requestUri)) {
                TenantContextHolder.set(TenantContext.skip());
                chain.doFilter(req, res);
                return;
            }

            // 2. 优先从 JWT 认证信息解析租户 ID（不可伪造）
            String tenantId = AuthInfoUtils.getTenantId();

            // 3. JWT 不可用时，从 Feign 透传 header 恢复（仅内部调用可信）
            if (tenantId == null || tenantId.isEmpty()) {
                String headerTenantId = request.getHeader(HEADER_TENANT_ID);
                if (headerTenantId != null && !headerTenantId.isEmpty()) {
                    tenantId = headerTenantId;
                    log.debug("从 X-Tenant-Id header 恢复租户上下文: {}", tenantId);
                }
            }

            if (tenantId != null && !tenantId.isEmpty()) {
                boolean isSuperAdmin = properties.getSuperTenantId().equals(tenantId);
                TenantContext context = TenantContext.builder(tenantId)
                        .superAdmin(isSuperAdmin)
                        .build();
                TenantContextHolder.set(context);
                // 注入 MDC 日志上下文，确保所有日志/链路追踪携带租户维度
                MDC.put(MDC_TENANT_ID, tenantId);
            }
            // 无认证无跳过 → 不设置上下文
            // 后续 SQL 拦截器会 fail-closed 抛异常

            chain.doFilter(req, res);
        } finally {
            TenantContextHolder.clear();
            MDC.remove(MDC_TENANT_ID);
        }
    }

    private boolean isAnonUrl(String requestUri) {
        if (requestUri == null || anonUrls.isEmpty()) {
            return false;
        }
        for (String anonUrl : anonUrls) {
            if (requestUri.startsWith(anonUrl)) {
                return true;
            }
        }
        return false;
    }
}
