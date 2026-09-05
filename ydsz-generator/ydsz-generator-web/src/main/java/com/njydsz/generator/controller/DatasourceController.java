package com.njydsz.generator.controller;

import com.njydsz.common.core.response.YdszResponse;
import com.njydsz.generator.entity.GenDatasource;
import com.njydsz.generator.service.DatasourceService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 数据源管理 REST 控制器。
 *
 * @author ydsz-team
 * @since 26.09.05
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/generator/datasources")
@RequiredArgsConstructor
public class DatasourceController {

  private final DatasourceService datasourceService;

  /**
   * 查询全部数据源。
   *
   * @return 数据源列表
   */
  @GetMapping
  public YdszResponse<List<GenDatasource>> list() {
    return YdszResponse.success(datasourceService.listAll());
  }

  /**
   * 获取默认数据源。
   *
   * @return 默认数据源
   */
  @GetMapping("/default")
  public YdszResponse<GenDatasource> getDefault() {
    return YdszResponse.success(datasourceService.getDefault());
  }

  /**
   * 测试连接。
   *
   * @param datasource 数据源配置
   * @return 是否连接成功
   */
  @PostMapping("/test")
  public YdszResponse<Boolean> testConnection(@RequestBody GenDatasource datasource) {
    return YdszResponse.success(datasourceService.testConnection(datasource));
  }

  /**
   * 创建数据源。
   *
   * @param datasource 数据源实体
   * @return 持久化后实体
   */
  @PostMapping
  public YdszResponse<GenDatasource> create(@RequestBody GenDatasource datasource) {
    return YdszResponse.success(datasourceService.create(datasource));
  }

  /**
   * 更新数据源。
   *
   * @param datasource 数据源实体
   * @return 持久化后实体
   */
  @PostMapping("/update")
  public YdszResponse<GenDatasource> update(@RequestBody GenDatasource datasource) {
    return YdszResponse.success(datasourceService.update(datasource));
  }

  /**
   * 删除数据源。
   *
   * @param id 数据源 ID
   * @return 操作结果
   */
  @DeleteMapping("/{id}")
  public YdszResponse<Void> delete(@PathVariable Long id) {
    datasourceService.deleteById(id);
    return YdszResponse.success(null);
  }
}
