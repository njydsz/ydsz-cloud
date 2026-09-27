package com.njydsz.userinfo.domain.dto;

import java.io.Serial;
import java.io.Serializable;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

import com.njydsz.common.safe.annotation.Xss;

/**
 * 菜单请求 DTO。
 *
 * <p>同时用于创建和更新场景：创建时 {@code id} 可不传，更新时 {@code id} 必填。
 *
 * @author ydsz-team
 * @since 26.09.01
 */
@Data
public class MenuDTO implements Serializable {

  @Serial private static final long serialVersionUID = 1L;

  /** 菜单 ID（更新时必填） */
  @Xss(message = "{userinfo.param.xss}")
  private String id;

  /** 父菜单 ID（{@code "0"} 表示根节点） */
  @Xss(message = "{userinfo.param.xss}")
  private String parentId;

  /** 菜单名称（前端展示） */
  @NotBlank(message = "{userinfo.param.required}")
  @Size(max = 64, message = "{userinfo.error.max_length}")
  @Xss(message = "{userinfo.param.xss}")
  private String menuName;

  /** 菜单编码（全局唯一） */
  @NotBlank(message = "{userinfo.param.required}")
  @Size(max = 64, message = "{userinfo.error.max_length}")
  @Xss(message = "{userinfo.param.xss}")
  private String menuCode;

  /** 菜单类型（DIR=目录 / Menu=菜单 / BUTTON=按钮） */
  @NotBlank(message = "{userinfo.param.required}")
  @Size(max = 20, message = "{userinfo.error.max_length}")
  @Xss(message = "{userinfo.param.xss}")
  private String menuType;

  /** 前端路由路径（menuType=Menu 时必填） */
  @Size(max = 255, message = "{userinfo.error.max_length}")
  @Xss(message = "{userinfo.param.xss}")
  private String path;

  /** 前端组件路径（menuType=Menu 时必填，如 {@code "system/user/index"}） */
  @Size(max = 255, message = "{userinfo.error.max_length}")
  @Xss(message = "{userinfo.param.xss}")
  private String component;

  /** 菜单图标（Iconify / Element Plus 图标名） */
  @Size(max = 100, message = "{userinfo.error.max_length}")
  @Xss(message = "{userinfo.param.xss}")
  private String icon;

  /** 同级排序序号（升序） */
  private Integer sort;

  /** 权限码（{@code "system:user:create"} 格式） */
  @Size(max = 100, message = "{userinfo.error.max_length}")
  @Xss(message = "{userinfo.param.xss}")
  private String permissionCode;

  /** 是否前端可见（0=隐藏，1=可见） */
  private Integer visible;

  /** 启用状态（{@code "ENABLED"} / {@code "DISABLED"}） */
  @Xss(message = "{userinfo.param.xss}")
  private String status;
}
