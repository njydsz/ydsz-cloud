package com.njydsz.common.tenant.datasource;

import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.List;

import javax.sql.DataSource;

import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import com.njydsz.common.tenant.config.TenantProperties;

/**
 * SCHEMA 模式 schema 初始化器：为新租户创建独立 schema 并复制表结构。
 *
 * <p>纯 JDBC 实现，不依赖 Flyway/Liquibase（遵守本项目 §1 规范）。
 *
 * <p>典型 SaaS 场景：每个新租户分配独立 PostgreSQL schema，此初始化器负责：
 *
 * <ol>
 *   <li>CREATE SCHEMA IF NOT EXISTS {schema_name}
 *   <li>GRANT USAGE/CREATE ON SCHEMA {schema_name} TO current_user
 *   <li>从模板 schema（{@code template} 参数）复制表结构（{@code CREATE TABLE ... LIKE ...}）
 * </ol>
 *
 * <p><b>使用方式：</b>
 *
 * <pre>{@code
 * // 租户管理服务调用
 * SchemaInitializer initializer = new SchemaInitializer(properties, dataSource);
 * String schema = properties.resolveSchemaName(tenantId);
 * if (!initializer.isSchemaProvisioned(schema)) {
 *     initializer.provisionNewTenant(schema, "public");  // 从 public 复制表结构
 * }
 * }</pre>
 *
 * <p><b>前置条件：</b>
 *
 * <ul>
 *   <li>当前模式为 {@link TenantProperties.TenantMode#SCHEMA}
 *   <li>数据库用户具备 CREATE SCHEMA + 读取模板 schema 权限
 * </ul>
 *
 * @author ydsz-team
 * @since 26.09.19
 */
@Slf4j
@RequiredArgsConstructor
public class SchemaInitializer {

  private final TenantProperties properties;
  @Nullable
  private final DataSource dataSource;

  /**
   * 为租户创建 schema 并复制表结构。
   *
   * <p>幂等操作：重复调用不会对已存在的 schema 重复复制表结构。
   *
   * @param schemaName 目标 schema 名称（已由 {@link TenantProperties#resolveSchemaName} 解析）
   * @templateSchema 模板 schema（如 "public"），为 null 则只创建 schema 不复制表结构
   */
  public void provisionNewTenant(@Nonnull String schemaName, @Nullable String templateSchema) {
    validateSchemaMode();
    validateSchemaName(schemaName);

    if (dataSource == null) {
      LOG.warn("DataSource 未注入，跳过 schema 初始化: {}", schemaName);
      return;
    }

    if (isSchemaProvisioned(schemaName)) {
      LOG.info("Schema [{}] 已初始化，跳过重复初始化", schemaName);
      return;
    }

    try (Connection conn = dataSource.getConnection()) {
      // Step 1: 创建 schema
      createSchema(conn, schemaName);
      // Step 2: 授权
      grantSchemaPermissions(conn, schemaName);
      // Step 3: 从模板 schema 复制表结构（如有指定）
      if (templateSchema != null && !templateSchema.isEmpty()) {
        copyTableStructures(conn, templateSchema, schemaName);
      }
      LOG.info("租户 schema [{}] 初始化完成（模板 schema: {}）", schemaName, templateSchema);
    } catch (SQLException e) {
      LOG.error("租户 schema [{}] 初始化失败: {}", schemaName, e.getMessage(), e);
      throw new SchemaProvisionException("schema 初始化失败: " + schemaName, e);
    }
  }

  /**
   * 仅创建 schema 并授权（不复制表结构）。
   *
   * @param schemaName 目标 schema 名称
   */
  public void createSchema(@Nonnull String schemaName) {
    validateSchemaMode();
    validateSchemaName(schemaName);

    if (dataSource == null) {
      LOG.warn("DataSource 未注入，跳过 schema 创建");
      return;
    }

    try (Connection conn = dataSource.getConnection()) {
      createSchema(conn, schemaName);
      grantSchemaPermissions(conn, schemaName);
    } catch (SQLException e) {
      throw new SchemaProvisionException("schema 创建失败: " + schemaName, e);
    }
  }

  private void createSchema(@Nonnull Connection conn, @Nonnull String schemaName)
      throws SQLException {
    try (Statement stmt = conn.createStatement()) {
      stmt.execute("CREATE SCHEMA IF NOT EXISTS " + schemaName);
      LOG.info("Schema [{}] 已创建/确认", schemaName);
    } catch (SQLException e) {
      // 并发创建场景：schema 已存在时忽略
      if ("42P06".equals(e.getSQLState())) {  // duplicate_schema
        LOG.info("Schema [{}] 已存在（并发），跳过创建", schemaName);
      } else {
        throw e;
      }
    }
  }

  private void grantSchemaPermissions(@Nonnull Connection conn, @Nonnull String schemaName)
      throws SQLException {
    try (Statement stmt = conn.createStatement()) {
      // USAGE: 允许在 schema 中访问对象
      stmt.execute("GRANT USAGE ON SCHEMA " + schemaName + " TO CURRENT_USER");
      // CREATE: 允许在 schema 中创建表
      stmt.execute("GRANT CREATE ON SCHEMA " + schemaName + " TO CURRENT_USER");
      log.debug("Schema [{}] 授权完成", schemaName);
    } catch (SQLException e) {
      // 用户可能是 schema owner，授权被忽略也能工作，只记 warn
      log.warn("Schema [{}] 授权失败（用户可能已是 owner）: {}", schemaName, e.getMessage());
    }
  }

  /**
   * 从模板 schema 复制表结构到目标 schema（CREATE TABLE ... (LIKE ... INCLUDING ALL)）。
   *
   * @param conn        数据库连接
   * @param templateSchema 模板 schema
   * @param targetSchema   目标 schema
   */
  private void copyTableStructures(
      @Nonnull Connection conn, @Nonnull String templateSchema, @Nonnull String targetSchema)
      throws SQLException {
    // 查询模板 schema 中所有表
    List<String> tables = new java.util.ArrayList<>(16);
    try (var ps = conn.prepareStatement(
        "SELECT table_name FROM information_schema.tables "
            + "WHERE table_schema = ? AND table_type = 'BASE TABLE' ORDER BY table_name")) {
      ps.setString(1, templateSchema);
      try (var rs = ps.executeQuery()) {
        while (rs.next()) {
          tables.add(rs.getString("table_name"));
        }
      }
    }

    if (tables.isEmpty()) {
      log.warn("模板 schema [{}] 中无表可复制", templateSchema);
      return;
    }

    try (Statement stmt = conn.createStatement()) {
      for (String table : tables) {
        // 幂等：IF NOT EXISTS 避免重复创建
        String createSql = String.format(
            "CREATE TABLE IF NOT EXISTS %s.%s (LIKE %s.%s INCLUDING ALL)",
            targetSchema, table, templateSchema, table);
        stmt.execute(createSql);
        log.debug("表 {}.{} 已复制（来自 {}.{}）", targetSchema, table, templateSchema, table);
      }
    }
    log.info("模板 schema [{}] 的 {} 个表已复制至 schema [{}]", templateSchema, tables.size(), targetSchema);
  }

  /**
   * 校验 schema 是否已初始化（是否包含至少一个表）。
   *
   * @param schemaName 目标 schema 名称
   * @return true=已初始化（schema 存在且有表）
   * @since 26.09.19
   */
  public boolean isSchemaProvisioned(@Nonnull String schemaName) {
    if (dataSource == null) {
      return false;
    }
    validateSchemaName(schemaName);

    try (Connection conn = dataSource.getConnection();
         var ps = conn.prepareStatement(
             "SELECT COUNT(*) FROM information_schema.tables "
                 + "WHERE table_schema = ? AND table_type = 'BASE TABLE'")) {
      ps.setString(1, schemaName);
      try (var rs = ps.executeQuery()) {
        if (rs.next()) {
          return rs.getInt(1) > 0;
        }
      }
    } catch (SQLException e) {
      log.debug("Schema [{}] 初始化状态查询失败: {}", schemaName, e.getMessage());
    }
    return false;
  }

  private void validateSchemaMode() {
    if (!properties.isSchemaMode()) {
      throw new IllegalStateException(
          "当前非 SCHEMA 模式，无法使用 SchemaInitializer。请确认 ydzs.tenant.mode=SCHEMA");
    }
  }

  private void validateSchemaName(@Nonnull String schemaName) {
    if (schemaName == null || schemaName.isEmpty()) {
      throw new IllegalArgumentException("schemaName 不可为空");
    }
    // 安全性：schema 名必须符合命名规范，防止 SQL 注入
    if (!schemaName.matches("^[a-zA-Z_][a-zA-Z0-9_]*$")) {
      throw new IllegalArgumentException("非法的 schema 名称: " + schemaName);
    }
  }

  /**
   * Schema 初始化异常。
   *
   * <p>用于隔离 schema 创建/复制失败与其他业务异常，便于上层租户管理服务独立捕获重试。
   *
   * @since 26.09.19
   */
  public static class SchemaProvisionException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    public SchemaProvisionException(String message, Throwable cause) {
      super(message, cause);
    }
  }
}
