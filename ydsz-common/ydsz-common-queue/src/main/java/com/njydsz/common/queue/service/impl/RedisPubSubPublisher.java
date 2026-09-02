package com.njydsz.common.queue.service.impl;

import java.util.List;

import org.springframework.data.redis.core.RedisTemplate;

import com.njydsz.common.queue.domain.QueueMessage;
import com.njydsz.common.queue.service.IMessagePublisher;

/**
 * Redis Pub/Sub 模式发布者。
 *
 * <p>基于 Redis Pub/Sub 的发布-订阅模式，适用场景：广播、实时通知、
 *
 * <p>无需持久化。消息不落盘，订阅者必须在线，否则消息丢失。
 *
 * @author ydsz-team
 * @since 26.09.01
 */
public class RedisPubSubPublisher implements IMessagePublisher {

  private final RedisTemplate<String, Object> redisTemplate;
  private final String channel;

  public RedisPubSubPublisher(RedisTemplate<String, Object> redisTemplate, String channel) {
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
    redisTemplate.convertAndSend(channel, message);
  }

  @Override
  public void publish(QueueMessage message) {
    if (message == null) {
      return;
    }
    redisTemplate.convertAndSend(channel, QueueMessage.toPayload(message));
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
    // Redis PubSub 发布者无需显式关闭资源
  }
}
