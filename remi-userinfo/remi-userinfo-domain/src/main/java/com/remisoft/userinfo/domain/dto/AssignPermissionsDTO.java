package com.remisoft.userinfo.domain.dto;

import java.io.Serializable;
import java.io.Serial;
import java.util.List;

import lombok.Data;

/**
 * 分配角色权限请求 DTO。
 *
 * <p>用于 {@code POST /api/v1/role/{roleId}/permissions} 接口，为指定角色分配权限。
 * 采用<b>全量覆盖</b>策略：传入的权限 ID 列表将完全替换角色原有权限关联。
 *
 * <p><b>注意事项：</b>
 * <ul>
 *   <li>传入空列表表示清除角色所有权限</li>
 *   <li>权限 ID 必须为系统中已存在的有效权限（菜单/按钮/API）</li>
 * </ul>
 *
 * @author remi-team
 * @since 1.0.0
 */
@Data
public class AssignPermissionsDTO implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 权限 ID 列表（全量覆盖，空列表表示清除所有权限） */
    private List<String> permissionIds;
}
