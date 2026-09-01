package com.njydsz.userinfo.domain.vo;

import java.util.List;

import lombok.Data;

/**
 * 菜单树形结构 VO，用于前端动态路由渲染。
 *
 * <p>由 {@code MenuServiceImpl.buildMenuTree()} 构建递归树， 前端根据该树渲染侧边栏菜单和路由配置。
 *
 * @author ydsz-team
 * @since 26.09.01
 */
@Data
public class MenuTreeVO {

  /** 菜单唯一标识 */
  private String id;

  /** 父菜单 ID，根菜单为 0 或 null */
  private String parentId;

  /** 菜单名称 */
  private String menuName;

  /** 菜单编码 */
  private String menuCode;

  /** 菜单类型：DIRECTORY-目录、Menu-菜单、BUTTON-按钮 */
  private String menuType;

  /** 前端路由路径 */
  private String path;

  /** 前端组件路径 */
  private String component;

  /** 菜单图标 */
  private String icon;

  /** 排序序号 */
  private Integer sortOrder;

  /** 权限标识 */
  private String permissionCode;

  /** 是否可见：1-可见、0-隐藏 */
  private Integer visible;

  /** 状态：ENABLE-启用、DISABLE-禁用 */
  private String status;

  /** 子菜单列表 */
  private List<MenuTreeVO> children;
}
