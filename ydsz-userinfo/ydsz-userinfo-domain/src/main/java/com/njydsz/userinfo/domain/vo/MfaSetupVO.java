package com.njydsz.userinfo.domain.vo;

import java.io.Serial;
import java.io.Serializable;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 双因素认证（TOTP）绑定信息 VO。
 *
 * <p>返回给前端的绑定初始数据：Base32 密钥（供手动录入）与 otpauth URI（供生成二维码，兼容 Google /
 * Microsoft Authenticator）。
 *
 * @author ydsz-team
 * @since 26.09.01
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class MfaSetupVO implements Serializable {

  @Serial private static final long serialVersionUID = 1L;

  /** Base32 编码的 TOTP 密钥（仅供绑定当次展示，绑定成功后服务端留存） */
  private String secret;

  /** otpauth:// 协议 URI，前端可渲染为二维码（如 qrcode.js） */
  private String otpauthUri;
}
