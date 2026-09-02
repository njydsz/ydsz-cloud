package com.njydsz.common.queue.service.impl;

import java.util.List;

import org.springframework.data.redis.core.RedisTemplate;

import com.njydsz.common.queue.domain.QueueMessage;
import com.njydsz.common.queue.service.IMessagePublisher;

/**
 * Redis List 模式发布者。
 *
 * <p>基于 Redis List（{@code LPUSH} / {@code BRPOP}）实现轻量级消息队列。
 *
 * <p>适用场景：可靠性要求不高、单消费者、不需要广播，
 *
 * <p>如：日志收集、指标异步聚合。
 *
 * @author ydsz-team
 * @since 26.09.01
 */
public class RedisListPublisher implements IMessagePublisher {

  private final RedisTemplate<String, Object> redisTemplate;
  private final String channel;

  public RedisListPublisher(RedisTemplate<String, Object> redisTemplate, String channel) {
    if (redisTemplate == null) {
      throw new IllegalArgumentException("RedisTemplate 不能为空");
    }
    if (channel == null || channel.isEmpty()) {
      throw new IllegalArgumentException("通道名称不能为空");
    }
    this.redisTemplate = redisTemplate;
    this.channel = channel;
  }

  @Override
  public void publish(String message) {
    if (message == null) {
      return;
    }
    redisTemplate.opsForList().rightPush(channel, message);
  }

  @Override
  public void publish(QueueMessage message) {
    if (message == null) {
      return;
    }
    redisTemplate.opsForList().rightPush(channel, QueueMessage.toPayload(message));
  }

  @Override
  public void publishBatch(List<QueueMessage> messages) {
    if (messages == null || messages.isEmpty()) {
      return;
    }
    for (QueueMessage message : messages) {
      publish(message);
    }
  }

  @Override
  public void close() {
    // Redis List 发布者无需显式关闭资源
  }
}
