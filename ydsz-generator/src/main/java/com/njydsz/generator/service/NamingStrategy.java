package com.njydsz.generator.service;

import com.njydsz.generator.config.GeneratorProperties.ModuleGroupConfig;
import com.njydsz.generator.model.TableMetadata;

/**
 * 代码生成器命名策略接口。
 *
 * <p>允许用户自定义各类名称的生成规则（实体类名、Repository、Service、Controller 等）。
 *
 * <p>默认实现 {@link DefaultNamingStrategy} 遵循云顶编码规范（YDIZ-NAME-001/002）。
 *
 * <p><b>SPI 扩展方式：</b>在 {@code ydsz.generator.naming-strategy} 配置中指定实现类全名，
 * 或在 {@code META-INF/services/com.njydsz.generator.service.NamingStrategy} 中注册。
 *
 * @author ydsz-team
 * @since 26.09.04
 * @see DefaultNamingStrategy
 */
public interface NamingStrategy {

  /**
   * 将原始表名转为实体类名（PascalCase）。
   *
   * <p>规则示例：{@code sys_tenant} → {@code Tenant}，{@code user_info} → {@code UserInfo}。
   *
   * @param rawTableName - 去前缀后的裸表名（如 {@code sys_tenant}）
   * @param config - 当前模块配置（含模块名、表前缀等）
   * @return 实体类名（PascalCase，不含 DO/Entity 后缀）
   */
  String toEntityName(String rawTableName, ModuleGroupConfig config);

  /**
   * 获取 Repository 接口名。
   *
   * @param entityName - 实体类名
   * @return Repository 接口名（如 {@code TenantRepository}）
   */
  String toRepositoryName(String entityName);

  /**
   * 获取 Service 接口名。
   *
   * @param entityName - 实体类名
   * @return Service 接口名（如 {@code TenantService}）
   */
  String toServiceName(String entityName);

  /**
   * 获取 Service 实现类名。
   *
   * @param entityName - 实体类名
   * @return Service 实现类名（如 {@code TenantServiceImpl}）
   */
  String toServiceImplName(String entityName);

  /**
   * 获取 Controller 类名。
   *
   * @param entityName - 实体类名
   * @return Controller 类名（如 {@code TenantController}）
   */
  String toControllerName(String entityName);

  /**
   * 获取 DTO 类名。
   *
   * @param entityName - 实体类名
   * @return DTO 类名（如 {@code TenantDTO}）
   */
  String toDtoName(String entityName);

  /**
   * 获取 VO 类名。
   *
   * @param entityName - 实体类名
   * @return VO 类名（如 {@code TenantVO}）
   */
  String toVoName(String entityName);

  /**
   * 获取分页 Query 类名。
   *
   * @param entityName - 实体类名
   * @return Query 类名（如 {@code TenantPageQuery}）
   */
  String toQueryName(String entityName);

  /**
   * 获取 Mapper 接口名。
   *
   * @param entityName - 实体类名
   * @return Mapper 接口名（如 {@code TenantMapper}）
   */
  String toMapperName(String entityName);

  /**
   * 获取 Converter 接口名。
   *
   * @param moduleName - 模块名（如 {@code system}）
   * @return Converter 类名（如 {@code SystemConverter}）
   */
  String toConverterName(String moduleName);

  /**
   * 获取 FeignClient 接口名。
   *
   * @param entityName - 实体类名
   * @return FeignClient 接口名（如 {@code TenantFeignClient}）
   */
  String toFeignClientName(String entityName);

  /**
   * 获取 API 路径。
   *
   * @param rawTableName - 去前缀后的裸表名
   * @param moduleName - 模块名
   * @return REST API 路径（如 {@code /api/v1/tenant}）
   */
  String toApiPath(String rawTableName, String moduleName);

  /**
   * 获取权限前缀。
   *
   * @param moduleName - 模块名
   * @param rawTableName - 去前缀后的裸表名
   * @return 权限前缀（如 {@code sys:tenant}）
   */
  String toPermissionPrefix(String moduleName, String rawTableName);

  /**
   * 应用此策略到 TableMetadata（批量设置所有名称）。
   *
   * @param rawTableName - 去前缀后的裸表名
   * @param moduleName - 模块名
   * @param metadata - 待填充的元数据对象
   * @param config - 当前模块配置
   */
  default void applyTo(String rawTableName, String moduleName, TableMetadata metadata,
                       ModuleGroupConfig config) {
    String entity = toEntityName(rawTableName, config);
    metadata.setEntityName(entity);
    metadata.setRepositoryName(toRepositoryName(entity));
    metadata.setServiceName(toServiceName(entity));
    metadata.setServiceImplName(toServiceImplName(entity));
    metadata.setControllerName(toControllerName(entity));
    metadata.setDtoName(toDtoName(entity));
    metadata.setVoName(toVoName(entity));
    metadata.setQueryName(toQueryName(entity));
    metadata.setConverterName(toConverterName(moduleName));
    metadata.setApiPath(toApiPath(rawTableName, moduleName));
    metadata.setPermissionPrefix(toPermissionPrefix(moduleName, rawTableName));
  }
}
