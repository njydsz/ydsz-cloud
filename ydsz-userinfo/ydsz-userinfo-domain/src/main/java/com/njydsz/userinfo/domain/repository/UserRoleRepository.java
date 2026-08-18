package com.njydsz.userinfo.domain.repository;

import java.util.List;

import com.njydsz.userinfo.infra.entity.UserRoleDO;

/**
 * 用户-角色关联 Repository 接口
 *
 * <p>封装用户-角色关联表（{@code ydsz_user_role}）的数据访问操作。
 *
 * <p>禁止暴露底层 Mapper，所有数据库操作通过本接口进行。
 *
 * @author ydsz-team
 * @since 1.0.0
 */
public interface UserRoleRepository {

  /**
   * 根据用户 ID 查询用户-角色关联列表。
   *
   * @param userId 用户 ID
   * @return 用户-角色关联列表
   */
  List<UserRoleDO> findByUserId(String userId);

  /**
   * 根据用户 ID 查询角色 ID 列表。
   *
   * @param userId 用户 ID
   * @return 角色 ID 列表
   */
  List<String> findRoleIdsByUserId(String userId);

  /**
   * 根据用户 ID 和角色 ID 查询关联。
   *
   * @param userId 用户 ID
   * @param roleId 角色 ID
   * @return 用户-角色关联实体，不存在时返回 null
   */
  UserRoleDO findByUserIdAndRoleId(String userId, String roleId);

  /**
   * 条件查询用户-角色关联列表。
   *
   * @param wrapper 查询条件
   * @return 关联列表
   */
  List<UserRoleDO> list(
      com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<UserRoleDO> wrapper);

  /**
   * 保存用户-角色关联（插入）。
   *
   * @param entity 用户-角色关联实体
   * @return 插入影响的行数
   */
  int insert(UserRoleDO entity);

  /**
   * 批量插入用户-角色关联。
   *
   * @param list 关联列表
   * @return 插入行数
   */
  int batchInsert(List<UserRoleDO> list);

  /**
   * 根据用户 ID 删除关联。
   *
   * @param userId 用户 ID
   * @return 删除影响的行数
   */
  int deleteByUserId(String userId);

  /**
   * 条件删除用户-角色关联。
   *
   * @param wrapper 删除条件
   * @return 删除影响的行数
   */
  int delete(
      com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<UserRoleDO> wrapper);

  /**
   * 统计符合条件的关联数量。
   *
   * @param wrapper 查询条件
   * @return 关联数量
   */
  long count(
      com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<UserRoleDO> wrapper);
}
