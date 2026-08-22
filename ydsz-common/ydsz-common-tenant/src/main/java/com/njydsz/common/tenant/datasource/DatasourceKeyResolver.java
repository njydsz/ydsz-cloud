package com.njydsz.common.tenant.datasource;

import java.util.Map;

import com.njydsz.common.tenant.config.TenantProperties;

/**
 * 租户数据源 Key 解析器 SPI。
 *
 * <p>在 ISOLATE_DB 模式下，根据租户 ID 解析出对应的 Spring 数据源 Bean 名称（或 lookup key）。
 *
 * <p><b>内置实现：</b>{@link SimpleDatasourceKeyResolver} 支持配置映射 + 命名约定回退。
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
 * @since 1.0.0
 * @see TenantDataSourceRouter
 */
public interface DatasourceKeyResolver {

  /**
   * 根据租户 ID 解析数据源 Key。
   *
   * <p>内置实现逻辑：
   *
   * <ol>
   *   <li>优先从 {@code ydsz.tenant.datasource.mapping} 配置读取映射
   *   <li>未命中时使用命名约定 {@code "tenant_" + tenantId}
   * </ol>
   *
   * @param tenantId 租户 ID
   * @return 数据源 Key（在 DynamicRoutingDataSource 中的注册名称）， 返回 null 表示使用默认数据源
   */
  String resolve(String tenantId);

  /**
   * 验证该租户的数据源是否可用。
   *
   * <p>用于健康检查和启动预检。默认实现委托 {@link #resolve(String)}。
   *
   * @param tenantId 租户 ID
   * @return true=数据源可用，false=不可用
   */
  default boolean isAvailable(String tenantId) {
    return resolve(tenantId) != null;
  }

  /**
   * 创建默认解析器实例（配置映射 + 命名约定回退）。
   *
   * <p>供内部使用，业务模块无需调用此方法。
   *
   * @param properties 租户配置
   * @return 默认解析器
   * @since 1.0.0
   */
  static DatasourceKeyResolver createDefault(TenantProperties properties) {
    return new SimpleDatasourceKeyResolver(properties);
  }

  /**
   * 简单的默认解析器实现。
   *
   * <p>包级私有，仅供 {@link #createDefault} 使用。
   *
   * @since 1.0.0
   */
  class SimpleDatasourceKeyResolver implements DatasourceKeyResolver {

    private static final String PREFIX = "tenant_";

    private final Map<String, String> mapping;

    SimpleDatasourceKeyResolver(TenantProperties properties) {
      this.mapping = properties.getDatasourceMapping();
    }

    @Override
    public String resolve(String tenantId) {
      if (tenantId == null || tenantId.isEmpty()) {
        return null;
      }

      // 优先从配置映射读取
      if (mapping != null && !mapping.isEmpty()) {
        String key = mapping.get(tenantId);
        if (key != null && !key.isEmpty()) {
          return key;
        }
      }

      // 命名约定回退
      return PREFIX + tenantId;
    }
  }
}
