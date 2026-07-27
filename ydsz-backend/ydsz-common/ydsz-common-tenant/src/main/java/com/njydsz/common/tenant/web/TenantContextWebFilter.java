package com.njydsz.common.tenant.web;

import java.io.IOException;
import java.util.Set;

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
 * <p>在请求入口从 JWT 认证信息解析租户 ID，设置到 {@link TenantContextHolder}。
 * <p>执行顺序应在认证 Filter 之后，业务 Filter 之前。
 *
 * @author ydsz-team
 * @since 1.0.0
 */
@Slf4j
public class TenantContextWebFilter implements Filter {

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

            // 2. 从 JWT 认证信息解析租户 ID
            String tenantId = AuthInfoUtils.getTenantId();

            if (tenantId != null && !tenantId.isEmpty()) {
                // 判断是否超级管理员
                boolean isSuperAdmin = properties.getSuperTenantId().equals(tenantId);
                TenantContext context = TenantContext.builder(tenantId)
                        .superAdmin(isSuperAdmin)
                        .build();
                TenantContextHolder.set(context);
            }
            // 3. 无认证无跳过 → 不设置上下文
            //    后续 SQL 拦截器会 fail-closed 抛异常

            chain.doFilter(req, res);
        } finally {
            TenantContextHolder.clear();
        }
    }

    /**
     * 判断请求 URL 是否为匿名 URL。
     *
     * @param requestUri 请求 URI
     * @return true=匿名 URL
     */
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
