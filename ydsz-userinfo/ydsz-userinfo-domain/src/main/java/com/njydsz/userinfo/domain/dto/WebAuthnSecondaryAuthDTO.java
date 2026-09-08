package com.njydsz.userinfo.domain.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * WebAuthn 二级认证请求体。
 *
 * <p>前端在完成 WebAuthn 通行钥断言后，调用 {@code POST /api/auth/secondary-auth/webauthn} 提交验证结果。
 *
 * <p><b>流程：</b>
 *
 * <ol>
 *   <li>前端请求 {@code /secondary-auth/webauthn/challenge} 获取 WebAuthn 挑战码</li>
 *   <li>浏览器调用 {@code navigator.credentials.get()} 完成通行钥断言</li>
 *   <li>前端将断言结果提交到此 DTO，调用 {@code /secondary-auth/webauthn} 完成二级认证</li>
 * </ol>
 *
 * @author ydsz-team
 * @since 26.09.07
 */
@Data
public class WebAuthnSecondaryAuthDTO {

  /**
   * 场景标识（scene）。
   *
   * <p>用于区分不同业务场景的二级认证，每个场景独立验证、独立过期。
   */
  @NotBlank(message = "场景标识不能为空")
  private String scene;

  /**
   * WebAuthn 认证挑战码（由 challenge 端点获取）。
   */
  @NotBlank(message = "挑战码不能为空")
  private String challenge;

  /**
   * 凭证 ID（Base64URL 编码）。
   */
  @NotBlank(message = "凭证ID不能为空")
  private String credentialId;

  /**
   * 客户端数据 JSON（Base64URL 编码）。
   */
  @NotBlank(message = "clientDataJSON不能为空")
  private String clientDataJSON;

  /**
   * 认证器数据（Base64URL 编码）。
   */
  @NotBlank(message = "authenticatorData不能为空")
  private String authenticatorData;

  /**
   * 签名（Base64URL 编码）。
   */
  @NotBlank(message = "签名不能为空")
  private String signature;

  /**
   * 二级认证有效期（秒）。默认 300 秒（5 分钟）。
   */
  private Integer ttlSeconds;
}
