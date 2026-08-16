package com.njydsz.userinfo.domain.vo;

import lombok.Data;

/**
 * 菜单 VO，扁平结构，用于 Controller 列表返回。
 *
 * <p>不包含 deleted、createdBy 等内部维护字段。 树形结构请使用 {@link MenuTreeVO}。
 *
 * @author ydsz-team
 * @since 1.0.0
 */
@Data
public class MenuVO {

  /** 菜单唯一标识 */
  private String id;

  /** 父菜单 ID，根菜单为 0 或 null */
  private String parentId;

  /** 菜单名称 */
  private String menuName;

  /** 菜单编码，全局唯一 */
  private String menuCode;

  /** 菜单类型：DIRECTORY-目录、MENU-菜单、BUTTON-按钮 */
  private String menuType;

  /** 前端路由路径 */
  private String path;

  /** 前端组件路径 */
  private String component;

  /** 菜单图标 */
  private String icon;

  /** 排序序号，越小越靠前 */
  private Integer sortOrder;

  /** 权限标识，用于按钮级权限控制 */
  private String permissionCode;

  /** 是否可见：1-可见、0-隐藏 */
  private Integer visible;

  /** 状态：ENABLE-启用、DISABLE-禁用 */
  private String status;
}
