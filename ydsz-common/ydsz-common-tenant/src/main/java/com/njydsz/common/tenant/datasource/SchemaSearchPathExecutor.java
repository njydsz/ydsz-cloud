package com.njydsz.common.tenant.datasource;

import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;

import jakarta.annotation.Nonnull;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import com.njydsz.common.tenant.config.TenantProperties;

/**
 * SCHEMA 模式下连接初始化器：在获取数据库连接后自动执行 {@code SET search_path TO {schema},public}。
 *
 * <p>启用条件：
 *
 * <ul>
 *   <li>当前多租户模式为 {@link TenantProperties.TenantMode#SCHEMA}
 *   <li>{@link TenantProperties#isSchemaSearchPathEnabled()} 为 {@code true}
 *   <li>当前租户上下文非空、非超级管理员、非 skip 隔离
 * </ul>
 *
 * <p>执行效果：后续不带 schema 前缀的表名自动解析到租户 schema，无需 JSqlParser 改写表名前缀。
 *
 * <p><b>使用方式：</b>
 *
 * <ul>
 *   <li>方案 A（推荐）：在 {@code ydsz-common-jdbc} 的 {@code DynamicRoutingDataSource#getConnection()} 中调用
 *   <li>方案 B：业务模块通过 Spring AOP 拦截 DataSource 获取连接操作后调用
 * </ul>
 *
 * <p><b>清理：</b>连接归还连接池时，建议执行 {@code SET search_path TO public} 重置，避免连接复用串扰。
 *
 * @author ydsz-team
 * @since 26.09.19
 */
@Slf4j
@RequiredArgsConstructor
public class SchemaSearchPathExecutor {

  private final TenantProperties properties;

  /**
   * 设置当前连接的 search_path 到租户 schema。
   *
   * <p>若条件不满足（非 SCHEMA 模式、search_path 未启用、无租户上下文、超级管理员、skip 隔离），则为空操作。
   *
   * @param connection 数据库连接，不可为 {@code null}
   *throws SQLException SQL 执行异常
   */
  public void applySearchPath(@Nonnull Connection connection) throws SQLException {
    if (!properties.isSchemaMode()) {
      return;
    }
    if (!properties.isSchemaSearchPathEnabled()) {
      return;
    }
    if (connection == null) {
      return;
    }

    // 仅在有租户上下文时设置；无上下文时（定时任务/内部调用）保持默认 search_path
    var context = com.njydsz.common.core.context.TenantContextHolder.get();
    if (context == null
        || context.isSkipIsolation()
        || context.isSuperAdmin()) {
      return;
    }

    String schema = context.getSchema();
    if (schema != null && !schema.isEmpty()) {
      // YDIZ-INJ-006: schema 名通过 SPI 映射解析，非外部输入，无注入风险
      // 但为防御性编程，仍校验 schema 名仅包含字母数字下划线
      if (!schema.matches("^[a-zA-Z_][a-zA-Z0-9_]*$")) {
        log.warn("Schema 名 [{}] 包含非法字符，跳过 search_path 设置", schema);
        return;
      }
      try (Statement stmt = connection.createStatement()) {
        stmt.execute("SET search_path TO " + schema + ",public");
        log.debug("SET search_path TO {},public — 租户 {}", schema, context.getTenantId());
      } catch (SQLException e) {
        log.warn("search_path 设置失败，schema={}, 原因: {}", schema, e.getMessage());
        throw e;
      }
    }
  }

  /**
   * 重置连接 search_path 为默认值（连接归还时调用）。
   *
   * @param connection 数据库连接
   * @throws SQLException SQL 执行异常
   */
  public void resetSearchPath(@Nonnull Connection connection) throws SQLException {
    if (connection == null) {
      return;
    }
    try (Statement stmt = connection.createStatement()) {
      stmt.execute("SET search_path TO public");
    } catch (SQLException e) {
      log.debug("search_path 重置失败（可忽略，连接会被关闭）: {}", e.getMessage());
    }
  }
}
