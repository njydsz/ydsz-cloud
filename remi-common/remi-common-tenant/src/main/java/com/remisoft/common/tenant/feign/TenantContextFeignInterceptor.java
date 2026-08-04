package com.remisoft.common.tenant.feign;

import java.util.List;
import java.util.Map;

import com.remisoft.common.tenant.TenantContext;
import com.remisoft.common.tenant.TenantContextHolder;

import feign.RequestInterceptor;
import feign.RequestTemplate;
import lombok.extern.slf4j.Slf4j;

/**
 * Feign 请求拦截器：跨服务透传全部租户字段。
 *
 * <p>将 {@link TenantContext} 中的所有字段透传为 HTTP header，
 * 下游服务的 {@code TenantContextWebFilter} 从 header 恢复全部字段。
 *
 * <p>透传规则：
 * <ul>
 *   <li>单值字段 → header 值为 String</li>
 *   <li>多值字段 → header 值为逗号分隔 String（如 "dept_001,dept_002"）</li>
 * </ul>
 *
 * @author remi-team
 * @since 1.0.0
 */
@Slf4j
public class TenantContextFeignInterceptor implements RequestInterceptor {

    private static final String HEADER_PREFIX = "X-Tenant-";
    private static final String HEADER_TENANT_ID = "X-Tenant-Id";

    @Override
    public void apply(RequestTemplate template) {
        TenantContext context = TenantContextHolder.get();
        if (context == null || context.isSkipIsolation() || context.getTenantId() == null) {
            return;
        }

        // 注入主租户 ID
        template.header(HEADER_TENANT_ID, context.getTenantId());

        // 透传全部字段
        Map<String, Object> fields = context.getFields();
        if (fields != null && !fields.isEmpty()) {
            for (Map.Entry<String, Object> entry : fields.entrySet()) {
                String key = entry.getKey();
                // 跳过 tenantId（已作为 X-Tenant-Id 透传）
                if ("tenantId".equals(key)) {
                    continue;
                }
                Object value = entry.getValue();
                String headerName = HEADER_PREFIX + key;

                if (value instanceof String s) {
                    template.header(headerName, s);
                } else if (value instanceof List<?> list) {
                    // 多值 → 逗号分隔
                    StringBuilder sb = new StringBuilder();
                    for (int i = 0; i < list.size(); i++) {
                        if (i > 0) {
                            sb.append(",");
                        }
                        sb.append(list.get(i));
                    }
                    if (!sb.isEmpty()) {
                        template.header(headerName, sb.toString());
                    }
                }
            }
        }
    }
}
