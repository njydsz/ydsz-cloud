package com.njydsz.userinfo.domain.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import lombok.Data;

/**
 * 菜单创建/更新 DTO。
 *
 * @author ydsz-team
 * @since 1.0.0
 */
@Data
public class MenuSaveDTO {

    private String id;

    private String parentId;

    @NotBlank(message = "菜单名称不能为空")
    @Size(max = 64, message = "菜单名称长度不能超过 64 个字符")
    private String menuName;

    @NotBlank(message = "菜单编码不能为空")
    @Size(max = 64, message = "菜单编码长度不能超过 64 个字符")
    private String menuCode;

    @NotBlank(message = "菜单类型不能为空")
    @Size(max = 20, message = "菜单类型长度不能超过 20 个字符")
    private String menuType;

    @Size(max = 255, message = "路径长度不能超过 255 个字符")
    private String path;

    @Size(max = 255, message = "组件路径长度不能超过 255 个字符")
    private String component;

    @Size(max = 100, message = "图标长度不能超过 100 个字符")
    private String icon;

    private Integer sortOrder;

    @Size(max = 100, message = "权限编码长度不能超过 100 个字符")
    private String permissionCode;

    private Integer visible;
    private String status;
}
