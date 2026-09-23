package com.njydsz.generator.service;

import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.njydsz.common.exception.custom.SysException;
import com.njydsz.common.util.string.StringUtils;
import com.njydsz.generator.engine.GeneratorTypeMapper;
import com.njydsz.generator.entity.GenColumnMeta;
import com.njydsz.generator.entity.GenDatasource;
import com.njydsz.generator.entity.GenTableMeta;
import com.njydsz.generator.enums.DbDialectEnum;
import com.njydsz.generator.repository.GenColumnMetaRepository;
import com.njydsz.generator.repository.GenTableMetaRepository;

/**
 * 表&列元数据领域服务。
 *
 * <p>从数据库 metadata 读取表结构并缓存到 gen_table_meta / gen_column_meta。
 *
 * @author ydsz-team
 * @since 26.09.05
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class TableMetadataService {

  /** 表元数据列表初始容量。 */
  private static final int TABLE_LIST_CAPACITY = 64;
  /** 主键列表初始容量。 */
  private static final int PK_LIST_CAPACITY = 8;
  /** 枚举值正则：匹配 字段名(值1=标签1,值2=标签2) 格式。 */
  private static final java.util.regex.Pattern ENUM_PATTERN =
      java.util.regex.Pattern.compile("\\(([^)]+)\\)");
  /** 基类审计字段集合（这些字段由 MpBaseAuditEntity / MpBaseIdEntity 提供）。 */
  private static final java.util.Set<String> AUDIT_FIELD_COLUMNS = java.util.Set.of(
      "id", "tenant_id", "created_at", "updated_at", "created_by", "updated_by",
      "is_deleted", "deleted", "creator_id", "updater_id");

  private final GenTableMetaRepository tableMetaRepository;
  private final GenColumnMetaRepository columnMetaRepository;
  /** 数据库类型到 Java 类型的映射器。 */
  private final GeneratorTypeMapper typeMapper;

  /**
   * 查询数据源下全部已缓存表（按表名升序）。
   *
   * @param datasourceId 数据源 ID
   * @return 表元数据列表
   */
  public List<GenTableMeta> listCachedTables(Long datasourceId) {
    return tableMetaRepository.findByDatasourceIdOrderByTableNameAsc(datasourceId);
  }

  /**
   * 连接数据库刷新并缓存全部表元数据。
   *
   * @param datasource 数据源配置
   * @return 刷新后的表列表
   */
  @Transactional(rollbackFor = Exception.class)
  public List<GenTableMeta> refreshTables(GenDatasource datasource) {
    log.info("刷新数据源表元数据 name={}", datasource.getName());
    List<String> tableNames;
    try {
      tableNames = fetchTableNames(datasource);
    } catch (Exception e) {
      throw SysException.builder()
          .message("刷新表元数据失败: " + e.getMessage())
          .cause(e)
          .build();
    }
    List<GenTableMeta> result = new ArrayList<>(tableNames.size());

    // 删除旧缓存
    tableMetaRepository.deleteByDatasourceId(datasource.getId());

    for (String tableName : tableNames) {
      GenTableMeta meta = GenTableMeta.builder()
          .datasourceId(datasource.getId())
          .tableName(tableName)
          .comment(fetchTableComment(datasource, tableName))
          .aliasName(StringUtils.toCamelCase(tableName))
          .moduleName(extractModule(tableName))
          .cachedAt(LocalDateTime.now())
          .build();
      tableMetaRepository.save(meta);
      result.add(meta);
    }
    log.info("刷新表元数据完成 count={}", result.size());
    return result;
  }

  /**
   * 刷新并缓存指定表的列元数据。
   *
   * @param datasource 数据源
   * @param tableMeta  表元数据
   * @return 列元数据列表
   */
  @Transactional(rollbackFor = Exception.class)
  public List<GenColumnMeta> refreshColumns(GenDatasource datasource, GenTableMeta tableMeta) {
    List<GenColumnMeta> columns;
    try {
      columns = fetchColumns(datasource, tableMeta.getTableName());
    } catch (Exception e) {
      throw SysException.builder()
          .message("刷新列元数据失败: " + e.getMessage())
          .cause(e)
          .build();
    }
    // 删除旧列数据
    columnMetaRepository.deleteByTableMetaId(tableMeta.getId());
    // 插入新列数据
    for (GenColumnMeta col : columns) {
      col.setTableMetaId(tableMeta.getId());
      columnMetaRepository.save(col);
    }
    return columns;
  }

  /**
   * 查询表的列元数据。
   *
   * @param tableMetaId 表元数据 ID
   * @return 列元数据列表
   */
  public List<GenColumnMeta> listColumns(Long tableMetaId) {
    return columnMetaRepository.findByTableMetaIdOrderByIdAsc(tableMetaId);
  }

  /**
   * 获取表元数据（优先数据库，未缓存则刷新）。
   *
   * @param datasource 数据源
   * @param tableName  表名
   * @return 表元数据
   */
  public GenTableMeta getOrRefresh(GenDatasource datasource, String tableName) {
    return tableMetaRepository.findByDatasourceIdAndTableName(datasource.getId(), tableName)
        .orElseGet(() -> {
          refreshTables(datasource);
          return tableMetaRepository.findByDatasourceIdAndTableName(
              datasource.getId(), tableName).orElse(null);
        });
  }

  // ════════════════════════════════════════════════════════════
  // JDBC 原生读取
  // ════════════════════════════════════════════════════════════

  private List<String> fetchTableNames(GenDatasource datasource)
      throws SQLException, ClassNotFoundException {
    List<String> tables = new ArrayList<>(TABLE_LIST_CAPACITY);
    String driverClass = datasource.getDialect() != null
        ? DbDialectEnum.valueOf(datasource.getDialect()).getDriverClass()
        : DbDialectEnum.fromUrl(datasource.getJdbcUrl()).getDriverClass();
    Class.forName(driverClass);
    try (Connection conn = DriverManager.getConnection(
        datasource.getJdbcUrl(), datasource.getUsername(), datasource.getPassword())) {
      DatabaseMetaData metaData = conn.getMetaData();
      try (ResultSet rs = metaData.getTables(conn.getCatalog(), null, "%",
          new String[]{"TABLE"})) {
        while (rs.next()) {
          tables.add(rs.getString("TABLE_NAME"));
        }
      }
    }
    return tables;
  }

  private String fetchTableComment(GenDatasource datasource, String tableName) {
    try (Connection conn = DriverManager.getConnection(
        datasource.getJdbcUrl(), datasource.getUsername(), datasource.getPassword())) {
      DatabaseMetaData metaData = conn.getMetaData();
      try (ResultSet rs = metaData.getTables(conn.getCatalog(), null, tableName,
          new String[]{"TABLE"})) {
        if (rs.next()) {
          return rs.getString("REMARKS");
        }
      }
    } catch (Exception e) {
      log.warn("获取表注释失败 table={} err={}", tableName, e.getMessage());
    }
    return "";
  }

  private List<GenColumnMeta> fetchColumns(GenDatasource datasource, String tableName)
      throws SQLException, ClassNotFoundException {
    List<GenColumnMeta> columns = new ArrayList<>(TABLE_LIST_CAPACITY);
    String driverClass = datasource.getDialect() != null
        ? DbDialectEnum.valueOf(datasource.getDialect()).getDriverClass()
        : DbDialectEnum.fromUrl(datasource.getJdbcUrl()).getDriverClass();
    Class.forName(driverClass);
    try (Connection conn = DriverManager.getConnection(
        datasource.getJdbcUrl(), datasource.getUsername(), datasource.getPassword())) {
      DatabaseMetaData metaData = conn.getMetaData();

      // 主键
      List<String> pks = new ArrayList<>(PK_LIST_CAPACITY);
      try (ResultSet pkRs = metaData.getPrimaryKeys(conn.getCatalog(), null, tableName)) {
        while (pkRs.next()) {
          pks.add(pkRs.getString("COLUMN_NAME"));
        }
      }

      // 列
      try (ResultSet rs = metaData.getColumns(conn.getCatalog(), null, tableName, "%")) {
        while (rs.next()) {
          String colName = rs.getString("COLUMN_NAME");
          String dataType = rs.getString("TYPE_NAME");
          String remark = rs.getString("REMARKS");
          String javaType = typeMapper.resolveJavaType(dataType);
          GenColumnMeta col = GenColumnMeta.builder()
              .columnName(colName)
              .dataType(dataType)
              .javaType(javaType)
              .tsType(typeMapper.resolveTsType(dataType))
              .columnSize(rs.getInt("COLUMN_SIZE"))
              .isNullable(rs.getInt("NULLABLE") == DatabaseMetaData.columnNullable)
              .isPk(pks.contains(colName))
              .comment(remark)
              .enumValues(parseEnumValues(remark))
              .isAuditField(AUDIT_FIELD_COLUMNS.contains(colName.toLowerCase()))
              .isDtoSkipped(false)
              .isVoSkipped(false)
              .isQuerySkipped(false)
              .build();
          columns.add(col);
        }
      }
    }
    return columns;
  }

  // ════════════════════════════════════════════════════════════
  // 辅助方法
  // ════════════════════════════════════════════════════════════

  /**
   * 从字段注释中解析枚举值列表。
   *
   * <p>匹配格式：{@code 字段名(值1=标签1,值2=标签2)}，提取括号内的内容。
   * 例如：{@code 状态(0=禁用,1=启用)} → {@code "0=禁用,1=启用"}。
   *
   * @param comment 字段注释（可为 null）
   * @return 枚举值字符串，未匹配时返回 null
   */
  private String parseEnumValues(String comment) {
    if (comment == null || comment.isEmpty()) {
      return null;
    }
    java.util.regex.Matcher matcher = ENUM_PATTERN.matcher(comment);
    if (matcher.find()) {
      String inner = matcher.group(1).trim();
      // 验证格式：必须包含至少一个 "="
      if (inner.contains("=")) {
        return inner;
      }
    }
    return null;
  }

  private String extractModule(String tableName) {
    // 去掉前缀 t_ / T_ / tab_ / TAB_，取第一个下划线前的分段作为模块名
    String cleaned = StringUtils.removeStart(tableName, "t_");
    cleaned = StringUtils.removeStart(cleaned, "T_");
    cleaned = StringUtils.removeStart(cleaned, "tab_");
    cleaned = StringUtils.removeStart(cleaned, "TAB_");
    int idx = cleaned.indexOf('_');
    return idx > 0 ? cleaned.substring(0, idx) : cleaned;
  }
}
