package com.njydsz.userinfo.domain.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import lombok.Data;

/**
 * 角色创建/更新 DTO（SaveDTO 共用模式）。
 *
 * @author ydsz-team
 * @since 1.0.0
 */
@Data
public class RoleSaveDTO {

    /** 角色 ID，更新时必填 */
    private String id;

    /** 角色编码，全局唯一 */
    @NotBlank(message = "角色编码不能为空")
    @Size(max = 64, message = "角色编码长度不能超过 64 个字符")
    private String roleCode;

    /** 角色名称 */
    @NotBlank(message = "角色名称不能为空")
    @Size(max = 64, message = "角色名称长度不能超过 64 个字符")
    private String roleName;

    /** 角色描述 */
    @Size(max = 500, message = "描述长度不能超过 500 个字符")
    private String description;

    /** 排序序号 */
    private Integer sortOrder;
    /** 数据权限范围：ALL/DEPT/SELF */
    private String dataScope;
    /** 状态：ENABLE-启用、DISABLE-禁用 */
    private String status;
    /** 是否内置角色 */
    private Boolean builtIn;
    /** 租户 ID */
    private String tenantId;
}
