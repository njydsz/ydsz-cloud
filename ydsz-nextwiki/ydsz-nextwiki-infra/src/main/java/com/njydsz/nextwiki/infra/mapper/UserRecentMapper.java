package com.njydsz.nextwiki.infra.mapper;

import java.util.List;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import com.njydsz.nextwiki.infra.entity.UserRecentDO;

/**
 * 用户最近访问 Mapper
 *
 * <p>对应数据表 {@code nw_user_recent}。
 *
 * <p><b>主要索引：</b>
 *
 * <ul>
 *   <li>uk_user_recent_user_node — 用户+节点唯一索引</li>
 *   <li>idx_user_recent_user_accessed — 用户+访问时间倒序查询索引</li>
 * </ul>
 *
 * <p><b>多租户：</b>由 MyBatis 拦截器自动注入 {@code tenant_id} 过滤条件。
 *
 * <p><b>容量限制：</b>每个用户最多保留 100 条最近访问记录，超限时自动清理最早的记录。
 *
 * @author ydsz-team
 * @since 1.0.0
 */
@Mapper
public interface UserRecentMapper extends BaseMapper<UserRecentDO> {

  /**
   * 查询用户最近访问列表（按访问时间倒序）。
   *
   * @param userId 用户ID
   * @param tenantId 租户ID
   * @param limit 返回数量限制
   * @return 最近访问列表
   */
  List<UserRecentDO> selectByUserIdOrderByAccessedAt(
      @Param("userId") String userId,
      @Param("tenantId") String tenantId,
      @Param("limit") int limit);

  /**
   * 查询用户最近访问列表（分页）。
   *
   * @param userId 用户ID
   * @param tenantId 租户ID
   * @param offset 偏移量
   * @param limit 每页数量
   * @return 最近访问分页列表
   */
  List<UserRecentDO> selectByUserIdWithPage(
      @Param("userId") String userId,
      @Param("tenantId") String tenantId,
      @Param("offset") int offset,
      @Param("limit") int limit);

  /**
   * 统计用户最近访问记录数量。
   *
   * @param userId 用户ID
   * @param tenantId 租户ID
   * @return 记录数量
   */
  int countByUserId(@Param("userId") String userId, @Param("tenantId") String tenantId);

  /**
   * 更新节点访问时间（访问记录已存在时）。
   *
   * @param userId 用户ID
   * @param nodeId 节点ID
   * @param accessType 访问类型
   * @return 受影响行数
   */
  int updateAccessTime(
      @Param("userId") String userId,
      @Param("nodeId") String nodeId,
      @Param("accessType") String accessType);

  /**
   * 删除用户最早的最近访问记录（超出容量限制时清理）。
   *
   * @param userId 用户ID
   * @param tenantId 租户ID
   * @param keepCount 保留的记录数量
   * @return 删除的记录数
   */
  int deleteEarliestRecords(
      @Param("userId") String userId,
      @Param("tenantId") String tenantId,
      @Param("keepCount") int keepCount);

  /**
   * 删除用户对某节点的最近访问记录。
   *
   * @param userId 用户ID
   * @param nodeId 节点ID
   * @return 受影响行数
   */
  @Delete("DELETE FROM nw_user_recent WHERE user_id = #{userId} AND node_id = #{nodeId}")
  int deleteByUserIdAndNodeId(
      @Param("userId") String userId, @Param("nodeId") String nodeId);
}
