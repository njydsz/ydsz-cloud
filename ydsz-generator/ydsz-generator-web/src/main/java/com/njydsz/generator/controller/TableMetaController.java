package com.njydsz.generator.controller;

import java.util.List;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.access.annotation.Secured;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.njydsz.common.audit.annotation.Audit;
import com.njydsz.common.audit.enums.AuditAction;
import com.njydsz.common.base.api.ApiVersion;
import com.njydsz.common.core.response.YdszResponse;
import com.njydsz.generator.entity.GenColumnMeta;
import com.njydsz.generator.entity.GenDatasource;
import com.njydsz.generator.entity.GenTableMeta;
import com.njydsz.generator.service.DatasourceService;
import com.njydsz.generator.service.TableMetadataService;

/**
 * 表元数据管理 REST 控制器。
 *
 * @author ydsz-team
 * @since 26.09.05
 */
@Slf4j
@ApiVersion("26.09.01")
@Secured("ROLE_GENERATOR_USER")
@RestController
@RequestMapping("/generator/tables")
@RequiredArgsConstructor
public class TableMetaController {

  private final DatasourceService datasourceService;
  private final TableMetadataService tableMetadataService;

  /**
   * 查询数据源下全部表结构元数据（本地缓存）。
   *
   * <p>兼容 MySQL、PostgreSQL、Oracle、SQL Server 等主流关系型数据库。
   * 首次查询或缓存过期时自动从数据库实时加载并缓存。
   *
   * @param datasourceId 数据源 ID，需为已配置且连接正常的数据源
   * @return 表元数据列表，包含 tableName（物理表名）、comment（表注释）、
   *         aliasName（别名/类名来源）、moduleName（模块名）等；无表时返回空列表
   */
  @GetMapping
  public YdszResponse<List<GenTableMeta>> listTables(@RequestParam Long datasourceId) {
    return YdszResponse.success(tableMetadataService.listCachedTables(datasourceId));
  }

  /**
   * 重新连接数据库刷新表元数据缓存。
   *
   * <p>兼容 MySQL、PostgreSQL、Oracle、SQL Server 等主流关系型数据库。
   * 从目标数据库实时读取表结构信息替换本地缓存，用于表结构发生变更后同步。
   *
   * @param datasourceId 数据源 ID，需为已配置且连接正常的数据源
   * @return 刷新后的表元数据列表
   * @throws IllegalArgumentException 数据源不存在或已删除
   */
  @PostMapping("/refresh")
  @Audit(module = "表元数据", action = AuditAction.SYNC, content = "'刷新表元数据:' + #datasourceId")
  public YdszResponse<List<GenTableMeta>> refreshTables(@RequestParam Long datasourceId) {
    GenDatasource ds = datasourceService.getById(datasourceId);
    if (ds == null) {
      throw new IllegalArgumentException("数据源不存在: " + datasourceId);
    }
    return YdszResponse.success(tableMetadataService.refreshTables(ds));
  }

  /**
   * 查询指定表的列元数据（字段名、类型、注释、主键标识等）。
   *
   * <p>兼容 MySQL、PostgreSQL、Oracle、SQL Server 等主流关系型数据库的字段类型映射。
   * 自动将数据库原生类型转换为 Java 类型映射。
   *
   * @param tableMetaId 表元数据 ID（由 {@link #listTables(Long)} 返回的 GenTableMeta.id）
   * @return 列元数据列表，包含 columnName（列名）、columnType（数据库类型）、
   *         javaType（Java 类型映射）、columnComment（列注释）、
   *         isPrimaryKey（是否主键）等；无列时返回空列表
   */
  @GetMapping("/columns")
  public YdszResponse<List<GenColumnMeta>> getColumns(@RequestParam Long tableMetaId) {
    return YdszResponse.success(tableMetadataService.listColumns(tableMetaId));
  }

  /**
   * 从数据库重新读取并刷新指定表的列元数据。
   *
   * <p>兼容 MySQL、PostgreSQL、Oracle、SQL Server 等主流关系型数据库。
   * 当表结构发生变更（新增/删除/修改字段）后调用此接口同步缓存。
   *
   * @param datasourceId 数据源 ID，需为已配置且连接正常的数据源
   * @param tableName    需刷新的物理表名
   * @return 刷新后的列元数据列表
   * @throws IllegalArgumentException 数据源不存在
   */
  @PostMapping("/columns/refresh")
  @Audit(module = "表元数据", action = AuditAction.SYNC, content = "'刷新列元数据:' + #tableName")
  public YdszResponse<List<GenColumnMeta>> refreshColumns(
      @RequestParam Long datasourceId, @RequestParam String tableName) {
    GenDatasource ds = datasourceService.getById(datasourceId);
    GenTableMeta tableMeta = tableMetadataService.getOrRefresh(ds, tableName);
    return YdszResponse.success(tableMetadataService.refreshColumns(ds, tableMeta));
  }
}
