package com.njydsz.userinfo.domain.dto;

import java.io.Serial;
import java.io.Serializable;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

import com.njydsz.common.safe.annotation.Xss;

/**
 * 角色请求 DTO。
 *
 * <p>同时用于创建和更新场景：创建时 {@code id} 可不传，更新时 {@code id} 必填。
 *
 * <p><b>字段语义：</b>
 *
 * <ul>
 *   <li>{@code roleCode} — 角色编码（全局唯一，建议格式 ROLE_XXX），用于权限匹配</li>
 *   <li>{@code dataScope} — 数据权限范围（ALL/DEPT_AND_CHILD/DEPT/SELF/CUSTOM），控制角色可见数据范围</li>
 *   <li>{@code isBuiltIn} — 是否内置角色（true 时禁止删除与修改编码）</li>
 *   <li>{@code tenantId} — 租户 ID（"0" = 平台级角色，跨租户可见）</li>
 * </ul>
 *
 * @author ydsz-team
 * @since 26.10.01
 */
@Data
public class RoleDTO implements Serializable {

  @Serial private static final long serialVersionUID = 1L;

  /** 角色 ID（更新时必填） */
  @Xss(message = "{userinfo.param.xss}")
  private String id;

  /** 角色编码（全局唯一，建议格式 {@code ROLE_XXX}） */
  @NotBlank(message = "{userinfo.param.required}")
  @Size(max = 64, message = "{userinfo.error.max_length}")
  @Xss(message = "{userinfo.param.xss}")
  private String roleCode;

  /** 角色名称（前端展示） */
  @NotBlank(message = "{userinfo.param.required}")
  @Size(max = 64, message = "{userinfo.error.max_length}")
  @Xss(message = "{userinfo.param.xss}")
  private String roleName;

  /** 角色描述 */
  @Size(max = 500, message = "{userinfo.error.max_length}")
  @Xss(message = "{userinfo.param.xss}")
  private String description;

  /** 同级排序序号（升序） */
  private Integer sort;

  /** 数据权限范围（ALL / DEPT_AND_CHILD / DEPT / SELF / CUSTOM） */
  @Xss(message = "{userinfo.param.xss}")
  private String dataScope;

  /** 启用状态（{@code "ENABLED"} / {@code "DISABLED"}） */
  @Xss(message = "{userinfo.param.xss}")
  private String status;

  /** 是否内置角色（{@code true} 时禁止删除与修改编码） */
  private Boolean isBuiltIn;

  /** 租户 ID（{@code "0"} = 平台级角色） */
  @Xss(message = "{userinfo.param.xss}")
  private String tenantId;
}
