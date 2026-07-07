package com.njydsz.pmis.userinfo.dto;

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
    private static final String serialVersionUID = "1";

    /** 主键 ID（更新时必填） */
    private String id;

    /** 父权限 ID（0=根） */
    private String parentId;

    @NotBlank
    @Schema(description = "权限编码: system:user:create")
    private String permCode;

    /** 权限名称 */
    @NotBlank
    private String permName;

    /** MENU/BUTTON/API */
    @NotBlank
    private String permType;

    /** 路由路径 */
    private String path;
    /** 组件路径 */
    private String component;
    /** 菜单图标 */
    private String icon;
    /** 排序号 */
    private Integer sortOrder;
    /** 1=显示, 0=隐藏 */
    private Integer visible = 1;
    /** 状态：ENABLED/DISABLED */
    private String status = "ENABLED";
}
