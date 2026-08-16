package com.njydsz.userinfo.domain.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;
import com.njydsz.common.jdbc.entity.MpBaseEntity;

/**
 * 菜单/权限实体
 *
 * <p>对应数据库表 {@code ydsz_menu}，存储系统菜单与权限点。
 * 菜单是 RBAC 模型中最细粒度的「权限点」，既可以表示前端路由节点，也可以表示后端接口权限码。
 *
 * <p><b>核心字段：</b>
 * <ul>
 *   <li>{@code parentId}：父菜单 ID，支持无限级树形结构（{@code "0" = 根节点}）</li>
 *   <li>{@code menuCode}：菜单编码（业务侧引用，全局唯一）</li>
 *   <li>{@code menuType}：菜单类型（DIR=目录 / MENU=菜单 / BUTTON=按钮）</li>
 *   <li>{@code path} / {@code component}：前端路由路径与组件路径</li>
 *   <li>{@code icon}：菜单图标（Iconify / Element Plus 图标名）</li>
 *   <li>{@code permissionCode}：权限码（{@code "system:user:create"} 格式，被后端 {@code @AuthApiPermission} 引用）</li>
 *   <li>{@code visible}：是否前端可见（0=隐藏但仍参与鉴权，1=可见）</li>
 *   <li>{@code sortOrder}：同级排序序号（升序）</li>
 * </ul>
 *
 * <p><b>典型使用：</b>
 * <ul>
 *   <li>前端管理系统按 menuType=MENU/PERMISSION 渲染菜单树</li>
 *   <li>后端通过 {@code @AuthApiPermission(apiCodes = "system:user:create")} 引用 permissionCode 做接口鉴权</li>
 *   <li>角色-菜单关联通过 {@link RolePermission} 中间表维护</li>
 * </ul>
 *
 * <p><b>索引设计：</b>唯一索引 {@code uk_menu_code}（{@code menu_code}），普通索引 {@code idx_parent_id}（{@code parent_id}）。
 *
 * @author ydsz-team
 * @since 1.0.0
 *
 * @see RolePermission 角色-菜单权限中间表
 * @see com.njydsz.userinfo.web.controller.MenuController 菜单 Controller
 */
@Data
@SuperBuilder
@NoArgsConstructor
@EqualsAndHashCode(callSuper = true)
@TableName("ydsz_menu")
public class Menu extends MpBaseEntity<String> {

    /** 父菜单 ID，根节点为 {@code "0"}，支持无限级树形结构 */
    private String parentId;

    /** 菜单名称（前端展示） */
    private String menuName;

    /** 菜单编码（业务侧引用，全局唯一） */
    private String menuCode;

    /** 菜单类型（DIR=目录 / MENU=菜单 / BUTTON=按钮） */
    private String menuType;

    /** 前端路由路径（menuType=MENU 时使用） */
    private String path;

    /** 前端组件路径（menuType=MENU 时使用，如 {@code "system/user/index"}） */
    private String component;

    /** 菜单图标（Iconify / Element Plus 图标名） */
    private String icon;

    /** 同级排序序号（升序） */
    private Integer sortOrder;

    /** 权限码（{@code "system:user:create"} 格式，被后端 {@code @AuthApiPermission} 引用） */
    private String permissionCode;

    /** 是否前端可见（0=隐藏但仍参与鉴权，1=可见） */
    private Integer visible;

    /** 启用状态（ENABLED / DISABLED） */
    private String status;
}
