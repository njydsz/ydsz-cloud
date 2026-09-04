package com.njydsz.generator.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 数据库列元数据。
 *
 * <p>封装 JDBC {@link java.sql.DatabaseMetaData#getColumns} 返回的列描述，用于模板渲染字段声明。
 *
 * @author ydsz-team
 * @since 26.09.01
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ColumnMetadata {

  /** 列名（数据库原始名称，如 {@code tenant_name}） */
  private String columnName;

  /** Java 驼峰字段名（如 {@code tenantName}） */
  private String javaFieldName;

  /** Java 类型全限定名（如 {@code java.lang.String}） */
  private String javaType;

  /** Java 短类型名（如 {@code String}、{@code LocalDateTime}） */
  private String javaShortType;

  /** JDBC 类型码（{@link java.sql.Types}） */
  private int jdbcType;

  /** JDBC 类型名（如 {@code VARCHAR}、{@code TIMESTAMP}） */
  private String jdbcTypeName;

  /** 列注释（COMMENT） */
  private String comment;

  /** 列长度 */
  private int columnSize;

  /** 是否可为 NULL */
  private boolean nullable;

  /** 是否为主键 */
  private boolean primaryKey;

  /** 是否为审计字段（createdAt/createdBy/updatedAt/updatedBy/deleted/tenantId/status） */
  private boolean auditField;

  /** 枚举候选值（从注释中解析，格式: {@code 状态:1=启用;0=禁用} -> "1=启用;0=禁用"） */
  private String enumValues;
}
