package com.njydsz.common.tenant.datasource.resolver;

import java.util.Map;

import com.njydsz.common.tenant.config.TenantProperties;

/**
 * 基于配置文件的数据源 Key 解析器。
 *
 * <p>从 {@code ydsz.tenant.datasource.mapping} 配置中读取租户 ID → 数据源 Key 映射。
 *
 * <pre>
 * ydsz:
 *   tenant:
 *     datasource:
 *       mapping:
 *         acme: "tenant_acme"
 *         globex: "tenant_globex"
 *         initech: "ds_itech_primary"
 * </pre>
 *
 * <p>由 {@code TenantAutoConfiguration} 以
 * {@code @ConditionalOnProperty(prefix="ydsz.tenant.datasource", name="mapping")}
 * 条件注册为 Primary；未匹配时回退到 {@link NamingConventionResolver} 的命名约定。
 *
 * @author ydsz-team
 * @since 1.1.0
 * @see DatasourceKeyResolver
 */
public class ConfigurationResolver implements DatasourceKeyResolver {

    private final Map<String, String> mapping;
    private final NamingConventionResolver fallback;

    public ConfigurationResolver(TenantProperties properties,
                                  NamingConventionResolver fallback) {
        this.mapping = properties.getDatasourceMapping();
        this.fallback = fallback;
    }

    @Override
    public String resolve(String tenantId) {
        if (tenantId == null) {
            return null;
        }
        // 先查配置映射
        if (mapping != null) {
            String key = mapping.get(tenantId);
            if (key != null && !key.isEmpty()) {
                return key;
            }
        }
        // 回退到命名约定
        return fallback.resolve(tenantId);
    }

    @Override
    public boolean isAvailable(String tenantId) {
        if (tenantId == null) {
            return false;
        }
        return mapping != null && mapping.containsKey(tenantId);
    }
}
