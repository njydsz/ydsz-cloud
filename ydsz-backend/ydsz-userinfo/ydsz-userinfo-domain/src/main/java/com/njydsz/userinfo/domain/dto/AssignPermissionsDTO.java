package com.njydsz.userinfo.domain.dto;

import java.util.List;

import jakarta.validation.constraints.NotEmpty;

import lombok.Data;

/**
 * 分配角色权限 DTO，用于批量关联角色与权限。
 *
 * @author ydsz-team
 * @since 1.0.0
 */
@Data
public class AssignPermissionsDTO {

    /** 权限 ID 列表 */
    @NotEmpty(message = "权限 ID 列表不能为空")
    private List<String> permissionIds;
}
