package com.njydsz.userinfo.server.auth;

/**
 * SAML 认证异常
 *
 * <p>封装 SAML 认证流程中的各类错误，包括 XML 解析失败、签名验证失败、
 * 证书配置错误等场景。
 *
 * @author ydsz-team
 * @since 1.0.0
 */
public class SamlException extends Exception {

  private static final long serialVersionUID = 1L;

  /** 错误阶段（如 "PARSE", "SIGNATURE_VERIFY", "CERT_CONFIG"） */
  private final String phase;

  /**
   * 构造 SAML 认证异常
   *
   * @param message 错误消息
   */
  public SamlException(String message) {
    super(message);
    this.phase = null;
  }

  /**
   * 构造 SAML 认证异常（带原始异常）
   *
   * @param message 错误消息
   * @param cause 原始异常
   */
  public SamlException(String message, Throwable cause) {
    super(message, cause);
    this.phase = null;
  }

  /**
   * 构造 SAML 认证异常（带阶段信息）
   *
   * @param phase 错误阶段
   * @param message 错误消息
   * @param cause 原始异常
   */
  public SamlException(String phase, String message, Throwable cause) {
    super(String.format("SAML %s 失败: %s", phase, message), cause);
    this.phase = phase;
  }

  public String getPhase() {
    return phase;
  }
}
