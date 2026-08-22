package com.njydsz.userinfo.domain.dto;

import java.io.Serial;
import java.io.Serializable;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * MFA 动态码操作 DTO。
 *
 * <p>用于 MFA 绑定激活/解除场景，携带用户输入的一次性动态码（Authenticator 应用生成）。
 *
 * @author ydsz-team
 * @since 1.0.0
 */
@Data
public class MfaOperationDTO implements Serializable {

  @Serial private static final long serialVersionUID = 1L;

  /** TOTP 动态码（6 位数字，由 Authenticator 应用生成） */
  @NotBlank(message = "动态码不能为空")
  private String code;
}
