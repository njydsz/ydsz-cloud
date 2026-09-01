package com.njydsz.userinfo.domain.dto;

import java.io.Serial;
import java.io.Serializable;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * 发送验证码请求 DTO。
 *
 * <p>用于自助注册/找回密码流程中，向用户手机或邮箱发送验证码。
 *
 * <p><b>使用方式：</b>
 *
 * <ul>
 *   <li>手机验证：{@code target} 填手机号，{@code targetType} 为 {@code PHONE}</li>
 *   <li>邮箱验证：{@code target} 填邮箱地址，{@code targetType} 为 {@code EMAIL}</li>
 * </ul>
 *
 * @author ydsz-team
 * @since 26.09.01
 */
@Data
public class SendVerifyCodeDTO implements Serializable {

  @Serial private static final long serialVersionUID = 1L;

  /** 发送类型：REGISTER（注册）/ FORGOT_PASSWORD（找回密码）/ UNLOCK（账号解锁） */
  @NotBlank(message = "{userinfo.verify.code.type.required}")
  private String type;

  /** 验证码目标类型：PHONE（手机）/ EMAIL（邮箱） */
  @NotBlank(message = "{userinfo.verify.code.target.type.required}")
  private String targetType;

  /** 目标标识（手机号或邮箱地址） */
  @NotBlank(message = "{userinfo.verify.code.target.required}")
  private String target;

  /** 图形验证码 key（P0-5：防短信轰炸，前端先调用 /api/v1/captcha 获取） */
  @NotBlank(message = "{userinfo.verify.code.captcha.key.required}")
  private String captchaKey;

  /** 图形验证码用户输入（P0-5：防短信轰炸） */
  @NotBlank(message = "{userinfo.verify.code.captcha.required}")
  private String captcha;

  /**
   * 获取目标手机号（兼容旧版调用）。
   *
   * <p>当 {@code targetType} 为 {@code PHONE} 时返回 target，否则返回 null。
   *
   * @return 手机号或 null
   */
  public String getPhone() {
    return "PHONE".equalsIgnoreCase(targetType) ? target : null;
  }

  /**
   * 获取目标邮箱。
   *
   * <p>当 {@code targetType} 为 {@code EMAIL} 时返回 target，否则返回 null。
   *
   * @return 邮箱地址或 null
   */
  public String getEmail() {
    return "EMAIL".equalsIgnoreCase(targetType) ? target : null;
  }
}
