package com.njydsz.userinfo.domain.dto;

import java.io.Serial;
import java.io.Serializable;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 用户资料更新 DTO（当前登录用户修改自己的资料）。
 *
 * <p>与 {@link com.njydsz.userinfo.domain.dto.UserAccountDTO} 不同，此 DTO 不包含状态、角色等管理字段，仅包含用户可自助修改的基本信息。
 *
 * @author ydsz-team
 * @since 26.09.01
 */
@Data
public class UserProfileUpdateDTO implements Serializable {

  @Serial private static final long serialVersionUID = 1L;

  /** 真实姓名 */
  @Size(max = 64, message = "真实姓名长度不能超过 64 个字符")
  private String realName;

  /** 手机号 */
  @Size(max = 20, message = "手机号长度不能超过 20 个字符")
  private String phone;

  /** 邮箱 */
  @Size(max = 128, message = "邮箱长度不能超过 128 个字符")
  @Email(message = "邮箱格式不正确")
  private String email;

  /** 头像 URL */
  @Size(max = 255, message = "头像URL长度不能超过 255 个字符")
  private String avatar;
}
