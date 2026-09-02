package com.njydsz.userinfo.domain.vo;

import lombok.Data;

/**
 * 角色 VO，用于 Controller 返回，不包含 deleted、createdBy 等内部维护字段。
 *
 * <p>由 {@code UserInfoConverter.entityToVO()} 从 {@code Role} 实体转换而来。
 *
 * @author ydsz-team
 * @since 26.09.01
 */
@Data
public class RoleVO {

  /** 角色唯一标识 */
  private String id;

  /** 角色编码，全局唯一，用于权限匹配 */
  private String roleCode;

  /** 角色名称 */
  private String roleName;

  /** 角色描述 */
  private String description;

  /** 排序序号，越小越靠前 */
  private Integer sortOrder;

  /** 状态：ENABLE-启用、DISABLE-禁用 */
  private String status;

  /** 是否内置角色，内置角色不允许删除 */
  private Boolean builtIn;
}
