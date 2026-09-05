package com.njydsz.generator.repository;

import java.util.List;
import java.util.Optional;

import com.njydsz.generator.entity.GenTemplateGroup;

/**
 * 模板分组 Repository 接口。
 *
 * @author ydsz-team
 * @since 26.09.05
 */
public interface GenTemplateGroupRepository {

  /**
   * 保存或更新分组。
   *
   * @param group 实体
   * @return 保存后的实体
   */
  GenTemplateGroup save(GenTemplateGroup group);

  /**
   * 根据 ID 查询。
   *
   * @param id 主键
   * @return Optional 实体
   */
  Optional<GenTemplateGroup> findById(Long id);

  /**
   * 根据名称查询。
   *
   * @param name 分组名
   * @return Optional 实体
   */
  Optional<GenTemplateGroup> findByName(String name);

  /**
   * 查询全部分组（按 sortOrder 升序）。
   *
   * @return 分组列表
   */
  List<GenTemplateGroup> findAllByOrderBySortOrderAsc();

  /**
   * 查询当前激活分组。
   *
   * @return Optional 实体
   */
  Optional<GenTemplateGroup> findByIsActiveTrue();

  /**
   * 删除分组。
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
