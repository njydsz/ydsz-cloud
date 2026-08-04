package com.njydsz.userinfo.domain.dto.post;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.io.Serializable;
import java.io.Serial;
/**
 * 角色新增请求 DTO。
 *
 * <p>对应后端 {@code POST /api/v1/role} 请求体。
 * 新增角色后自动纳入 RBAC 权限体系，通过 {@link #roleCode} 被各业务模块引用。
 *
 * @author ydsz-team
 * @since 1.0.0
 */
@Data
public class RolePostDTO implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 角色编码（全局唯一，建议格式 {@code ROLE_XXX}） */
    @NotBlank(message = "角色编码不能为空")
    @Size(max = 64, message = "角色编码长度不能超过 64 个字符")
    private String roleCode;

    /** 角色名称（前端展示） */
    @NotBlank(message = "角色名称不能为空")
    @Size(max = 64, message = "角色名称长度不能超过 64 个字符")
    private String roleName;

    /** 角色描述 */
    @Size(max = 500, message = "描述长度不能超过 500 个字符")
    private String description;

    /** 同级排序序号（升序） */
    private Integer sortOrder;

    /** 数据权限范围（ALL / DEPT_AND_CHILD / DEPT / SELF / CUSTOM） */
    private String dataScope;

    /** 启用状态（{@code "ENABLED"} / {@code "DISABLED"}） */
    private String status;

    /** 是否内置角色（{@code true} 时禁止删除与修改编码） */
    private Boolean builtIn;

    /** 租户 ID（{@code "0"} = 平台级角色） */
    private String tenantId;

}