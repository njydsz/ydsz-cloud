package com.njydsz.common.tenant.datasource.resolver;

import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.stereotype.Component;

/**
 * 基于命名约定的默认数据源 Key 解析器。
 *
 * <p>使用约定 {@code "tenant_" + tenantId} 作为数据源 Key。
 * 例如租户 "acme" 对应数据源 Bean：{@code tenant_acme}。
 *
 * <p>此解析器作为默认实现，当业务模块未自定义时启用。
 *
 * @author ydsz-team
 * @since 1.1.0
 * @see DatasourceKeyResolver
 */
@Component
@ConditionalOnMissingBean(DatasourceKeyResolver.class)
public class NamingConventionResolver implements DatasourceKeyResolver {

    private static final String PREFIX = "tenant_";

    @Override
    public String resolve(String tenantId) {
        if (tenantId == null || tenantId.isEmpty()) {
            return null;
        }
        return PREFIX + tenantId;
    }

    @Override
    public boolean isAvailable(String tenantId) {
        return tenantId != null && !tenantId.isEmpty();
    }
}
