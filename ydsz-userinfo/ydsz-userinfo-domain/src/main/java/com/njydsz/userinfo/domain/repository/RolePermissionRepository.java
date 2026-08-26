package com.njydsz.userinfo.domain.repository;

import java.util.List;
import java.util.Optional;

import com.njydsz.userinfo.domain.dto.RolePermissionDTO;
import com.njydsz.userinfo.domain.vo.RolePermissionVO;

/**
 * 角色-权限关联 Repository 接口
 *
 * <p>封装角色-权限关联表（{@code ydsz_rbac_role_permission}）的数据访问操作。
 *
 * <p>入参为 DTO / 具体字段，返回值为 VO 类型，禁止暴露 MyBatis-Plus 类。
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
  List<RolePermissionVO> findByRoleId(String roleId);

  /**
   * 根据角色 ID 查询权限 ID 列表。
   *
   * @param roleId 角色 ID
   * @return 权限 ID 列表
   */
  List<String> findPermissionIdsByRoleId(String roleId);

  /**
   * 根据角色 ID 和权限 ID 查询关联。
   *
   * @param roleId 角色 ID
   * @param permissionId 权限 ID
   * @return 角色-权限关联 VO
   */
  Optional<RolePermissionVO> findByRoleIdAndPermissionId(String roleId, String permissionId);

  /**
   * 保存角色-权限关联（插入）。
   *
   * @param dto 角色-权限关联 DTO
   * @return 保存后的关联 VO
   */
  RolePermissionVO create(RolePermissionDTO dto);

  /**
   * 批量插入角色-权限关联。
   *
   * @param dtoList 关联 DTO 列表
   * @return 插入行数
   */
  int batchInsert(List<RolePermissionDTO> dtoList);

  /**
   * 根据角色 ID 删除关联。
   *
   * @param roleId 角色 ID
   * @return 删除影响的行数
   */
  int deleteByRoleId(String roleId);

  /**
   * 根据角色 ID 和权限 ID 删除关联。
   *
   * @param roleId 角色 ID
   * @param permissionId 权限 ID
   * @return 删除影响的行数
   */
  int deleteByRoleIdAndPermissionId(String roleId, String permissionId);
}
