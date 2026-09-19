package com.njydsz.common.tenant.datasource;

import java.util.Collection;
import java.util.List;

import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.flywaydb.core.Flyway;

import com.njydsz.common.tenant.config.TenantProperties;

/**
 * SCHEMA 模式 Flyway 迁移助手：为新租户创建独立 schema 并执行迁移脚本。
 *
 * <p>典型场景：SaaS 场景下每个新租户分配独立 PostgreSQL schema，需要在租户创建时：
 *
 * <ol>
 *   <li>CREATE SCHEMA IF NOT EXISTS {schema_name}
 *   <li>GRANT USAGE ON SCHEMA {schema_name} TO current_user
 *   <li>执行 schema 级别表结构迁移（与 public schema 共享表结构版本）
 * </ol>
 *
 * <p>本初始化器提供工具方法，Flyway 依赖需业务模块显式引入；{@link #migrateTenantSchema} 方法
 * 仅在 Flyway 已注册为 Spring Bean 时有效。
 *
 * <p><b>使用方式：</b>
 *
 * <pre>{@code
 * // 租户管理服务（ydsz-tenant-admin）调用
 * public void provisionNewTenant(String tenantId) {
 *     String schema = properties.resolveSchemaName(tenantId);
 *     flywayInitializer.createSchema(schema);
 *     flywayInitializer.migrateTenantSchema(schema);
 *     LOG.info("租户 {} schema {} 已初始化", tenantId, schema);
 * }
 * }</pre>
 *
 * <p><b>前置条件：</b>
 *
 * <ul>
 *   <li>当前模式为 {@link TenantProperties.TenantMode#SCHEMA}
 *   <li>数据库用户具备 CREATE SCHEMA 权限
 *   <li>Flyway migration 脚本路径在 {@code db/migration/tenant}（或自定义）
 * </ul>
 *
 * @author ydsz-team
 * @since 26.09.19
 */
@Slf4j
@RequiredArgsConstructor
public class SchemaFlywayInitializer {

  private final TenantProperties properties;
  @Nullable
  private final Flyway flyway;

  /**
   * 为指定租户创建 schema（若不存在）。
   *
   * <p>使用 {@code CREATE SCHEMA IF NOT EXISTS} 幂等执行——租户重入不会报错。
   *
   * @param schemaName 目标 schema 名称（已通过 {@link TenantProperties#resolveSchemaName} 解析）
   */
  public void createSchema(@Nonnull String schemaName) {
    if (!properties.isSchemaMode()) {
      LOG.warn("当前非 SCHEMA 模式，跳过 schema 创建: {}", schemaName);
      return;
    }
    // 安全性：schema 名必须符合命名规范
    if (!schemaName.matches("^[a-zA-Z_][a-zA-Z0-9_]*$")) {
      throw new IllegalArgumentException("非法的 schema 名称: " + schemaName);
    }
    // 使用 Flyway 的连接（或通过注入的 DataSource）执行 DDL
    if (flyway != null && flyway.getConfiguration().getDataSource() != null) {
      try (var conn = flyway.getConfiguration().getDataSource().getConnection();
           var stmt = conn.createStatement()) {
        stmt.execute("CREATE SCHEMA IF NOT EXISTS " + schemaName);
        LOG.info("Schema [{}] 已创建/确认", schemaName);
      } catch (Exception e) {
        LOG.error("Schema [{}] 创建失败: {}", schemaName, e.getMessage());
        throw new SchemaProvisionException("Schema创建失败: " + schemaName, e);
      }
    } else {
      LOG.warn("Flyway 未配置，跳过 schema 创建（业务模块需自行创建 schema）: {}", schemaName);
    }
  }

  /**
   * 为指定 schema 执行 Flyway 迁移。
   *
   * <p>策略：覆盖 Flyway 的 defaultSchema 为目标 schema，执行迁移后回滚。
   *
   * @param schemaName 目标 schema 名称
   */
  public void migrateTenantSchema(@Nonnull String schemaName) {
    if (flyway == null) {
      LOG.warn("Flyway 未配置，跳过 schema [{}] 迁移", schemaName);
      return;
    }
    if (!properties.isSchemaMode()) {
      LOG.warn("当前非 SCHEMA 模式，跳过 schema 迁移: {}", schemaName);
      return;
    }
    LOG.info("开始为 schema [{}] 执行 Flyway 迁移...", schemaName);
    // 使用 Flyway 的 schemas 配置为目标 schema
    Flyway tenantFlyway = Flyway.configure()
        .dataSource(flyway.getConfiguration().getDataSource())
        .schemas(schemaName)
        .defaultSchema(schemaName)
        .locations(resolveMigrationLocations())
        .baselineOnMigrate(true)
        .load();
    tenantFlyway.migrate();
    LOG.info("Schema [{}] Flyway 迁移完成", schemaName);
  }

  /**
   * 解析 migration 脚本位置。
   *
   * <p>默认扫描 {@code db/migration/tenant}，业务模块可通过覆盖 {@code ydsz.tenant.schema.migration-locations} 自定义。
   *
   * @return migration 脚本位置数组
   */
  protected String[] resolveMigrationLocations() {
    // 默认迁移脚本路径
    return new String[] {"db/migration/tenant"};
  }

  /**
   * 校验 schema 是否已初始化（是否包含 flyway_schema_history 表）。
   *
   * @param schemaName 目标 schema 名称
   * @return true=已初始化
   */
  public boolean isSchemaProvisioned(@Nonnull String schemaName) {
    if (flyway == null || flyway.getConfiguration().getDataSource() == null) {
      return false;
    }
    try (var conn = flyway.getConfiguration().getDataSource().getConnection();
         var stmt = conn.prepareStatement(
             "SELECT COUNT(*) FROM information_schema.tables "
                 + "WHERE table_schema = ? AND table_name = 'flyway_schema_history'")) {
      stmt.setString(1, schemaName);
      try (var rs = stmt.executeQuery()) {
        if (rs.next()) {
          return rs.getInt(1) > 0;
        }
      }
    } catch (Exception e) {
      LOG.debug("Schema [{}] 初始化状态查询失败: {}", schemaName, e.getMessage());
    }
    return false;
  }

  /**
   * Schema 初始化异常（provisioning failure）。
   *
   * <p>用于指示 schema 创建或迁移失败，与业务异常隔离以便独立重试。
   */
  public static class SchemaProvisionException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    public SchemaProvisionException(String message, Throwable cause) {
      super(message, cause);
    }
  }
}
