package com.njydsz.userinfo.domain.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

import com.njydsz.common.jdbc.entity.MpBaseEntity;

/**
 * 角色-权限关联实体
 *
 * <p>对应数据库表 {@code ydsz_rbac_role_permission}，是 RBAC 模型中连接角色与权限的多对多中间表。 「权限」在系统中由 {@link
 * Menu#getPermissionCode()} 表示（{@code "system:user:create"} 格式）， 既可以是菜单级权限，也可以是按钮级权限。
 *
 * <p><b>设计要点：</b>
 *
 * <ul>
 *   <li>每条记录表示「该角色拥有该权限」
 *   <li>{@code permissionId} 实际指向 {@link Menu#getId()}，但语义上不局限于菜单
 *   <li>{@code menuId} 可为空：纯按钮级权限无对应菜单节点
 *   <li>由 {@link com.njydsz.userinfo.web.controller.RoleController#assignPermissions} 维护
 * </ul>
 *
 * <p><b>权限码格式：</b>{@code <module>:<resource>:<action>}，示例：
 *
 * <ul>
 *   <li>{@code system:user:create} — 系统-用户-创建
 *   <li>{@code system:user:export} — 系统-用户-导出
 *   <li>{@code project:contract:approve} — 项目-合同-审批
 * </ul>
 *
 * <p><b>索引设计：</b>普通索引 {@code idx_role_id}（{@code role_id}）、 {@code idx_permission_id}（{@code
 * permission_id}）。如需强唯一性（角色-权限一一对应）， 可加唯一索引 {@code uk_role_permission}（{@code role_id,
 * permission_id}）。
 *
 * @author ydsz-team
 * @since 26.09.01
 * @see Role 角色实体
 * @see Menu 菜单/权限实体
 * @see com.njydsz.userinfo.web.controller.RoleController 角色 Controller（含 {@code assignPermissions}
 *     接口）
 */
@Data
@SuperBuilder
@NoArgsConstructor
@EqualsAndHashCode(callSuper = true)
@TableName("ydsz_rbac_role_permission")
public class RolePermission extends MpBaseEntity<String> {

  /** 角色 ID，关联 {@link Role#getId()} */
  private String roleId;

  /**
   * 权限 ID，实际指向 {@link Menu#getId()}。
   *
   * <p>语义上为「权限点」而非「菜单节点」，但物理外键指向 {@code ydsz_rbac_menu.id}。
   */
  private String permissionId;

  /**
   * 关联菜单 ID（可空）。
   *
   * <p>用于按钮级权限：纯按钮权限无对应菜单（{@code menuId=null}）， 但仍记录到中间表用于后端 {@code @AuthApiPermission} 鉴权。
   */
  private String menuId;
}
