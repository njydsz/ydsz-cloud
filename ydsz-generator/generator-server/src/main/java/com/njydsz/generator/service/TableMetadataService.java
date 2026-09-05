package com.njydsz.generator.service;

import com.njydsz.generator.entity.GenColumnMeta;
import com.njydsz.generator.entity.GenDatasource;
import com.njydsz.generator.entity.GenTableMeta;
import com.njydsz.generator.repository.GenColumnMetaRepository;
import com.njydsz.generator.repository.GenTableMetaRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

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

  private final GenTableMetaRepository tableMetaRepository;
  private final GenColumnMetaRepository columnMetaRepository;

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
      throw new RuntimeException("刷新表元数据失败: " + e.getMessage(), e);
    }
    List<GenTableMeta> result = new ArrayList<>();

    // 删除旧缓存
    tableMetaRepository.deleteByDatasourceId(datasource.getId());

    for (String tableName : tableNames) {
      GenTableMeta meta = GenTableMeta.builder()
          .datasourceId(datasource.getId())
          .tableName(tableName)
          .comment(fetchTableComment(datasource, tableName))
          .aliasName(toCamelCase(tableName))
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
      throw new RuntimeException("刷新列元数据失败: " + e.getMessage(), e);
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

  private List<String> fetchTableNames(GenDatasource datasource) throws Exception {
    List<String> tables = new ArrayList<>();
    Class.forName(datasource.getDialect().getDriverClass());
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
      throws Exception {
    List<GenColumnMeta> columns = new ArrayList<>();
    Class.forName(datasource.getDialect().getDriverClass());
    try (Connection conn = DriverManager.getConnection(
        datasource.getJdbcUrl(), datasource.getUsername(), datasource.getPassword())) {
      DatabaseMetaData metaData = conn.getMetaData();

      // 主键
      List<String> pks = new ArrayList<>();
      try (ResultSet pkRs = metaData.getPrimaryKeys(conn.getCatalog(), null, tableName)) {
        while (pkRs.next()) {
          pks.add(pkRs.getString("COLUMN_NAME"));
        }
      }

      // 列
      try (ResultSet rs = metaData.getColumns(conn.getCatalog(), null, tableName, "%")) {
        while (rs.next()) {
          String colName = rs.getString("COLUMN_NAME");
          GenColumnMeta col = GenColumnMeta.builder()
              .columnName(colName)
              .dataType(rs.getString("TYPE_NAME"))
              .columnSize(rs.getInt("COLUMN_SIZE"))
              .nullable(rs.getInt("NULLABLE") == DatabaseMetaData.columnNullable)
              .isPk(pks.contains(colName))
              .comment(rs.getString("REMARKS"))
              .skipDto(false)
              .skipVo(false)
              .skipQuery(false)
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

  private String toCamelCase(String name) {
    if (name == null || !name.contains("_")) {
      return name;
    }
    String[] parts = name.split("_");
    StringBuilder sb = new StringBuilder();
    for (int i = 0; i < parts.length; i++) {
      if (parts[i].isEmpty()) {
        continue;
      }
      if (i == 0) {
        sb.append(parts[i].toLowerCase());
      } else {
        sb.append(Character.toUpperCase(parts[i].charAt(0)));
        if (parts[i].length() > 1) {
          sb.append(parts[i].substring(1).toLowerCase());
        }
      }
    }
    return sb.toString();
  }

  private String extractModule(String tableName) {
    // 去掉前缀 t_ 或 tab_，取第一个单词作为模块名
    String cleaned = tableName.replaceAll("^[tT]_|^[tT]ab_", "");
    int idx = cleaned.indexOf('_');
    return idx > 0 ? cleaned.substring(0, idx) : cleaned;
  }
}
