package com.njydsz.common.tenant.datasource;

import java.io.IOException;

import com.njydsz.common.tenant.TenantContextHolder;

import jakarta.servlet.Filter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import lombok.extern.slf4j.Slf4j;

/**
 * ISOLATE_DB 模式 Web 过滤器。
 *
 * <p>在请求入口根据租户上下文切换数据源，请求结束后恢复默认数据源。
 *
 * <p>仅在 {@code ydsz.tenant.mode=ISOLATE_DB} 时生效。
 *
 * @author ydsz-team
 * @since 1.0.0
 */
@Slf4j
public class TenantDataSourceFilter implements Filter {

    private final TenantDataSourceRouter router;

    public TenantDataSourceFilter(TenantDataSourceRouter router) {
        this.router = router;
    }

    @Override
    public void doFilter(ServletRequest req, ServletResponse res, FilterChain chain)
            throws IOException, ServletException {
        if (router.isIsolateDbMode()) {
            String tenantId = TenantContextHolder.getTenantId();
            try {
                router.routeToTenantDataSource(tenantId);
                chain.doFilter(req, res);
            } finally {
                router.restoreDataSource();
            }
        } else {
            chain.doFilter(req, res);
        }
    }
}
