package com.njydsz.nextwiki.infra.mapper;

import java.util.List;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import com.njydsz.nextwiki.infra.entity.SpaceMember;

/**
 * 空间成员 Mapper
 *
 * <p>对应数据表 {@code nw_space_member}。
 *
 * @author ydsz-team
 * @since 1.0.0
 */
@Mapper
public interface SpaceMemberMapper extends BaseMapper<SpaceMember> {

  /**
   * 查询空间的成员列表（按角色优先级排序）。
   *
   * @param spaceId 空间ID
   * @return 成员列表
   */
  List<SpaceMember> selectBySpaceId(@Param("spaceId") String spaceId);

  /**
   * 查询用户参与的空间列表。
   *
   * @param userId 用户ID
   * @return 成员列表
   */
  List<SpaceMember> selectByUserId(@Param("userId") String userId);

  /**
   * 根据空间ID和用户ID查找成员。
   *
   * @param spaceId 空间ID
   * @param userId 用户ID
   * @return 成员实体
   */
  SpaceMember selectBySpaceIdAndUserId(
      @Param("spaceId") String spaceId, @Param("userId") String userId);

  /**
   * 统计空间成员数量。
   *
   * @param spaceId 空间ID
   * @return 成员数量
   */
  int countBySpaceId(@Param("spaceId") String spaceId);

  /**
   * 检查用户是否在空间中。
   *
   * @param spaceId 空间ID
   * @param userId 用户ID
   * @return true 表示是成员
   */
  boolean existsBySpaceIdAndUserId(
      @Param("spaceId") String spaceId, @Param("userId") String userId);

  /**
   * 更新成员角色。
   *
   * @param spaceId 空间ID
   * @param userId 用户ID
   * @param role 新角色
   * @return 受影响行数
   */
  int updateRole(
      @Param("spaceId") String spaceId,
      @Param("userId") String userId,
      @Param("role") String role);

  /**
   * 删除成员。
   *
   * @param spaceId 空间ID
   * @param userId 用户ID
   * @return 受影响行数
   */
  int deleteBySpaceIdAndUserId(
      @Param("spaceId") String spaceId, @Param("userId") String userId);
}
