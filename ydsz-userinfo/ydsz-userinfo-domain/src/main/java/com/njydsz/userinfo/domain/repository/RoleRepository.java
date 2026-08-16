package com.njydsz.userinfo.domain.repository;

import com.njydsz.userinfo.domain.entity.Role;
import java.util.List;
import java.util.Optional;

/**
 * 角色聚合仓储接口。
 *
 * <p>角色是 RBAC 权限体系的核心聚合根，支持角色-权限关联管理。
 *
 * @author ydsz-team
 * @since 1.0.0
 */
public interface RoleRepository {

  /**
   * 根据 ID 查找角色
   *
   * @param id 角色 ID
   * @return Optional 包装
   */
  Optional<Role> findById(String id);

  /**
   * 根据角色编码查找角色
   *
   * @param roleCode 角色编码（如 PM / FINANCE）
   * @return Optional 包装
   */
  Optional<Role> findByCode(String roleCode);

  /**
   * 查询全部角色
   *
   * @return 全部角色列表
   */
  List<Role> findAll();

  /**
   * 根据角色编码列表查询
   *
   * @param roleCodes 角色编码集合
   * @return 角色列表
   */
  List<Role> findByCodes(List<String> roleCodes);

  /**
   * 保存角色（新增或更新）
   *
   * @param role 角色实体
   * @return 保存后的实体
   */
  Role save(Role role);

  /**
   * 根据 ID 删除角色
   *
   * @param id 角色 ID
   * @return true 表示成功删除
   */
  boolean deleteById(String id);

  /**
   * 判断角色编码是否已存在
   *
   * @param roleCode 角色编码
   * @return true 表示已存在
   */
  boolean existsByCode(String roleCode);

  /**
   * 判断角色是否关联了用户
   *
   * @param roleId 角色 ID
   * @return true 表示已关联用户
   */
  boolean hasUsers(String roleId);
}
