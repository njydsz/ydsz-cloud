package com.njydsz.userinfo.domain.vo;

import lombok.Data;

/**
 * 角色-权限关联 VO，用于 Controller 返回，不包含 deleted、createdBy 等内部维护字段。
 *
 * @author ydsz-team
 * @since 1.0.0
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
