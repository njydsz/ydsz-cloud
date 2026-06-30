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

    private Long id;

    @NotBlank
    @Size(max = 64)
    private String roleCode;

    @NotBlank
    @Size(max = 64)
    private String roleName;

    private String description;

    private Integer sortOrder;

    /** ALL/DEPT/SELF/CUSTOM */
    private String dataScope = "SELF";

    private String status = "ENABLED";

    /** 关联权限 ID 列表 */
    private List<Long> permissionIds;
}
