package com.njydsz.nextwiki.domain.repository;

import java.util.List;
import java.util.Optional;


/**
 * 空间模板仓储接口
 *
 * @author ydsz-team
 * @since 26.09.01
 */
public interface SpaceTemplateRepository {

  /**
   * 保存模板记录。
   *
   * @param dto 模板DTO
   * @return 受影响行数
   */
  int save(SpaceTemplateDTO dto);

  /**
   * 更新模板记录。
   *
   * @param dto 模板DTO
   * @return 受影响行数
   */
  int update(SpaceTemplateDTO dto);

  /**
   * 根据ID查找模板。
   *
   * @param id 模板ID
   * @return 模板DTO
   */
  Optional<SpaceTemplateDTO> findById(String id);

  /**
   * 查询可用模板列表（含系统模板 + 租户自定义模板）。
   *
   * @param tenantId 租户ID
   * @param category 分类（可为 null 表示全部分类）
   * @return 模板DTO列表
   */
  List<SpaceTemplateDTO> findAvailableTemplates(String tenantId, String category);

  /**
   * 分页查询模板列表。
   *
   * @param tenantId 租户ID
   * @param category 分类
   * @param offset 偏移量
   * @param limit 每页数量
   * @return 模板DTO分页列表
   */
  List<SpaceTemplateDTO> findWithPage(String tenantId, String category, int offset, int limit);

  /**
   * 统计模板数量。
   *
   * @param tenantId 租户ID
   * @param category 分类
   * @return 模板数量
   */
  int countByTenantId(String tenantId, String category);

  /**
   * 增加使用次数。
   *
   * @param id 模板ID
   * @return 受影响行数
   */
  int incrementUsageCount(String id);

  /**
   * 删除模板（仅非系统模板可删除）。
   *
   * @param id 模板ID
   * @return 受影响行数
   */
  int deleteById(String id);
}
