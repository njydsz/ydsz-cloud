package com.njydsz.common.tenant.datasource.resolver;

/**
 * 基于命名约定的默认数据源 Key 解析器。
 *
 * <p>使用约定 {@code "tenant_" + tenantId} 作为数据源 Key。
 * 例如租户 "acme" 对应数据源 Bean：{@code tenant_acme}。
 *
 * <p>此解析器由 {@code TenantAutoConfiguration} 始终注册为 Spring Bean，作为兜底实现。
 * 当配置了租户映射（{@code ydsz.tenant.datasource.mapping}）时，
 * {@link ConfigurationResolver} 优先被调用，未匹配时回退到此解析器。
 *
 * @author ydsz-team
 * @since 1.1.0
 * @see DatasourceKeyResolver
 * @see ConfigurationResolver
 */
public class NamingConventionResolver implements DatasourceKeyResolver {

    static final String PREFIX = "tenant_";

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
