package com.njydsz.nextwiki.infra.mapper;

import java.util.List;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import com.njydsz.nextwiki.infra.entity.Space;

/**
 * 知识库空间 Mapper
 *
 * <p>对应数据表 {@code nw_space}。
 *
 * @author ydsz-team
 * @since 26.09.01
 */
@Mapper
public interface SpaceMapper extends BaseMapper<Space> {

  /**
   * 根据租户ID查询空间列表（按排序号升序）。
   *
   * @param tenantId 租户ID
   * @return 空间列表
   */
  List<Space> selectByTenantId(@Param("tenantId") String tenantId);

  /**
   * 分页查询租户下的空间列表。
   *
   * @param page MyBatis-Plus 分页对象
   * @param tenantId 租户ID
   * @param offset 偏移量
   * @param limit 每页数量
   * @return 空间分页结果
   */
  IPage<Space> selectByTenantIdWithPage(
      Page<Space> page,
      @Param("tenantId") String tenantId,
      @Param("offset") int offset,
      @Param("limit") int limit);

  /**
   * 根据租户ID和名称查询空间。
   *
   * @param tenantId 租户ID
   * @param name 空间名称
   * @return 空间实体
   */
  Space selectByTenantIdAndName(
      @Param("tenantId") String tenantId, @Param("name") String name);

  /**
   * 统计租户下的空间数量。
   *
   * @param tenantId 租户ID
   * @return 空间数量
   */
  int countByTenantId(@Param("tenantId") String tenantId);
}
