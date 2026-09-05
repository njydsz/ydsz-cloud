package com.njydsz.generator.repository;

import java.util.List;
import java.util.Optional;

import com.njydsz.generator.entity.GenTemplate;

/**
 * 模板 Repository 接口。
 *
 * @author ydsz-team
 * @since 26.09.05
 */
public interface GenTemplateRepository {

  /**
   * 保存或更新模板。
   *
   * @param template 实体
   * @return 保存后的实体
   */
  GenTemplate save(GenTemplate template);

  /**
   * 根据 ID 查询。
   *
   * @param id 主键
   * @return Optional 实体
   */
  Optional<GenTemplate> findById(Long id);

  /**
   * 根据分组 ID + 文件名查询。
   *
   * @param groupId  分组 ID
   * @param fileName 文件名
   * @return Optional 实体
   */
  Optional<GenTemplate> findByGroupIdAndFileName(Long groupId, String fileName);

  /**
   * 查询分组全部模板。
   *
   * @param groupId 分组 ID
   * @return 模板列表
   */
  List<GenTemplate> findByGroupIdOrderByFileNameAsc(Long groupId);

  /**
   * 批量持久化模板集合。
   *
   * @param templates 实体集合
   * @return 保存后的实体集合
   */
  List<GenTemplate> batchSave(List<GenTemplate> templates);

  /**
   * 删除模板。
   *
   * @param id 主键
   */
  void deleteById(Long id);

  /**
   * 删除分组全部模板。
   *
   * @param groupId 分组 ID
   */
  void deleteByGroupId(Long groupId);

  /**
   * 统计分组模板数量。
   *
   * @param groupId 分组 ID
   * @return 数量
   */
  long countByGroupId(Long groupId);
}
