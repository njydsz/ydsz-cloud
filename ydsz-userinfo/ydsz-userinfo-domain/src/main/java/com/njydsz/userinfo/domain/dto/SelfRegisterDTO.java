package com.njydsz.userinfo.domain.dto;

import java.io.Serial;
import java.io.Serializable;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * 自助注册请求 DTO。
 *
 * <p>用户通过前端注册页面提交注册申请，包含基本信息和验证码。 注册成功后账号状态为"待审核"或"已启用"（取决于配置）。
 *
 * @author ydsz-team
 * @since 26.09.01
 */
@Data
public class SelfRegisterDTO implements Serializable {

  @Serial private static final long serialVersionUID = 1L;

  /** 用户名（全局唯一） */
  @NotBlank(message = "{userinfo.self.register.username.required}")
  private String username;

  /** 真实姓名 */
  @NotBlank(message = "{userinfo.self.register.realname.required}")
  private String realName;

  /** 密码（明文，须符合密码策略） */
  @NotBlank(message = "{userinfo.self.register.password.required}")
  private String password;

  /** 手机号 */
  private String phone;

  /** 邮箱 */
  private String email;

  /** 手机/邮箱验证码 */
  @NotBlank(message = "{userinfo.self.register.verify.code.required}")
  private String verifyCode;

  /** 图形验证码 key（P0-5：防批量注册，前端先调用 /api/v1/captcha 获取） */
  @NotBlank(message = "{userinfo.self.register.captcha.key.required}")
  private String captchaKey;

  /** 图形验证码用户输入（P0-5：防批量注册） */
  @NotBlank(message = "{userinfo.self.register.captcha.required}")
  private String captcha;
}
