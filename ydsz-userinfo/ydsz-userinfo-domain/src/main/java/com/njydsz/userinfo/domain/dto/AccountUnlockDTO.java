package com.njydsz.userinfo.domain.dto;

import java.io.Serial;
import java.io.Serializable;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * 账号解锁请求 DTO。
 *
 * <p>用户自助解锁被锁定的账号，支持手机验证码/邮箱验证码两种身份验证方式。
 *
 * <p><b>使用方式：</b>
 *
 * <ul>
 *   <li>手机验证：{@code target} 填手机号，{@code targetType} 为 {@code PHONE}</li>
 *   <li>邮箱验证：{@code target} 填邮箱地址，{@code targetType} 为 {@code EMAIL}</li>
 * </ul>
 *
 * @author ydsz-team
 * @since 1.0.0
 */
@Data
public class AccountUnlockDTO implements Serializable {

  @Serial private static final long serialVersionUID = 1L;

  /** 用户名（需与验证方式绑定的用户匹配） */
  @NotBlank(message = "{userinfo.unlock.username.required}")
  private String username;

  /** 验证目标类型：PHONE（手机）/ EMAIL（邮箱） */
  @NotBlank(message = "{userinfo.unlock.target.type.required}")
  private String targetType;

  /** 验证目标（手机号或邮箱地址） */
  @NotBlank(message = "{userinfo.unlock.target.required}")
  private String target;

  /** 验证码 */
  @NotBlank(message = "{userinfo.unlock.verify.code.required}")
  private String verifyCode;

  /** 图形验证码 key（防暴力破解，前端先调用 /api/v1/captcha 获取） */
  @NotBlank(message = "{userinfo.unlock.captcha.key.required}")
  private String captchaKey;

  /** 图形验证码用户输入 */
  @NotBlank(message = "{userinfo.unlock.captcha.required}")
  private String captcha;
}
