package com.njydsz.userinfo.domain.dto;

import java.io.Serial;
import java.io.Serializable;

import lombok.Data;

/**
 * 用户资料更新 DTO（当前登录用户修改自己的资料）。
 *
 * <p>与 {@link com.njydsz.userinfo.domain.dto.UserAccountUpdateDTO} 不同，此 DTO 不包含状态、角色等管理字段，仅包含用户可自助修改的基本信息。
 *
 * @author ydsz-team
 * @since 1.0.0
 */
@Data
public class UserProfileUpdateDTO implements Serializable {

  @Serial private static final long serialVersionUID = 1L;

  /** 真实姓名 */
  private String realName;

  /** 手机号 */
  private String phone;

  /** 邮箱 */
  private String email;

  /** 头像 URL */
  private String avatar;
}
