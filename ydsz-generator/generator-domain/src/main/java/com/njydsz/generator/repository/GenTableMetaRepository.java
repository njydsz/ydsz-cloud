package com.njydsz.generator.repository;

import com.njydsz.generator.entity.GenTableMeta;

import java.util.List;
import java.util.Optional;

/**
 * 表元数据 Repository 接口。
 *
 * @author ydsz-team
 * @since 26.09.05
 */
public interface GenTableMetaRepository {

  /**
   * 保存或更新表元数据。
   *
   * @param tableMeta 实体
   * @return 保存后的实体
   */
  GenTableMeta save(GenTableMeta tableMeta);

  /**
   * 根据 ID 查询。
   *
   * @param id 主键
   * @return Optional 实体
   */
  Optional<GenTableMeta> findById(Long id);

  /**
   * 根据数据源 ID + 表名查询。
   *
   * @param datasourceId 数据源 ID
   * @param tableName    物理表名
   * @return Optional 实体
   */
  Optional<GenTableMeta> findByDatasourceIdAndTableName(Long datasourceId, String tableName);

  /**
   * 查询某个数据源全部表元数据。
   *
   * @param datasourceId 数据源 ID
   * @return 表元数据列表
   */
  List<GenTableMeta> findByDatasourceIdOrderByTableNameAsc(Long datasourceId);

  /**
   * 删除表元数据。
   *
   * @param id 主键
   */
  void deleteById(Long id);

  /**
   * 删除数据源下全部表元数据。
   *
   * @param datasourceId 数据源 ID
   */
  void deleteByDatasourceId(Long datasourceId);

  /**
   * 统计数据源下表元数据数量。
   *
   * @param datasourceId 数据源 ID
   * @return 数量
   */
  long countByDatasourceId(Long datasourceId);
}
