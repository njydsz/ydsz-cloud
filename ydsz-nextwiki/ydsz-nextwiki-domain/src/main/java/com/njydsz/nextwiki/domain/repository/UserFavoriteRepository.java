package com.njydsz.nextwiki.domain.repository;

import java.util.List;

import com.njydsz.nextwiki.domain.dto.UserFavoriteDTO;

/**
 * 用户收藏夹仓储接口
 *
 * <p>定义收藏夹数据访问操作，实现类位于 infra 层。
 *
 * @author ydsz-team
 * @since 1.1.0
 */
public interface UserFavoriteRepository {

  /**
   * 保存收藏记录。
   *
   * @param dto 收藏DTO
   * @return 受影响行数
   */
  int save(UserFavoriteDTO dto);

  /**
   * 根据用户ID和节点ID删除收藏记录（逻辑删除）。
   *
   * @param userId 用户ID
   * @param nodeId 节点ID
   * @return 受影响行数
   */
  int deleteByUserIdAndNodeId(String userId, String nodeId);

  /**
   * 查询用户收藏列表（按排序号升序）。
   *
   * @param userId 用户ID
   * @param tenantId 租户ID
   * @return 收藏DTO列表
   */
  List<UserFavoriteDTO> findByUserId(String userId, String tenantId);

  /**
   * 分页查询用户收藏列表。
   *
   * @param userId 用户ID
   * @param tenantId 租户ID
   * @param offset 偏移量
   * @param limit 每页数量
   * @return 收藏DTO分页列表
   */
  List<UserFavoriteDTO> findByUserIdWithPage(String userId, String tenantId, int offset, int limit);

  /**
   * 统计用户收藏数量。
   *
   * @param userId 用户ID
   * @param tenantId 租户ID
   * @return 收藏数量
   */
  int countByUserId(String userId, String tenantId);

  /**
   * 查询用户的最大排序号。
   *
   * @param userId 用户ID
   * @param tenantId 租户ID
   * @return 最大排序号（无记录时返回 0）
   */
  int findMaxSortOrder(String userId, String tenantId);

  /**
   * 检查节点是否已被用户收藏。
   *
   * @param userId 用户ID
   * @param nodeId 节点ID
   * @param tenantId 租户ID
   * @return true 表示已收藏
   */
  boolean existsByUserIdAndNodeId(String userId, String nodeId, String tenantId);

  /**
   * 批量更新排序号。
   *
   * @param userId 用户ID
   * @param nodeId 节点ID
   * @param sortOrder 新排序号
   * @return 受影响行数
   */
  int updateSortOrder(String userId, String nodeId, int sortOrder);
}
