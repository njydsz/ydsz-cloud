package com.njydsz.userinfo.domain.dto;

import java.io.Serial;
import java.io.Serializable;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * 敏感操作二次认证请求 DTO。
 *
 * <p>用于 {@code Post /api/v1/user/sensitive-verify} 接口，管理员在执行敏感操作前 通过密码确认身份。验证通过后，后端在 Redis 写入一条短期有效（5 分钟）的标记。
 *
 * <p><b>安全说明：</b>
 *
 * <ul>
 *   <li>密码仅用于身份校验，不做任何持久化
 *   <li>验证标记存储在 Redis，TTL 5 分钟，过期后需重新验证
 *   <li>标记以当前登录用户 ID 为 Key，不跨用户共享
 * </ul>
 *
 * @author ydsz-team
 * @since 26.09.01
 */
@Data
public class SensitiveVerifyDTO implements Serializable {

  @Serial private static final long serialVersionUID = 1L;

  /** 当前登录用户的明文密码（用于身份校验） */
  @NotBlank(message = "{userinfo.sensitive.verify.password.required}")
  private String password;
}
