package com.njydsz.nextwiki.infra.mapper;

import java.util.List;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import com.njydsz.nextwiki.infra.entity.SpaceTemplateDO;

/**
 * 空间模板 Mapper
 *
 * @author ydsz-team
 * @since 1.2.0
 */
@Mapper
public interface SpaceTemplateMapper extends BaseMapper<SpaceTemplateDO> {

  /**
   * 查询可用模板（系统公开模板 + 租户自定义模板，按排序号升序）。
   *
   * @param tenantId 租户ID
   * @param category 分类
   * @return 模板列表
   */
  List<SpaceTemplateDO> selectAvailableTemplates(
      @Param("tenantId") String tenantId, @Param("category") String category);

  /**
   * 分页查询模板列表。
   *
   * @param tenantId 租户ID
   * @param category 分类
   * @param offset 偏移量
   * @param limit 每页数量
   * @return 模板列表
   */
  List<SpaceTemplateDO> selectWithPage(
      @Param("tenantId") String tenantId,
      @Param("category") String category,
      @Param("offset") int offset,
      @Param("limit") int limit);

  /**
   * 统计模板数量。
   *
   * @param tenantId 租户ID
   * @param category 分类
   * @return 模板数量
   */
  int countByCondition(
      @Param("tenantId") String tenantId, @Param("category") String category);
}
