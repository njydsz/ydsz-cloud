package com.njydsz.nextwiki.domain.repository;

import java.util.List;
import java.util.Optional;

import com.njydsz.nextwiki.domain.dto.SpaceDTO;

/**
 * 知识库空间仓储接口
 *
 * @author ydsz-team
 * @since 1.2.0
 */
public interface SpaceRepository {

  /**
   * 保存空间记录。
   *
   * @param dto 空间DTO
   * @return 受影响行数
   */
  int save(SpaceDTO dto);

  /**
   * 更新空间记录。
   *
   * @param dto 空间DTO
   * @return 受影响行数
   */
  int update(SpaceDTO dto);

  /**
   * 根据ID查找空间。
   *
   * @param id 空间ID
   * @return 空间DTO
   */
  Optional<SpaceDTO> findById(String id);

  /**
   * 根据租户ID和名称查找空间。
   *
   * @param tenantId 租户ID
   * @param name 空间名称
   * @return 空间DTO
   */
  Optional<SpaceDTO> findByTenantIdAndName(String tenantId, String name);

  /**
   * 查询租户下的空间列表。
   *
   * @param tenantId 租户ID
   * @return 空间DTO列表
   */
  List<SpaceDTO> findByTenantId(String tenantId);

  /**
   * 分页查询租户下的空间列表。
   *
   * @param tenantId 租户ID
   * @param offset 偏移量
   * @param limit 每页数量
   * @return 空间DTO分页列表
   */
  List<SpaceDTO> findByTenantIdWithPage(String tenantId, int offset, int limit);

  /**
   * 统计租户下的空间数量。
   *
   * @param tenantId 租户ID
   * @return 空间数量
   */
  int countByTenantId(String tenantId);

  /**
   * 删除空间（逻辑删除）。
   *
   * @param id 空间ID
   * @return 受影响行数
   */
  int deleteById(String id);
}
