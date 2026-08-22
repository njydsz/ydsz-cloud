package com.njydsz.nextwiki.infra.mapper;

import java.util.List;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;

import com.njydsz.nextwiki.infra.entity.UserFavoriteDO;

/**
 * 用户收藏夹 Mapper
 *
 * <p>对应数据表 {@code nw_user_favorite}。
 *
 * <p><b>主要索引：</b>
 *
 * <ul>
 *   <li>uk_user_favorite_user_node — 用户+节点唯一索引</li>
 *   <li>idx_user_favorite_user_sort — 用户+排序查询索引</li>
 * </ul>
 *
 * <p><b>多租户：</b>由 MyBatis 拦截器自动注入 {@code tenant_id} 过滤条件。
 *
 * @author ydsz-team
 * @since 1.0.0
 */
@Mapper
public interface UserFavoriteMapper extends BaseMapper<UserFavoriteDO> {

  /**
   * 查询用户收藏列表（按排序号升序）。
   *
   * @param userId 用户ID
   * @param tenantId 租户ID
   * @return 收藏列表
   */
  List<UserFavoriteDO> selectByUserId(
      @Param("userId") String userId, @Param("tenantId") String tenantId);

  /**
   * 查询用户收藏列表（分页）。
   *
   * @param userId 用户ID
   * @param tenantId 租户ID
   * @param offset 偏移量
   * @param limit 每页数量
   * @return 收藏分页列表
   */
  List<UserFavoriteDO> selectByUserIdWithPage(
      @Param("userId") String userId,
      @Param("tenantId") String tenantId,
      @Param("offset") int offset,
      @Param("limit") int limit);

  /**
   * 统计用户收藏数量。
   *
   * @param userId 用户ID
   * @param tenantId 租户ID
   * @return 收藏数量
   */
  int countByUserId(@Param("userId") String userId, @Param("tenantId") String tenantId);

  /**
   * 查询用户的最大排序号（用于新增时自动排到最后）。
   *
   * @param userId 用户ID
   * @param tenantId 租户ID
   * @return 最大排序号（无记录时返回 0）
   */
  int selectMaxSortOrder(@Param("userId") String userId, @Param("tenantId") String tenantId);

  /**
   * 检查节点是否已被用户收藏。
   *
   * @param userId 用户ID
   * @param nodeId 节点ID
   * @param tenantId 租户ID
   * @return true 表示已收藏
   */
  boolean existsByUserIdAndNodeId(
      @Param("userId") String userId,
      @Param("nodeId") String nodeId,
      @Param("tenantId") String tenantId);

  /**
   * 删除用户对某节点的收藏记录。
   *
   * @param userId 用户ID
   * @param nodeId 节点ID
   * @return 受影响行数
   */
  @Delete(
      "DELETE FROM nw_user_favorite WHERE user_id = #{userId} AND node_id = #{nodeId}")
  int deleteByUserIdAndNodeId(
      @Param("userId") String userId, @Param("nodeId") String nodeId);

  /**
   * 更新收藏排序号。
   *
   * @param userId 用户ID
   * @param nodeId 节点ID
   * @param sortOrder 新排序号
   * @return 受影响行数
   */
  @Update(
      "UPDATE nw_user_favorite SET sort_order = #{sortOrder}, updated_at = NOW() "
          + "WHERE user_id = #{userId} AND node_id = #{nodeId}")
  int updateSortOrder(
      @Param("userId") String userId,
      @Param("nodeId") String nodeId,
      @Param("sortOrder") int sortOrder);
}
