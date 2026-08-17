package com.njydsz.userinfo.domain.dto;

import java.io.Serial;
import java.io.Serializable;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * 发送验证码请求 DTO。
 *
 * <p>用于自助注册/找回密码流程中，向用户手机发送验证码。
 *
 * @author ydsz-team
 * @since 1.0.0
 */
@Data
public class SendVerifyCodeDTO implements Serializable {

  @Serial private static final long serialVersionUID = 1L;

  /** 发送类型：REGISTER（注册）/ FORGOT_PASSWORD（找回密码） */
  @NotBlank(message = "{userinfo.verify.code.type.required}")
  private String type;

  /** 目标手机号 */
  @NotBlank(message = "{userinfo.verify.code.phone.required}")
  private String phone;
}
