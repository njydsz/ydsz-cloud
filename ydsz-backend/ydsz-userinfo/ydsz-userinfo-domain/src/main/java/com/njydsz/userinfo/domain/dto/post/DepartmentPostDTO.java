package com.njydsz.userinfo.domain.dto.post;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.io.Serializable;
import java.io.Serial;
/**
 * 部门新增请求 DTO。
 *
 * <p>对应后端 {@code POST /api/v1/department} 请求体。
 * 新增时需要指定所属父级部门、部门编码名称、排序及初始状态。
 *
 * @author ydsz-team
 * @since 1.0.0
 */
@Data
public class DepartmentPostDTO implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 部门编码（全局唯一，建议格式 {@code DEPT_XXX}） */
    @NotBlank(message = "部门编码不能为空")
    @Size(max = 64, message = "部门编码长度不能超过 64 个字符")
    private String deptCode;

    /** 部门名称（前端展示） */
    @NotBlank(message = "部门名称不能为空")
    @Size(max = 128, message = "部门名称长度不能超过 128 个字符")
    private String deptName;

    /** 父部门 ID（{@code "0"} 表示根部门） */
    private String parentId;

    /** 部门描述 */
    @Size(max = 500, message = "描述长度不能超过 500 个字符")
    private String description;

    /** 同级排序序号（升序） */
    private Integer sortOrder;

    /** 启用状态（{@code "ENABLED"} / {@code "DISABLED"}） */
    private String status;

    /** 租户 ID */
    private String tenantId;

}