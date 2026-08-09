package com.njydsz.userinfo.domain.dto.put;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.io.Serializable;
import java.io.Serial;
/**
 * 菜单修改请求 DTO。
 *
 * <p>对应后端 {@code PUT /api/v1/menu} 请求体。
 * 修改时 {@link #id} 必填，其余字段按需填写，未传字段保持原值不变。
 *
 * @author ydsz-team
 * @since 1.0.0
 */
@Data
public class MenuPutDTO implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 菜单 ID（必填） */
    @NotBlank(message = "ID不能为空")
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

    /** 菜单类型（DIR=目录 / MENU=菜单 / BUTTON=按钮） */
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
