package com.njydsz.pmis.user.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;

/**
 * 权限/菜单创建/更新 DTO
 *
 * @author ydsz-pmis-team
 * @since 1.0.0
 */
@Data
@Schema(description = "权限表单")
public class PermissionFormDTO implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    private Long id;

    private Long parentId;

    @NotBlank
    @Schema(description = "权限编码: system:user:create")
    private String permCode;

    @NotBlank
    private String permName;

    /** MENU/BUTTON/API */
    @NotBlank
    private String permType;

    private String path;
    private String component;
    private String icon;
    private Integer sortOrder;
    private Integer visible = 1;
    private String status = "ENABLED";
}
