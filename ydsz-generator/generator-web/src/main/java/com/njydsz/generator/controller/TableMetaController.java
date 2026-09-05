package com.njydsz.generator.controller;

import com.njydsz.common.core.response.YdszResponse;
import com.njydsz.generator.entity.GenColumnMeta;
import com.njydsz.generator.entity.GenDatasource;
import com.njydsz.generator.entity.GenTableMeta;
import com.njydsz.generator.service.DatasourceService;
import com.njydsz.generator.service.TableMetadataService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 表元数据管理 REST 控制器。
 *
 * @author ydsz-team
 * @since 26.09.05
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/generator/tables")
@RequiredArgsConstructor
public class TableMetaController {

  private final DatasourceService datasourceService;
  private final TableMetadataService tableMetadataService;

  /**
   * 查询数据源下全部表（缓存）。
   *
   * @param datasourceId 数据源 ID
   * @return 表元数据列表
   */
  @GetMapping
  public YdszResponse<List<GenTableMeta>> listTables(@RequestParam Long datasourceId) {
    return YdszResponse.success(tableMetadataService.listCachedTables(datasourceId));
  }

  /**
   * 刷新数据源表元数据。
   *
   * @param datasourceId 数据源 ID
   * @return 刷新后列表
   */
  @PostMapping("/refresh")
  public YdszResponse<List<GenTableMeta>> refreshTables(@RequestParam Long datasourceId) {
    GenDatasource ds = datasourceService.getById(datasourceId);
    if (ds == null) {
      throw new IllegalArgumentException("数据源不存在: " + datasourceId);
    }
    return YdszResponse.success(tableMetadataService.refreshTables(ds));
  }

  /**
   * 查询表的列元数据。
   *
   * @param tableMetaId 表元数据 ID
   * @return 列元数据列表
   */
  @GetMapping("/columns")
  public YdszResponse<List<GenColumnMeta>> getColumns(@RequestParam Long tableMetaId) {
    return YdszResponse.success(tableMetadataService.listColumns(tableMetaId));
  }

  /**
   * 刷新表的列元数据。
   *
   * @param datasourceId 数据源 ID
   * @param tableName    表名
   * @return 列元数据列表
   */
  @PostMapping("/columns/refresh")
  public YdszResponse<List<GenColumnMeta>> refreshColumns(
      @RequestParam Long datasourceId, @RequestParam String tableName) {
    GenDatasource ds = datasourceService.getById(datasourceId);
    GenTableMeta tableMeta = tableMetadataService.getOrRefresh(ds, tableName);
    return YdszResponse.success(tableMetadataService.refreshColumns(ds, tableMeta));
  }
}
