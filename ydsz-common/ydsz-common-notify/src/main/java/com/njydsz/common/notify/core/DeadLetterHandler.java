package com.njydsz.common.notify.core;

import java.util.List;

import com.njydsz.common.notify.enums.NotifyChannel;

/**
 * 死信队列处理器接口（P0-2）
 *
 * <p>当重试队列中的消息超过最大重试次数后，将其移入死信队列进行人工干预或后续处理。
 *
 * @author ydsz-team
 * @since 26.09.01
 */
public interface DeadLetterHandler {

  /**
   * 将消息移入死信队列
   *
   * @param channel 通知渠道
   * @param receiver 接收者
   * @param title 标题
   * @param content 内容
   * @param failedAttempts 失败尝试次数
   * @param lastError 最后错误信息
   */
  void moveToDeadLetter(
      NotifyChannel channel,
      String receiver,
      String title,
      String content,
      int failedAttempts,
      String lastError);

  /**
   * 获取死信队列中的消息列表
   *
   * @param maxCount 最大返回数量
   * @return 死信消息列表
   */
  List<DeadLetterEntry> getDeadLetters(int maxCount);

  /**
   * 重试死信队列中的指定消息
   *
   * @param messageId 消息ID
   * @return true 表示重试成功
   */
  boolean retryDeadLetter(String messageId);

  /**
   * 获取死信队列大小
   *
   * @return 死信队列中消息数量
   */
  int getDeadLetterCount();

  /** 死信条目 */
  class DeadLetterEntry {

    private final String messageId;
    private final NotifyChannel channel;
    private final String receiver;
    private final String title;
    private final String content;
    private final int failedAttempts;
    private final String lastError;
    private final long createdAt;

    /**
     * 构造死信条目
     *
     * @param messageId 消息ID
     * @param channel 通知渠道
     * @param receiver 接收者
     * @param title 标题
     * @param content 内容
     * @param failedAttempts 失败尝试次数
     * @param lastError 最后错误信息
     * @param createdAt 创建时间戳
     */
    public DeadLetterEntry(
        String messageId,
        NotifyChannel channel,
        String receiver,
        String title,
        String content,
        int failedAttempts,
        String lastError,
        long createdAt) {
      this.messageId = messageId;
      this.channel = channel;
      this.receiver = receiver;
      this.title = title;
      this.content = content;
      this.failedAttempts = failedAttempts;
      this.lastError = lastError;
      this.createdAt = createdAt;
    }

    public String getMessageId() {
      return messageId;
    }

    public NotifyChannel getChannel() {
      return channel;
    }

    public String getReceiver() {
      return receiver;
    }

    public String getTitle() {
      return title;
    }

    public String getContent() {
      return content;
    }

    public int getFailedAttempts() {
      return failedAttempts;
    }

    public String getLastError() {
      return lastError;
    }

    public long getCreatedAt() {
      return createdAt;
    }
  }
}
