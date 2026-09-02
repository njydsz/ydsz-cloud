package com.njydsz.common.socket.push;

/**
 * 推送结果返回值（P2-5）。
 *
 * <p>封装推送操作的结果，供调用方感知推送状态（成功/失败/错误原因）。
 *
 * @param success 是否推送成功
 * @param messageId 消息 ID（成功时生成）
 * @param errorCode 错误码（失败时）
 * @param errorMessage 错误描述（失败时）
 * @author ydsz-team
 * @since 26.09.01
 */
public record PushResult(boolean success, String messageId, String errorCode, String errorMessage) {

  /**
   * 创建成功结果。
   *
   * @param messageId 消息 ID
   * @return 成功结果
   */
  public static PushResult success(String messageId) {
    return new PushResult(true, messageId, null, null);
  }

  /**
   * 创建失败结果。
   *
   * @param messageId 消息 ID
   * @param errorCode 错误码
   * @param errorMessage 错误描述
   * @return 失败结果
   */
  public static PushResult failure(String messageId, String errorCode, String errorMessage) {
    return new PushResult(false, messageId, errorCode, errorMessage);
  }

  /**
   * 创建失败结果（无错误信息）。
   *
   * @param messageId 消息 ID
   * @return 失败结果
   */
  public static PushResult failure(String messageId) {
    return new PushResult(false, messageId, "PUSH_FAILED", "Push failed");
  }
}
