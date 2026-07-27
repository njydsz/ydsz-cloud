package com.njydsz.userinfo.domain.dto.post;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.io.Serializable;
import java.io.Serial;
/**
 * Department 新增请求 DTO。
 *
 * @author ydsz-team
 * @since 1.0.0
 */
@Data
public class DepartmentPostDTO implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

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