package com.njydsz.userinfo.domain.dto.put;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.io.Serializable;
import java.io.Serial;
/**
 * 角色修改请求 DTO。
 *
 * <p>对应后端 {@code PUT /api/v1/role} 请求体。
 * 修改时 {@link #id} 必填，内置角色（{@code builtIn=true}）禁止修改 {@code roleCode}。
 *
 * @author ydsz-team
 * @since 1.0.0
 */
@Data
public class RolePutDTO implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 角色 ID（必填） */
    @NotBlank(message = "ID不能为空")
    private String id;

    /** 角色编码（内置角色禁止修改） */
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

    /** 同级排序序号（升序） */
    private Integer sortOrder;

    /** 数据权限范围（ALL / DEPT_AND_CHILD / DEPT / SELF / CUSTOM） */
    private String dataScope;

    /** 启用状态（{@code "ENABLED"} / {@code "DISABLED"}） */
    private String status;

    /** 是否内置角色（{@code true} 时禁止删除） */
    private Boolean builtIn;

    /** 租户 ID */
    private String tenantId;

}
