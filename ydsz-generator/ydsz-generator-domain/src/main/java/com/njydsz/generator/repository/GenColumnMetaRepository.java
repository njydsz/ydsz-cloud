package com.njydsz.generator.repository;

import java.util.List;

import com.njydsz.generator.entity.GenColumnMeta;

/**
 * 列元数据 Repository 接口。
 *
 * @author ydsz-team
 * @since 26.09.05
 */
public interface GenColumnMetaRepository {

  /**
   * 保存或更新列元数据。
   *
   * @param columnMeta 实体
   * @return 保存后的实体
   */
  GenColumnMeta save(GenColumnMeta columnMeta);

  /**
   * 批量持久化列元数据。
   *
   * @param columns 实体集合
   * @return 保存后的实体集合
   */
  List<GenColumnMeta> batchSave(List<GenColumnMeta> columns);

  /**
   * 根据 ID 查询。
   *
   * @param id 主键
   * @return 列元数据
   */
  GenColumnMeta findById(Long id);

  /**
   * 查询表全部列元数据。
   *
   * @param tableMetaId 表元数据 ID
   * @return 列元数据列表
   */
  List<GenColumnMeta> findByTableMetaIdOrderByIdAsc(Long tableMetaId);

  /**
   * 删除列元数据。
   *
   * @param id 主键
   */
  void deleteById(Long id);

  /**
   * 删除表全部列元数据。
   *
   * @param tableMetaId 表元数据 ID
   */
  void deleteByTableMetaId(Long tableMetaId);
}
