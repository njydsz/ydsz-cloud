package com.njydsz.common.notify.core;

/**
 * 消息发送结果统一封装
 *
 * @author ydsz-team
 * @since 26.09.01
 */
public interface NotifySendResult {

  /**
   * 是否发送成功。
   *
   * @return {@code true} 表示发送成功
   */
  boolean isSuccess();

  /**
   * 获取消息 ID。
   *
   * @return 消息 ID
   */
  String getMessageId();

  /**
   * 获取错误信息。
   *
   * @return 错误信息（成功时为 {@code null}）
   */
  String getErrorMessage();

  /**
   * 获取发送渠道。
   *
   * @return 发送渠道
   */
  String getChannel();

  /**
   * 获取发送时间戳。
   *
   * @return 发送时间戳（毫秒）
   */
  long getSendTime();

  /**
   * 创建成功结果。
   *
   * @param messageId 消息 ID
   * @param channel 发送渠道
   * @return 成功结果实例
   */
  static NotifySendResult success(String messageId, String channel) {
    return new DefaultNotifyResult(true, messageId, null, channel, System.currentTimeMillis());
  }

  /**
   * 创建失败结果。
   *
   * @param errorMessage 错误信息
   * @param channel 发送渠道
   * @return 失败结果实例
   */
  static NotifySendResult failure(String errorMessage, String channel) {
    return new DefaultNotifyResult(false, null, errorMessage, channel, System.currentTimeMillis());
  }
}
