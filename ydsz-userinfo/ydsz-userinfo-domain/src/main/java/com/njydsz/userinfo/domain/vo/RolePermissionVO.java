package com.njydsz.userinfo.domain.vo;

import lombok.Data;

/**
 * 角色-权限关联视图对象。
 *
 * <p>表示角色与菜单/权限之间的分配关系。通过 roleId 关联角色，permissionId 关联菜单或权限项，
 * menuId 标识关联的菜单（可空，纯按钮级权限无对应菜单）。
 *
 * <p>不包含 deleted、createdBy 等内部维护字段。
 *
 * @author ydsz-team
 * @since 26.09.01
 */
@Data
public class RolePermissionVO {

  /** 关联唯一标识 */
  private String id;

  /** 角色 ID */
  private String roleId;

  /** 权限 ID（指向 ydsz_rbac_menu.id） */
  private String permissionId;

  /** 关联菜单 ID（可空，纯按钮级权限无对应菜单） */
  private String menuId;
}
