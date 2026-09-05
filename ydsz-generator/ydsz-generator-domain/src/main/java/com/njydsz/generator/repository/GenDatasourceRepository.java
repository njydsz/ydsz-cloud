package com.njydsz.generator.repository;

import java.util.List;
import java.util.Optional;

import com.njydsz.generator.entity.GenDatasource;

/**
 * 数据源配置 Repository 接口。
 *
 * @author ydsz-team
 * @since 26.09.05
 */
public interface GenDatasourceRepository {

  /**
   * 保存或更新数据源。
   *
   * @param datasource 实体
   * @return 保存后的实体（带 ID）
   */
  GenDatasource save(GenDatasource datasource);

  /**
   * 根据 ID 查询。
   *
   * @param id 主键
   * @return Optional 实体
   */
  Optional<GenDatasource> findById(Long id);

  /**
   * 查询全部数据源。
   *
   * @return 数据源列表
   */
  List<GenDatasource> findAll();

  /**
   * 查询默认数据源。
   *
   * @return Optional 实体
   */
  Optional<GenDatasource> findByIsDefaultTrue();

  /**
   * 根据名称查询。
   *
   * @param name 数据源名称
   * @return Optional 实体
   */
  Optional<GenDatasource> findByName(String name);

  /**
   * 删除数据源。
   *
   * @param id 主键
   */
  void deleteById(Long id);

  /**
   * 统计数量。
   *
   * @return 总数
   */
  long count();
}
