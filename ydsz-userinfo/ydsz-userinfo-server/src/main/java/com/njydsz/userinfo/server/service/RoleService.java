package com.njydsz.userinfo.server.service;

import java.util.Collection;
import java.util.List;
import java.util.Map;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;

import com.njydsz.userinfo.domain.dto.RolePageQueryDTO;
import com.njydsz.userinfo.domain.dto.create.RoleCreateDTO;
import com.njydsz.userinfo.domain.dto.update.RoleUpdateDTO;
import com.njydsz.userinfo.infra.entity.RoleDO;
import com.njydsz.userinfo.domain.vo.RoleVO;

/**
 * 角色 Service 接口
 *
 * <p>封装角色的完整业务逻辑：CRUD、权限分配、跨服务名称富化。
 *
 * @author ydsz-team
 * @since 1.0.0
 * @see RoleDO 角色实体
 */
public interface RoleService {

  /**
   * 根据 ID 查询角色详情。
   *
   * @param id 角色 ID
   * @return 角色 VO
   */
  RoleVO getById(String id);

  /**
   * 分页查询角色列表。
   *
   * @param query 分页查询条件
   * @return 分页结果
   */
  Page<RoleVO> page(RolePageQueryDTO query);

  /**
   * 查询全部角色列表（无分页）。
   *
   * @return 角色 VO 列表
   */
  List<RoleVO> list();

  /**
   * 创建角色。
   *
   * @param dto 角色创建 DTO
   * @return 新角色 ID
   */
  String create(RoleCreateDTO dto);

  /**
   * 更新角色。
   *
   * @param dto 角色更新 DTO（含 ID）
   * @return true=成功
   */
  boolean update(RoleUpdateDTO dto);

  /**
   * 删除角色（逻辑删除）。
   *
   * @param id 角色 ID
   * @return true=成功
   */
  boolean removeById(String id);

  /**
   * 为角色分配权限（全量覆盖模式）。
   *
   * @param roleId 角色 ID
   * @param permissionIds 权限 ID 列表
   * @return true=成功
   */
  boolean assignPermissions(String roleId, List<String> permissionIds);

  /**
   * 查询角色的权限 ID 列表。
   *
   * @param roleId 角色 ID
   * @return 权限 ID 列表
   */
  List<String> getRolePermissionIds(String roleId);

  /**
   * 批量查询角色 ID → 角色名映射。
   *
   * @param roleIds 角色 ID 集合
   * @return roleId → roleName 映射
   */
  Map<String, String> batchNamesByIds(Collection<String> roleIds);
}
