package com.njydsz.pmis.common.queue.service.impl;

import com.njydsz.pmis.common.queue.service.IMessagePublisher;
import org.springframework.data.redis.core.RedisTemplate;

/**
 * 基于 Redis PubSub 的消息发布者
 *
 * <p>使用 Redis PUBLISH 命令将消息广播到指定频道。
 * 通过 {@link RedisTemplate} 复用 pmis-common-redis 的连接。
 *
 * @author ydsz-pmis-team
 * 
 * 
 * @since 1.0.0
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
    public String getChannel() {
        return channel;
    }
}
