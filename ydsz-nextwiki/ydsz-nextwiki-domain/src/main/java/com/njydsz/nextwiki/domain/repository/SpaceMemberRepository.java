package com.njydsz.nextwiki.domain.repository;

import java.util.List;
import java.util.Optional;

import com.njydsz.nextwiki.domain.dto.SpaceMemberDTO;

/**
 * 空间成员仓储接口
 *
 * @author ydsz-team
 * @since 1.2.0
 */
public interface SpaceMemberRepository {

  /**
   * 保存成员记录。
   *
   * @param dto 成员DTO
   * @return 受影响行数
   */
  int save(SpaceMemberDTO dto);

  /**
   * 更新成员角色。
   *
   * @param spaceId 空间ID
   * @param userId 用户ID
   * @param role 新角色
   * @return 受影响行数
   */
  int updateRole(String spaceId, String userId, String role);

  /**
   * 删除成员。
   *
   * @param spaceId 空间ID
   * @param userId 用户ID
   * @return 受影响行数
   */
  int deleteBySpaceIdAndUserId(String spaceId, String userId);

  /**
   * 根据空间ID和用户ID查找成员。
   *
   * @param spaceId 空间ID
   * @param userId 用户ID
   * @return 成员DTO
   */
  Optional<SpaceMemberDTO> findBySpaceIdAndUserId(String spaceId, String userId);

  /**
   * 查询空间的成员列表。
   *
   * @param spaceId 空间ID
   * @return 成员DTO列表
   */
  List<SpaceMemberDTO> findBySpaceId(String spaceId);

  /**
   * 查询用户参与的空间列表。
   *
   * @param userId 用户ID
   * @return 成员DTO列表
   */
  List<SpaceMemberDTO> findByUserId(String userId);

  /**
   * 统计空间成员数量。
   *
   * @param spaceId 空间ID
   * @return 成员数量
   */
  int countBySpaceId(String spaceId);

  /**
   * 检查用户是否在空间中。
   *
   * @param spaceId 空间ID
   * @param userId 用户ID
   * @return true 表示是成员
   */
  boolean existsBySpaceIdAndUserId(String spaceId, String userId);
}
