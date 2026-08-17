package com.njydsz.userinfo.domain.dto;

import java.io.Serial;
import java.io.Serializable;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * 找回密码请求 DTO。
 *
 * <p>用户通过手机号 + 验证码验证身份后，设置新密码。
 *
 * @author ydsz-team
 * @since 1.0.0
 */
@Data
public class ForgotPasswordDTO implements Serializable {

  @Serial private static final long serialVersionUID = 1L;

  /** 用户名 */
  @NotBlank(message = "{userinfo.forgot.password.username.required}")
  private String username;

  /** 手机号（用于验证身份） */
  @NotBlank(message = "{userinfo.forgot.password.phone.required}")
  private String phone;

  /** 手机验证码 */
  @NotBlank(message = "{userinfo.forgot.password.verify.code.required}")
  private String verifyCode;

  /** 新密码（明文，须符合密码策略） */
  @NotBlank(message = "{userinfo.forgot.password.new.password.required}")
  private String newPassword;
}
