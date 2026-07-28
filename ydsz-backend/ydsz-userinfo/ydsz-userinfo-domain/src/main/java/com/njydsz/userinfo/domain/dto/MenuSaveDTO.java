package com.njydsz.userinfo.domain.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import lombok.Data;

/**
 * 菜单创建/更新 DTO（SaveDTO 共用模式）。
 *
 * @author ydsz-team
 * @since 1.0.0
 */
@Data
public class MenuSaveDTO {

    /** 菜单 ID，更新时必填 */
    private String id;
    /** 父菜单 ID */
    private String parentId;

    /** 菜单名称 */
    @NotBlank(message = "菜单名称不能为空")
    @Size(max = 64, message = "菜单名称长度不能超过 64 个字符")
    private String menuName;

    /** 菜单编码 */
    @NotBlank(message = "菜单编码不能为空")
    @Size(max = 64, message = "菜单编码长度不能超过 64 个字符")
    private String menuCode;

    /** 菜单类型：DIRECTORY/MENU/BUTTON */
    @NotBlank(message = "菜单类型不能为空")
    @Size(max = 20, message = "菜单类型长度不能超过 20 个字符")
    private String menuType;

    /** 前端路由路径 */
    @Size(max = 255, message = "路径长度不能超过 255 个字符")
    private String path;

    /** 前端组件路径 */
    @Size(max = 255, message = "组件路径长度不能超过 255 个字符")
    private String component;

    /** 菜单图标 */
    @Size(max = 100, message = "图标长度不能超过 100 个字符")
    private String icon;

    /** 排序序号 */
    private Integer sortOrder;

    /** 权限标识 */
    @Size(max = 100, message = "权限编码长度不能超过 100 个字符")
    private String permissionCode;

    /** 是否可见：1-可见、0-隐藏 */
    private Integer visible;
    /** 状态：ENABLE-启用、DISABLE-禁用 */
    private String status;
}
