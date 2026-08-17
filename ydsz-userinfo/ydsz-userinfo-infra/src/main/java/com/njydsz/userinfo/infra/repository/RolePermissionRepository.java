package com.njydsz.userinfo.infra.repository;

import java.util.List;

import com.njydsz.userinfo.domain.entity.RolePermission;

/**
 * 角色-权限关联 Repository 接口
 *
 * <p>封装角色-权限关联表（{@code ydsz_role_permission}）的数据访问操作。
 *
 * <p>禁止暴露底层 Mapper，所有数据库操作通过本接口进行。
 *
 * @author ydsz-team
 * @since 1.0.0
 */
public interface RolePermissionRepository {

  /**
   * 根据角色 ID 查询角色-权限关联列表。
   *
   * @param roleId 角色 ID
   * @return 角色-权限关联列表
   */
  List<RolePermission> findByRoleId(String roleId);

  /**
   * 根据角色 ID 查询权限 ID 列表。
   *
   * @param roleId 角色 ID
   * @return 权限 ID 列表
   */
  List<String> findMenuIdsByRoleId(String roleId);

  /**
   * 条件查询角色-权限关联列表。
   *
   * @param wrapper 查询条件
   * @return 关联列表
   */
  List<RolePermission> list(
      com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<RolePermission> wrapper);

  /**
   * 批量插入角色-权限关联。
   *
   * @param list 关联列表
   * @return 插入行数
   */
  int batchInsert(List<RolePermission> list);

  /**
   * 根据角色 ID 删除关联。
   *
   * @param roleId 角色 ID
   * @return 删除影响的行数
   */
  int deleteByRoleId(String roleId);

  /**
   * 条件删除角色-权限关联。
   *
   * @param wrapper 删除条件
   * @return 删除影响的行数
   */
  int delete(
      com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<RolePermission> wrapper);
}
