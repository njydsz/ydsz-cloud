package com.njydsz.common.netty.auth;

/**
 * 连接认证结果。
 *
 * <p>封装认证器的返回值，包含认证是否通过（{@link #isSuccess()}）和业务标识。
 *
 * <p>使用静态工厂方法创建：
 *
 * <ul>
 *   <li>{@link #success(String)} — 认证通过
 *   <li>{@link #failed(String)} — 认证失败
 *   <li{@link #failed(String, String)} — 认证失败（含错误码）
 * </ul>
 *
 * @author ydsz-team
 * @since 26.09.01
 * @see ConnectionAuthenticator
 */
public class AuthenticationResult {

  /** 是否认证通过（YDIZ-OOP-006: 布尔字段必须带 is 前缀） */
  private final boolean isSuccess;

  /** 业务标识（认证通过时有效） */
  private final String bizId;

  /** 失败原因（认证失败时有效） */
  private final String failReason;

  /** 错误码（可选，用于客户端区分失败类型） */
  private final String errorCode;

  /**
   * 私有构造，使用静态工厂方法创建实例。
   *
   * @param success 是否通过
   * @param bizId 业务标识
   * @param failReason 失败原因
   * @param errorCode 错误码
   */
  private AuthenticationResult(boolean success, String bizId, String failReason, String errorCode) {
    this.isSuccess = success;
    this.bizId = bizId;
    this.failReason = failReason;
    this.errorCode = errorCode;
  }

  /**
   * 创建认证通过结果。
   *
   * @param bizId 业务标识（用户 ID / 设备 ID 等）
   * @return 认证通过的结果实例
   */
  public static AuthenticationResult success(String bizId) {
    if (bizId == null || bizId.isEmpty()) {
      throw new IllegalArgumentException("bizId 不能为空");
    }
    return new AuthenticationResult(true, bizId, null, null);
  }

  /**
   * 创建认证失败结果。
   *
   * @param reason 失败原因描述
   * @return 认证失败的结果实例
   */
  public static AuthenticationResult failed(String reason) {
    return new AuthenticationResult(false, null, reason, "AUTH_FAILED");
  }

  /**
   * 创建认证失败结果（含错误码）。
   *
   * @param reason 失败原因描述
   * @param errorCode 错误码（如：TOKEN_EXPIRED / INVALID_SIGNATURE）
   * @return 认证失败的结果实例
   */
  public static AuthenticationResult failed(String reason, String errorCode) {
    return new AuthenticationResult(false, null, reason, errorCode);
  }

  /**
   * 判断认证是否通过。
   *
   * @return true 表示认证通过
   */
  public boolean isSuccess() {
    return isSuccess;
  }

  /**
   * 获取业务标识（仅 {@link #isSuccess()} 为 true 时有效）。
   *
   * @return 业务标识
   */
  public String getBizId() {
    return bizId;
  }

  /**
   * 获取失败原因（仅 {@link #isSuccess()} 为 false 时有效）。
   *
   * @return 失败原因描述
   */
  public String getFailReason() {
    return failReason;
  }

  /**
   * 获取错误码（仅 {@link #isSuccess()} 为 false 时有效）。
   *
   * @return 错误码，默认 "AUTH_FAILED"
   */
  public String getErrorCode() {
    return errorCode;
  }

  @Override
  public String toString() {
    if (isSuccess) {
      return "AuthenticationResult{SUCCESS, bizId='" + bizId + "'}";
    }
    return "AuthenticationResult{FAILED, reason='" + failReason + "', code='" + errorCode + "'}";
  }
}
