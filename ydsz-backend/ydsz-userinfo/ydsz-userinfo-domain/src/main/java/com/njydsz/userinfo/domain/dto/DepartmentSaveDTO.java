package com.njydsz.userinfo.domain.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import lombok.Data;

/**
 * 部门创建/更新 DTO（SaveDTO 共用模式）。
 *
 * @author ydsz-team
 * @since 1.0.0
 */
@Data
public class DepartmentSaveDTO {

    /** 部门 ID，更新时必填 */
    private String id;

    /** 部门编码，全局唯一 */
    @NotBlank(message = "部门编码不能为空")
    @Size(max = 64, message = "部门编码长度不能超过 64 个字符")
    private String deptCode;

    /** 部门名称 */
    @NotBlank(message = "部门名称不能为空")
    @Size(max = 128, message = "部门名称长度不能超过 128 个字符")
    private String deptName;

    /** 父部门 ID */
    private String parentId;
    /** 部门描述 */
    @Size(max = 500, message = "描述长度不能超过 500 个字符")
    private String description;
    /** 排序序号 */
    private Integer sortOrder;
    /** 状态：ENABLE-启用、DISABLE-禁用 */
    private String status;
    /** 租户 ID */
    private String tenantId;
}
