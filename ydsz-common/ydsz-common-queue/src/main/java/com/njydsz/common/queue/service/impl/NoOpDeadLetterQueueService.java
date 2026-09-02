package com.njydsz.common.queue.service.impl;

import java.util.List;

import com.njydsz.common.queue.service.DeadLetterQueueService;

/**
 * 空操作死信队列（默认降级）。
 *
 * <p>当 Redis 不可用或死信队列被显式禁用时，注入此空实现，
 *
 * <p>所有调用方法均为 no-op，避免业务路径 NPE，保留日志埋点。
 *
 * @author ydsz-team
 * @since 26.09.01
 */
public class NoOpDeadLetterQueueService implements DeadLetterQueueService {

  @Override
  public void sendToDeadLetter(
      String topic, String messageId, String messageBody, String failureReason) {
    // No-op: 死信队列服务不可用
  }

  @Override
  public List<String> queryDeadLetters(String topic, int limit) {
    return List.of();
  }

  @Override
  public boolean retry(String topic, String messageId) {
    return false;
  }

  @Override
  public int retryAll() {
    return 0;
  }

  @Override
  public int getDeadLetterCount(String topic) {
    return 0;
  }

  @Override
  public int getRetryCount(String topic, String messageId) {
    return 0;
  }
}
