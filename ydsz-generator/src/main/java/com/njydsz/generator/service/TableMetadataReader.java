package com.njydsz.generator.service;

import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.List;

import javax.sql.DataSource;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import com.njydsz.generator.config.GeneratorProperties;
import com.njydsz.generator.model.ColumnMetadata;
import com.njydsz.generator.model.TableMetadata;

/**
 * 数据库表元数据读取器。
 *
 * <p>通过 {@link DatabaseMetaData} 读取表结构，包含列信息、主键、注释，并封装为 {@link TableMetadata}。
 *
 * <p>自动识别审计字段（created_at/created_by/updated_at/updated_by/deleted/tenant_id/status），
 * 业务字段列表中排除已继承自 {@code MpBaseEntity} 的字段。
 *
 * @author ydsz-team
 * @since 26.09.01
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class TableMetadataReader {

  private static final String[] TABLE_TYPES = {"TABLE"};

  private final DataSource dataSource;

  private final GeneratorProperties properties;

  /**
   * 读取指定表的完整元数据。
   *
   * @param tableName - 表名（如 {@code ydsz_sys_tenant}）
   * @return 表元数据
   */
  public TableMetadata readTable(String tableName) {
    TableMetadata metadata = new TableMetadata();
    metadata.setTableName(tableName);

    // 1. 解析表名 -> 各类名称
    parseNames(tableName, metadata);

    try (Connection conn = dataSource.getConnection()) {
      DatabaseMetaData dbMeta = conn.getMetaData();
      String catalog = conn.getCatalog();
      String schema = conn.getSchema();

      // 2. 读取列信息
      List<ColumnMetadata> allColumns = new ArrayList<>(32);
      List<ColumnMetadata> bizColumns = new ArrayList<>(32);
      try (ResultSet rs = dbMeta.getColumns(catalog, schema, tableName, "%")) {
        while (rs.next()) {
          ColumnMetadata col = mapColumn(rs);
          allColumns.add(col);
          if (!col.isAuditField()) {
            bizColumns.add(col);
          }
        }
      }
      metadata.setAllColumns(allColumns);
      metadata.setColumns(bizColumns);

      // 3. 读取主键
      try (ResultSet rs = dbMeta.getPrimaryKeys(catalog, schema, tableName)) {
        while (rs.next()) {
          String pkColumnName = rs.getString("COLUMN_NAME");
          for (ColumnMetadata col : allColumns) {
            if (col.getColumnName().equals(pkColumnName)) {
              col.setPrimaryKey(true);
              metadata.setPrimaryKey(col);
              break;
            }
          }
        }
      }

      // 4. 读取表注释
      metadata.setTableComment(readTableComment(conn, tableName));
    } catch (Exception e) {
      throw new RuntimeException("读取表 " + tableName + " 元数据失败", e);
    }

    log.info("读取表 {} 元数据: {} 列, {} 业务字段", tableName, allColumns.size(), bizColumns.size());
    return metadata;
  }

  /**
   * 批量读取配置的多张表。
   *
   * @return 表元数据列表
   */
  public List<TableMetadata> readAllConfiguredTables() {
    List<TableMetadata> list = new ArrayList<>(properties.getTableNames().size());
    for (String tableName : properties.getTableNames()) {
      list.add(readTable(tableName));
    }
    return list;
  }

  // -----------------------------------------------------------------------

 private void parseNames(String tableName, TableMetadata metadata) {
    String prefix = properties.getTablePrefix();
    // 去除表前缀
    String raw = tableName;
    if (prefix != null && !prefix.isBlank() && raw.startsWith(prefix)) {
      raw = raw.substring(prefix.length());
    }
    metadata.setRawTableName(raw);

    // 实体类名: 拆分 _ -> PascalCase 取最后一段
    String entity = toEntityName(raw);
    metadata.setEntityName(entity);
    metadata.setRepositoryName(entity + "Repository");
    metadata.setServiceName(entity + "Service");
    metadata.setServiceImplName(entity + "ServiceImpl");
    metadata.setControllerName(entity + "Controller");
    metadata.setConverterName(capitalize(properties.getModuleName()) + "Converter");
    metadata.setVoName(entity + "VO");
    metadata.setDtoName(entity + "DTO");
    metadata.setQueryName(entity + "PageQuery");

    // API 路径: /api/v1/模块/raw表名(去掉模块前缀)
    String moduleRaw = raw;
    String moduleName = properties.getModuleName();
    if (moduleRaw.startsWith(moduleName + "_")) {
      moduleRaw = moduleRaw.substring(moduleName.length() + 1);
    }
    metadata.setApiPath("/api/v1/" + moduleRaw.replace('_', '-'));
    // 权限前缀: 模块:实体名(短横线)
    metadata.setPermissionPrefix(moduleName + ":" + moduleRaw.replace('_', '-'));
  }

  private ColumnMetadata mapColumn(ResultSet rs) throws java.sql.SQLException {
    String columnName = rs.getString("COLUMN_NAME");
    int jdbcType = rs.getInt("DATA_TYPE");
    String jdbcTypeName = rs.getString("TYPE_NAME");
    int columnSize = rs.getInt("COLUMN_SIZE");
    int nullableInt = rs.getInt("NULLABLE");
    String comment = rs.getString("REMARKS");

    ColumnMetadata col = ColumnMetadata.builder()
        .columnName(columnName)
        .javaFieldName(toCamelCase(columnName))
        .javaType(DbTypeConverter.toJavaType(jdbcType))
        .javaShortType(DbTypeConverter.toJavaShortType(jdbcType))
        .jdbcType(jdbcType)
        .jdbcTypeName(jdbcTypeName)
        .comment(comment != null ? comment.trim() : "")
        .columnSize(columnSize)
        .nullable(nullableInt == DatabaseMetaData.columnNullable)
        .primaryKey(false)
        .auditField(isAuditColumn(columnName))
        .enumValues(parseEnumFromComment(comment))
        .build();
    return col;
  }

  private String readTableComment(Connection conn, String tableName) {
    String comment = "";
    try (ResultSet rs = conn.getMetaData().getTables(conn.getCatalog(), conn.getSchema(), tableName, TABLE_TYPES)) {
      if (rs.next()) {
        String remarks = rs.getString("REMARKS");
        if (remarks != null && !remarks.isBlank()) {
          comment = remarks.trim();
        }
      }
    } catch (Exception e) {
      log.warn("读取表 {} 注释失败: {}", tableName, e.getMessage());
    }
    return comment;
  }

  private static boolean isAuditColumn(String columnName) {
    return switch (columnName.toLowerCase()) {
      case "id", "created_at", "created_by", "updated_at", "updated_by", "deleted", "tenant_id", "status" -> true;
      default -> false;
    };
  }

  private static String parseEnumFromComment(String comment) {
    if (comment == null || comment.isBlank()) {
      return "";
    }
    int colonIdx = comment.indexOf(':');
    int semiIdx = comment.indexOf('；');
    if (colonIdx < 0) {
      return "";
    }
    int end = semiIdx > colonIdx ? semiIdx : comment.length();
    return comment.substring(colonIdx + 1, end).trim();
  }

  private static String toCamelCase(String snakeCase) {
    if (snakeCase == null || snakeCase.isEmpty()) {
      return snakeCase;
    }
    StringBuilder sb = new StringBuilder(snakeCase.length());
    boolean upperNext = false;
    for (char c : snakeCase.toCharArray()) {
      if (c == '_') {
        upperNext = true;
      } else if (upperNext) {
        sb.append(Character.toUpperCase(c));
        upperNext = false;
      } else {
        sb.append(Character.toLowerCase(c));
      }
    }
    return sb.toString();
  }

  private static String toEntityName(String rawTableName) {
    String cleaned = rawTableName.replace('-', '_');
    String[] parts = cleaned.split("_");
    // 跳过系统模块前缀，直击业务实体名
    StringBuilder sb = new StringBuilder();
    for (String part : parts) {
      if (part.length() > 1 && sb.isEmpty() && (part.equals("sys") || part.equals("acct") || part.equals("sec"))) {
        continue;
      }
      sb.append(capitalize(part.toLowerCase()));
    }
    return sb.toString();
  }

  private static String capitalize(String str) {
    if (str == null || str.isEmpty()) {
      return str;
    }
    return Character.toUpperCase(str.charAt(0)) + str.substring(1);
  }
}
