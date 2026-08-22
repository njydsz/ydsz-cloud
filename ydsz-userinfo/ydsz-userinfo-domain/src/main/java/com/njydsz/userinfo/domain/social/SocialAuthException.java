package com.njydsz.userinfo.domain.social;

/**
 * 社交认证运行时异常。
 *
 * <p>封装社交认证流程中的平台侧异常（网络超时、令牌失效、用户拒绝授权等），
 * 由 {@link SocialAuthProvider} 实现类抛出，上层 {@code SocialAuthService} 捕获后转换为对应的业务异常。
 *
 * <p>该异常为 domain 层异常，不依赖 Spring 或任何框架。
 *
 * @author ydsz-team
 * @since 1.0.0
 */
public class SocialAuthException extends RuntimeException {

  private static final long serialVersionUID = 1L;

  /** 平台标识 */
  private final String platform;

  /**
   * 构造社交认证异常（平台标识未知）。
   *
   * @param message 错误描述
   */
  public SocialAuthException(String message) {
    super(message);
    this.platform = "UNKNOWN";
  }

  /**
   * 构造社交认证异常。
   *
   * @param platform 平台标识
   * @param message 错误描述
   */
  public SocialAuthException(String platform, String message) {
    super(message);
    this.platform = platform;
  }

  /**
   * 构造社交认证异常（含根因）。
   *
   * @param platform 平台标识
   * @param message 错误描述
   * @param cause 根因异常
   */
  public SocialAuthException(String platform, String message, Throwable cause) {
    super(message, cause);
    this.platform = platform;
  }

  /**
   * 获取平台标识。
   *
   * @return 平台标识字符串
   */
  public String getPlatform() {
    return platform;
  }
}
