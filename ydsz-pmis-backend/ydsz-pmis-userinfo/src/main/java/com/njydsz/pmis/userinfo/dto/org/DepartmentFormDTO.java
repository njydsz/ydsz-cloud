package com.njydsz.pmis.userinfo.dto.org;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;

/**
 * 部门创建/更新 DTO
 *
 * @author ydsz-pmis-team
 * @since 1.0.0
 */
@Data
@Schema(description = "部门表单")
public class DepartmentFormDTO implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    @Schema(description = "部门 ID（更新时必填）")
    private String id;

    @NotBlank
    @Size(max = 64)
    @Schema(description = "部门编码", requiredMode = Schema.RequiredMode.REQUIRED)
    private String deptCode;

    @NotBlank
    @Size(max = 128)
    @Schema(description = "部门名称")
    private String deptName;

    @Schema(description = "父部门 ID（0=根）")
    private String parentId;

    @Schema(description = "排序")
    private Integer sortOrder;

    @Schema(description = "部门负责人 ID")
    private String leaderId;

    @Schema(description = "电话")
    private String phone;

    @Schema(description = "邮箱")
    private String email;

    @Schema(description = "描述")
    private String description;

    @Schema(description = "状态 ENABLED/DISABLED")
    private String status;
}
