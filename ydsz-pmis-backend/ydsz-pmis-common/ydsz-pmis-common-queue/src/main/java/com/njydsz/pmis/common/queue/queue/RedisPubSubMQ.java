package com.njydsz.pmis.common.queue.queue;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.locks.ReentrantLock;

import org.springframework.data.redis.core.RedisTemplate;

import com.njydsz.pmis.common.exception.custom.BusinessException;
import com.njydsz.pmis.common.queue.config.QueueProperties;
import com.njydsz.pmis.common.queue.service.IMessagePublisher;
import com.njydsz.pmis.common.queue.service.IMessageSubscriber;
import com.njydsz.pmis.common.queue.service.impl.RedisPubSubPublisher;
import com.njydsz.pmis.common.queue.service.impl.RedisPubSubSubscriber;
import com.njydsz.pmis.common.redis.service.RedisService;

import lombok.extern.slf4j.Slf4j;

/**
 * 基于 Redis PubSub 实现的消息队列
 *
 * <p>Redis PubSub（发布/订阅）是一种广播模式的消息通信机制。
 * 发布者将消息发送到指定频道，所有订阅该频道的订阅者都能收到消息。
 *
 * <p><b>连接复用：</b>通过 {@link RedisService} 复用 ydsz-pmis-common-redis 的连接。
 *
 * @author ydsz-pmis-team
 * @since 1.0.0
 */
@Slf4j
public class RedisPubSubMQ implements IMessageQueue {

    private final RedisTemplate<String, Object> redisTemplate;
    private final QueueProperties config;
    private final ConcurrentMap<String, RedisPubSubSubscriber> subscribers;
    private volatile boolean closed = false;
    private final ReentrantLock closeLock = new ReentrantLock();

    /**
     * 基于 RedisService 构造（复用 ydsz-pmis-common-redis 连接）
     *
     * @param redisService Redis 服务
     * @param config       队列配置
     */
    public RedisPubSubMQ(RedisService redisService, QueueProperties config) {
        if (config == null) {
            throw BusinessException.builder().key("队列配置不能为空").build();
        }
        this.config = config;
        this.subscribers = new ConcurrentHashMap<>(4);
        this.redisTemplate = redisService.getRedisTemplate();
        log.info("[RedisPubSubMQ] 初始化成功（复用 ydsz-pmis-common-redis 连接）");
    }

    @Override
    public IMessagePublisher createPublisher(String channel) {
        checkNotClosed();
        if (channel == null || channel.isEmpty()) {
            throw BusinessException.builder().key("通道名称不能为空").build();
        }
        return new RedisPubSubPublisher(redisTemplate, channel);
    }

    @Override
    public IMessageSubscriber createSubscriber(String channel) {
        checkNotClosed();
        if (channel == null || channel.isEmpty()) {
            throw BusinessException.builder().key("通道名称不能为空").build();
        }
        return new RedisPubSubSubscriber(redisTemplate, channel, this, config);
    }

    @Override
    public String[] getChannels() {
        return subscribers.keySet().toArray(new String[0]);
    }

    @Override
    public boolean isClosed() {
        return closed;
    }

    @Override
    public String getType() {
        return "Redis-PubSub";
    }

    @Override
    public void close() {
        if (closed) {
            return;
        }
        closeLock.lock();
        try {
            if (closed) {
                return;
            }
            closed = true;
            shutdownSubscribers();
            // RedisTemplate 由 ydsz-pmis-common-redis 管理，无需关闭
            log.info("[Redis-PubSub] 队列已关闭");
        } finally {
            closeLock.unlock();
        }
    }

    public void registerSubscriber(String subscriberId, RedisPubSubSubscriber subscriber) {
        if (subscriberId != null && subscriber != null) {
            subscribers.put(subscriberId, subscriber);
            log.debug("[RedisPubSubMQ] 订阅者已注册，subscriberId={}", subscriberId);
        }
    }

    public void removeSubscriber(String subscriberId) {
        if (subscriberId != null) {
            subscribers.remove(subscriberId);
            log.debug("[RedisPubSubMQ] 订阅者已移除，subscriberId={}", subscriberId);
        }
    }

    private void shutdownSubscribers() {
        for (Map.Entry<String, RedisPubSubSubscriber> entry : subscribers.entrySet()) {
            try {
                entry.getValue().shutdown();
                log.debug("[RedisPubSubMQ] 订阅者已关闭，subscriberId={}", entry.getKey());
            } catch (Exception e) {
                log.warn("[RedisPubSubMQ] 关闭订阅者时发生异常，subscriberId={}", entry.getKey(), e);
            }
        }
        subscribers.clear();
    }
}
