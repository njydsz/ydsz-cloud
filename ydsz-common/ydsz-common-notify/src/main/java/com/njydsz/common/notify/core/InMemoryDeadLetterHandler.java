package com.njydsz.common.notify.core;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicInteger;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.njydsz.common.notify.enums.NotifyChannel;
import com.njydsz.common.util.id.IdGenerator;

/**
 * 内存死信队列实现（P0-2）
 *
 * <p>当 Redis 不可用时的降级方案。消息存储在内存队列中， 服务重启后丢失。生产环境建议使用 Redis 持久化实现。
 *
 * @author ydsz-team
 * @since 1.0.0
 */
public class InMemoryDeadLetterHandler implements DeadLetterHandler {

  private static final Logger LOG = LoggerFactory.getLogger(InMemoryDeadLetterHandler.class);

  private static final int MAX_DLQ_SIZE = 10000;

  private final ConcurrentLinkedQueue<DeadLetterEntry> deadLetterQueue =
      new ConcurrentLinkedQueue<>();
  private final AtomicInteger count = new AtomicInteger(0);

  /**
   * 将消息移入内存死信队列
   *
   * @param channel 通知渠道
   * @param receiver 接收者
   * @param title 标题
   * @param content 内容
   * @param failedAttempts 失败尝试次数
   * @param lastError 最后错误信息
   */
  @Override
  public void moveToDeadLetter(
      NotifyChannel channel,
      String receiver,
      String title,
      String content,
      int failedAttempts,
      String lastError) {
    String messageId = IdGenerator.nextIdStr();
    DeadLetterEntry entry =
        new DeadLetterEntry(
            messageId,
            channel,
            receiver,
            title,
            content,
            failedAttempts,
            lastError,
            System.currentTimeMillis());

    if (count.get() >= MAX_DLQ_SIZE) {
      deadLetterQueue.poll();
      count.decrementAndGet();
      LOG.warn("[InMemoryDeadLetterHandler] 死信队列已满，丢弃最旧的消息");
    }

    deadLetterQueue.offer(entry);
    count.incrementAndGet();
    LOG.error(
        "[InMemoryDeadLetterHandler] 消息移入死信队列: messageId={}, channel={}, receiver={}, attempts={}, error={}",
        messageId,
        channel.getName(),
        receiver,
        failedAttempts,
        lastError);
  }

  /**
   * 获取死信队列中的消息列表
   *
   * @param maxCount 最大返回数量
   * @return 死信消息列表
   */
  @Override
  public List<DeadLetterEntry> getDeadLetters(int maxCount) {
    int limit = maxCount > 0 ? maxCount : 100;
    List<DeadLetterEntry> result = new ArrayList<>();
    for (DeadLetterEntry entry : deadLetterQueue) {
      if (result.size() >= limit) {
        break;
      }
      result.add(entry);
    }
    return result;
  }

  /**
   * 重试死信队列中的指定消息
   *
   * @param messageId 消息 ID
   * @return true 表示重试成功
   */
  @Override
  public boolean retryDeadLetter(String messageId) {
    DeadLetterEntry toRemove = null;
    for (DeadLetterEntry entry : deadLetterQueue) {
      if (entry.getMessageId().equals(messageId)) {
        toRemove = entry;
        break;
      }
    }
    if (toRemove != null) {
      deadLetterQueue.remove(toRemove);
      count.decrementAndGet();
      LOG.info("[InMemoryDeadLetterHandler] 死信消息已移除等待重试: messageId={}", messageId);
      return true;
    }
    return false;
  }

  /**
   * 获取死信队列大小
   *
   * @return 死信队列中消息数量
   */
  @Override
  public int getDeadLetterCount() {
    return count.get();
  }
}
