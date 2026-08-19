package com.njydsz.userinfo.domain.dto;

import java.io.Serial;
import java.io.Serializable;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * 登录请求 DTO。
 *
 * <p>用于 {@code PostDO /api/v1/auth/login} 接口，支持用户名+密码登录， 可选携带图形验证码进行人机校验。
 *
 * <p><b>校验规则：</b>
 *
 * <ul>
 *   <li>{@code username} — 必填，登录用户名
 *   <li>{@code password} — 必填，明文密码（传输层由 HTTPS 保护，服务端 BCrypt 比对）
 *   <li>{@code captchaKey} / {@code captcha} — 可选，当系统开启验证码或登录风险为 MEDIUM 及以上时必填
 *   <li>{@code mfaCode} — 可选，当登录风险为 HIGH 时必填（TOTP 动态码或短信验证码）
 * </ul>
 *
 * @author ydsz-team
 * @since 1.0.0
 */
@Data
public class LoginDTO implements Serializable {

  @Serial private static final long serialVersionUID = 1L;

  /** 登录用户名（全局唯一） */
  @NotBlank(message = "用户名不能为空")
  private String username;

  /** 登录密码（明文，传输层由 HTTPS 保护，服务端 BCrypt 比对） */
  @NotBlank(message = "密码不能为空")
  private String password;

  /** 验证码 Redis Key（由 {@code GET /api/v1/auth/captcha} 返回，开启验证码时必填） */
  private String captchaKey;

  /** 用户输入的图形验证码（不区分大小写，开启验证码时必填） */
  private String captcha;

  /** 双因素认证动态码（TOTP 或短信验证码，登录风险为 HIGH 时必填） */
  private String mfaCode;

  /** 租户 ID（多租户场景下指定登录归属租户，单租户模式可不传） */
  private String tenantId;

  /** 客户端 IP（由 Controller 从 HttpServletRequest 提取并填充，用于登录历史记录与 IP 封禁） */
  private String loginIp;

  /** User-Agent（由 Controller 从请求头提取，用于登录历史审计） */
  private String userAgent;

  /**
   * X-Platform 请求头（由 Controller 从请求头提取，用于分端会话控制）。
   *
   * <p>可选值：{@code web} / {@code app} / {@code api}。
   * 未传时从 {@link #userAgent} 推断设备类型。
   */
  private String platform;
}
