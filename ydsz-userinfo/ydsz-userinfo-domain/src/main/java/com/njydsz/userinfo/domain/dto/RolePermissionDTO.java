package com.njydsz.userinfo.domain.dto;

import java.io.Serial;
import java.io.Serializable;

import lombok.Data;

/**
 * 角色-权限关联 DTO。
 *
 * <p>用于批量分配权限给角色。
 *
 * @author ydsz-team
 * @since 1.0.0
 */
@Data
public class RolePermissionDTO implements Serializable {

  @Serial private static final long serialVersionUID = 1L;

  /** 角色 ID */
  private String roleId;

  /** 权限 ID（指向 ydsz_rbac_menu.id） */
  private String permissionId;

  /** 关联菜单 ID（可空 */
  private String menuId;
}
