package com.njydsz.common.tenant.feign;

import java.util.Map;

import com.njydsz.common.tenant.TenantContext;
import com.njydsz.common.tenant.TenantContextHolder;
import com.njydsz.common.tenant.TenantDimension;

import feign.RequestInterceptor;
import feign.RequestTemplate;
import lombok.extern.slf4j.Slf4j;

/**
 * Feign 请求拦截器：跨服务透传租户上下文。
 *
 * <p>在所有 Feign 调用的请求头中注入 X-Tenant-Id，
 * 下游服务的 {@code TenantContextWebFilter} 从 header 恢复上下文。
 *
 * <p>MULTI 模式下还会透传多级维度（X-Tenant-GROUP / X-Tenant-COMPANY）。
 *
 * @author ydsz-team
 * @since 1.0.0
 */
@Slf4j
public class TenantContextFeignInterceptor implements RequestInterceptor {

    private static final String HEADER_TENANT_ID = "X-Tenant-Id";
    private static final String HEADER_TENANT_PREFIX = "X-Tenant-";

    @Override
    public void apply(RequestTemplate template) {
        TenantContext context = TenantContextHolder.get();
        if (context == null || context.isSkipIsolation() || context.getTenantId() == null) {
            return;
        }

        // 注入主租户 ID
        template.header(HEADER_TENANT_ID, context.getTenantId());

        // MULTI 模式：透传多级维度
        Map<TenantDimension, String> dimensions = context.getDimensions();
        if (dimensions != null && !dimensions.isEmpty()) {
            for (Map.Entry<TenantDimension, String> entry : dimensions.entrySet()) {
                template.header(HEADER_TENANT_PREFIX + entry.getKey().name(),
                        entry.getValue());
            }
        }
    }
}
