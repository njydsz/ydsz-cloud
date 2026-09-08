package com.njydsz.userinfo.domain.vo;

import java.util.List;

import lombok.Data;

/**
 * 前端动态路由 VO（vben 路由形态）
 *
 * <p>当前用户可访问菜单转换为前端路由配置：{@code component} 为字符串组件标识
 * （布局组件名如 {@code BasicLayout}，或视图路径如 {@code system/user/index}），
 * 由前端 {@code generateAccessible} 通过 {@code pageMap/layoutMap} 解析为真实组件。
 *
 * <p>映射规则（{@code MenuVO} → {@code MenuRouteVO}）：
 *
 * <ul>
 *   <li>{@code name} ← {@code menuCode}（路由唯一标识；为空时回退 {@code menu-{id}}）
 *   <li>{@code path} / {@code component} ← 同名字段直传
 *   <li>{@code meta.title} ← {@code menuName}，{@code meta.icon} ← {@code icon}
 *   <li>{@code meta.order} ← {@code sort}，{@code meta.hideInMenu} ← {@code visible == 0}
 *   <li>{@code children} ← 按 {@code parentId} 递归构建，按 {@code sort} 升序
 * </ul>
 *
 * @author ydsz-team
 * @since 26.09.08
 * @see MenuVO 菜单源数据
 * @see com.njydsz.userinfo.server.service.MenuService#routesForRoles 路由构建入口
 */
@Data
public class MenuRouteVO {

  /** 路由名称（唯一标识，取 menuCode，为空时回退 menu-{id}） */
  private String name;

  /** 路由路径 */
  private String path;

  /** 组件标识（字符串）：布局组件名或视图路径 */
  private String component;

  /** 重定向路径（可选，目录节点使用） */
  private String redirect;

  /** 路由元信息 */
  private Meta meta;

  /** 子路由 */
  private List<MenuRouteVO> children;

  /**
   * 路由元信息（对齐前端 {@code RouteMeta} 所需最小字段集）。
   *
   * @author ydsz-team
   * @since 26.09.08
   */
  @Data
  public static class Meta {

    /** 菜单标题（取 menuName） */
    private String title;

    /** 菜单图标 */
    private String icon;

    /** 排序权重（取 sort，前端据此排序菜单） */
    private Integer order;

    /** 是否在菜单中隐藏（visible == 0 时为 true，路由仍可访问） */
    private Boolean hideInMenu;
  }
}
