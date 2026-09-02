package com.njydsz.common.socket.retry;

import java.util.List;

/**
 * 死信队列接口（P0-4）。
 *
 * <p>超过最大重试次数的消息移入死信队列，供人工排查或后续补偿处理。
 *
 * @author ydsz-team
 * @since 26.09.01
 */
public interface DeadLetterQueue {

  /**
   * 将消息移入死信队列。
   *
   * @param message 死信消息
   */
  void enqueue(RetryableMessage message);

  /**
   * 查询死信队列消息列表。
   *
   * @param offset 偏移量
   * @param limit 最大数量
   * @return 死信消息列表
   */
  List<RetryableMessage> list(int offset, int limit);

  /**
   * 获取死信队列消息数量。
   *
   * @return 死信消息数
   */
  long count();
}
