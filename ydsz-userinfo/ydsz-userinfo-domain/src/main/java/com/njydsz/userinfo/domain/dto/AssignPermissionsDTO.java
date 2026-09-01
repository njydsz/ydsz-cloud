package com.njydsz.userinfo.domain.dto;

import java.io.Serial;
import java.io.Serializable;
import java.util.List;

import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 分配角色权限请求 DTO。
 *
 * <p>用于 {@code Post /api/v1/Role/{roleId}/permissions} 接口，为指定角色分配权限。 采用<b>全量覆盖</b>策略：传入的权限 ID
 * 列表将完全替换角色原有权限关联。
 *
 * <p><b>注意事项：</b>
 *
 * <ul>
 *   <li>传入空列表表示清除角色所有权限
 *   <li>权限 ID 必须为系统中已存在的有效权限（菜单/按钮/API）
 * </ul>
 *
 * @author ydsz-team
 * @since 26.09.01
 */
@Data
public class AssignPermissionsDTO implements Serializable {

  @Serial private static final long serialVersionUID = 1L;

  /** 权限 ID 列表（全量覆盖，空列表表示清除所有权限） */
  @Size(max = 200, message = "单次分配权限数量不能超过 200 个")
  private List<String> permissionIds;
}
