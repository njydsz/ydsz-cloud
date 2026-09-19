package com.njydsz.common.auth.event;

/**
 * Token 撤销事件。
 *
 * <p>当 Token 被主动撤销（登出）或被动失效（管理员强制下线）时发布。 订阅方可用于审计日志记录、跨节点 session 清理通知等。
 *
 * @author ydsz-team
 * @since 26.09.18
 */
public class TokenRevokeEvent extends AuthenticationEvent {

  private static final long serialVersionUID = 1L;

  /** 用户 ID。 */
  private final String userId;

  /** 被撤销 Token 的 JWT ID（jti）。 */
  private final String jti;

  /** 撤销原因（LOGOUT / EXPIRED / ADMIN_REVOKED / PASSWORD_CHANGED）。 */
  private final String reason;

  /** 操作者（self 表示用户主动登出，admin ID 表示管理员操作）。 */
  private final String operator;

  /**
   * 构造 Token 撤销事件。
   *
   * @param source 事件源
   * @param userId 用户 ID
   * @param jti Token 的 JWT ID
   * @param reason 撤销原因
   * @param operator 操作者标识
   */
  public TokenRevokeEvent(
      String source, String userId, String jti, String reason, String operator) {
    super(source);
    this.userId = userId;
    this.jti = jti;
    this.reason = reason;
    this.operator = operator;
  }

  public String getUserId() {
    return userId;
  }

  public String getJti() {
    return jti;
  }

  public String getReason() {
    return reason;
  }

  public String getOperator() {
    return operator;
  }
}
