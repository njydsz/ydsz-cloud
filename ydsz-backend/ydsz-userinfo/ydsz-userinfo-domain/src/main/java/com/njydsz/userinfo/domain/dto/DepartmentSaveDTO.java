package com.njydsz.userinfo.domain.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import lombok.Data;

/**
 * 部门创建/更新 DTO。
 *
 * @author ydsz-team
 * @since 1.0.0
 */
@Data
public class DepartmentSaveDTO {

    private String id;

    @NotBlank(message = "部门编码不能为空")
    @Size(max = 64, message = "部门编码长度不能超过 64 个字符")
    private String deptCode;

    @NotBlank(message = "部门名称不能为空")
    @Size(max = 128, message = "部门名称长度不能超过 128 个字符")
    private String deptName;

    private String parentId;
    @Size(max = 500, message = "描述长度不能超过 500 个字符")
    private String description;
    private Integer sortOrder;
    private String status;
    private String tenantId;
}
