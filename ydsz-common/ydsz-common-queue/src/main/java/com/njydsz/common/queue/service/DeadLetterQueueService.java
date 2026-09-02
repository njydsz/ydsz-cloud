package com.njydsz.common.queue.service;

import java.util.List;

/**
 * 死信队列服务接口。
 *
 * <p>消息多次重试失败后入队。
 *
 * <p>供后台告警/人工干预/归档。
 *
 * @author ydsz-team
 * @since 26.09.01
 */
public interface DeadLetterQueueService {

  /**
   * 将消息发送到死信队列
   *
   * @param topic 主题
   * @param messageId 消息 ID
   * @param messageBody 消息体
   * @param failureReason 失败原因
   */
  void sendToDeadLetter(String topic, String messageId, String messageBody, String failureReason);

  /**
   * 查询死信队列中的消息
   *
   * @param topic 主题
   * @param limit 最大返回数量
   * @return 死信消息列表
   */
  List<String> queryDeadLetters(String topic, int limit);

  /**
   * 重试死信队列中的消息
   *
   * @param topic 主题
   * @param messageId 消息 ID
   * @return true 表示重试成功
   */
  boolean retry(String topic, String messageId);

  /**
   * 重试所有死信队列中的消息
   *
   * @return 重试成功的消息数量
   */
  int retryAll();

  /**
   * 获取指定主题的死信队列消息数量
   *
   * @param topic 主题
   * @return 死信消息数量
   */
  int getDeadLetterCount(String topic);

  /**
   * 获取消息已重试次数
   *
   * @param topic 主题
   * @param messageId 消息 ID
   * @return 已重试次数
   */
  int getRetryCount(String topic, String messageId);
}
