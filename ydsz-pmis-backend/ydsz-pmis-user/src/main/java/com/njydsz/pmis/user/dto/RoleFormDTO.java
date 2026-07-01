package com.njydsz.pmis.user.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.util.List;

/**
 * 角色创建/更新 DTO
 *
 * @author ydsz-pmis-team
 * @since 1.0.0
 */
@Data
@Schema(description = "角色表单")
public class RoleFormDTO implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 主键 ID（更新时必填） */
    private Long id;

    /** 角色编码 */
    @NotBlank
    @Size(max = 64)
    private String roleCode;

    /** 角色名称 */
    @NotBlank
    @Size(max = 64)
    private String roleName;

    /** 描述 */
    private String description;

    /** 排序号 */
    private Integer sortOrder;

    /** ALL/DEPT/SELF/CUSTOM */
    private String dataScope = "SELF";

    /** 状态：ENABLED/DISABLED */
    private String status = "ENABLED";

    /** 关联权限 ID 列表 */
    private List<Long> permissionIds;
}
