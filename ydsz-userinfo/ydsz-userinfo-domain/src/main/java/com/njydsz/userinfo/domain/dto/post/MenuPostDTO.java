package com.njydsz.userinfo.domain.dto.post;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.io.Serializable;
import java.io.Serial;
/**
 * 菜单新增请求 DTO。
 *
 * <p>对应后端 {@code POST /api/v1/menu} 请求体。
 * 支持目录（DIR）、菜单（MENU）、按钮（BUTTON）三种类型的新增，
 * 新增后自动纳入 RBAC 权限体系。
 *
 * @author ydsz-team
 * @since 1.0.0
 */
@Data
public class MenuPostDTO implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 父菜单 ID（{@code "0"} 表示根节点） */
    private String parentId;

    /** 菜单名称（前端展示） */
    @NotBlank(message = "菜单名称不能为空")
    @Size(max = 64, message = "菜单名称长度不能超过 64 个字符")
    private String menuName;

    /** 菜单编码（全局唯一） */
    @NotBlank(message = "菜单编码不能为空")
    @Size(max = 64, message = "菜单编码长度不能超过 64 个字符")
    private String menuCode;

    /** 菜单类型（DIR=目录 / MENU=菜单 / BUTTON=按钮） */
    @NotBlank(message = "菜单类型不能为空")
    @Size(max = 20, message = "菜单类型长度不能超过 20 个字符")
    private String menuType;

    /** 前端路由路径（menuType=MENU 时必填） */
    @Size(max = 255, message = "路径长度不能超过 255 个字符")
    private String path;

    /** 前端组件路径（menuType=MENU 时必填，如 {@code "system/user/index"}） */
    @Size(max = 255, message = "组件路径长度不能超过 255 个字符")
    private String component;

    /** 菜单图标（Iconify / Element Plus 图标名） */
    @Size(max = 100, message = "图标长度不能超过 100 个字符")
    private String icon;

    /** 同级排序序号（升序） */
    private Integer sortOrder;

    /** 权限码（{@code "system:user:create"} 格式） */
    @Size(max = 100, message = "权限编码长度不能超过 100 个字符")
    private String permissionCode;

    /** 是否前端可见（0=隐藏，1=可见） */
    private Integer visible;

    /** 启用状态（{@code "ENABLED"} / {@code "DISABLED"}） */
    private String status;

}