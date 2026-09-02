package com.njydsz.userinfo.domain.vo;

import lombok.Data;

/**
 * 用户-角色关联 VO，用于 Controller 返回，不包含 deleted、createdBy 等内部维护字段。
 *
 * @author ydsz-team
 * @since 26.09.01
 */
@Data
public class UserRoleVO {

  /** 关联唯一标识 */
  private String id;

  /** 用户 ID */
  private String userId;

  /** 角色 ID */
  private String roleId;
}
