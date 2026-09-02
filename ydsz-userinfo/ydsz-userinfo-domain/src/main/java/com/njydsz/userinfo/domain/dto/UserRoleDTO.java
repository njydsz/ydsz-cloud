package com.njydsz.userinfo.domain.dto;

import java.io.Serial;
import java.io.Serializable;

import lombok.Data;

/**
 * 用户-角色关联 DTO。
 *
 * <p>用于创建用户-角色关联关系。
 *
 * @author ydsz-team
 * @since 26.09.01
 */
@Data
public class UserRoleDTO implements Serializable {

  @Serial private static final long serialVersionUID = 1L;

  /** 用户 ID */
  private String userId;

  /** 角色 ID */
  private String roleId;
}
