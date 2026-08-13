package com.njydsz.common.tenant.datasource.resolver;

/**
 * 租户数据源 Key 解析器 SPI。
 *
 * <p>在 ISOLATE_DB 模式下，根据租户 ID 解析出对应的 Spring 数据源 Bean 名称（或 lookup key）。
 *
 * <p><b>内置实现：</b>
 * <ul>
 *   <li>{@link NamingConventionResolver} — 默认实现，使用 {@code "tenant_" + tenantId} 约定</li>
 *   <li>{@link ConfigurationResolver} — 从配置文件中读取映射关系</li>
 * </ul>
 *
 * <p><b>扩展方式：</b>业务模块可实现此接口并注册为 {@code @Primary} Bean 以覆盖默认行为。
 *
 * <pre>{@code
 * @Component
 * @Primary
 * public class CustomDatasourceResolver implements DatasourceKeyResolver {
 *     \@Override
 *     public String resolve(String tenantId) {
 *         // 从 ydsz_tenant 表查询 datasource_key
 *         return tenantDatasourceMapper.findKeyByTenantId(tenantId);
 *     }
 * }
 * }</pre>
 *
 * @author ydsz-team
 * @since 1.1.0
 * @see com.njydsz.common.tenant.datasource.TenantDataSourceRouter
 */
public interface DatasourceKeyResolver {

    /**
     * 根据租户 ID 解析数据源 Key。
     *
     * @param tenantId 租户 ID
     * @return 数据源 Key（在 DynamicRoutingDataSource 中的注册名称），
     *         返回 null 表示使用默认数据源
     */
    String resolve(String tenantId);

    /**
     * 验证该租户的数据源是否可用。
     *
     * <p>用于健康检查和启动预检。
     *
     * @param tenantId 租户 ID
     * @return true=数据源可用，false=不可用
     */
    default boolean isAvailable(String tenantId) {
        return resolve(tenantId) != null;
    }
}
