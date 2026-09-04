package com.njydsz.nextwiki.domain.repository;

import java.util.List;


/**
 * 用户最近访问仓储接口
 *
 * <p>定义最近访问数据访问操作，实现类位于 infra 层。
 *
 * @author ydsz-team
 * @since 26.09.01
 */
public interface UserRecentRepository {

  /**
   * 保存访问记录（新增或更新访问时间）。
   *
   * <p>若节点已有访问记录，则更新访问时间和访问类型；否则新增记录。
   *
   * @param dto 访问DTO
   * @return 受影响行数
   */
  int saveOrUpdate(UserRecentDTO dto);

  /**
   * 查询用户最近访问列表（按访问时间倒序，带数量限制）。
   *
   * @param userId 用户ID
   * @param tenantId 租户ID
   * @param limit 返回数量限制
   * @return 访问DTO列表
   */
  List<UserRecentDTO> findByUserIdOrderByAccessedAt(String userId, String tenantId, int limit);

  /**
   * 分页查询用户最近访问列表。
   *
   * @param userId 用户ID
   * @param tenantId 租户ID
   * @param offset 偏移量
   * @param limit 每页数量
   * @return 访问DTO分页列表
   */
  List<UserRecentDTO> findByUserIdWithPage(String userId, String tenantId, int offset, int limit);

  /**
   * 统计用户最近访问记录数量。
   *
   * @param userId 用户ID
   * @param tenantId 租户ID
   * @return 访问记录数量
   */
  int countByUserId(String userId, String tenantId);

  /**
   * 删除用户最早的访问记录（超出容量限制时清理）。
   *
   * @param userId 用户ID
   * @param tenantId 租户ID
   * @param keepCount 保留的记录数量
   * @return 删除的记录数
   */
  int deleteEarliestRecords(String userId, String tenantId, int keepCount);

  /**
   * 逻辑删除指定节点的访问记录。
   *
   * @param userId 用户ID
   * @param nodeId 节点ID
   * @return 受影响行数
   */
  int deleteByUserIdAndNodeId(String userId, String nodeId);
}
