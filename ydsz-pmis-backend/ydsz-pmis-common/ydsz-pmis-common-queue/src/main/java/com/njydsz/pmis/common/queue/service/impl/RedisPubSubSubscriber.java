package com.njydsz.pmis.common.queue.service.impl;

import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

import org.springframework.data.redis.connection.Message;
import org.springframework.data.redis.connection.MessageListener;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.listener.ChannelTopic;
import org.springframework.data.redis.listener.RedisMessageListenerContainer;

import com.njydsz.pmis.common.queue.config.QueueProperties;
import com.njydsz.pmis.common.queue.domain.QueueMessage;
import com.njydsz.pmis.common.queue.queue.RedisPubSubMQ;
import com.njydsz.pmis.common.queue.rate.ConsumerRateLimiter;
import com.njydsz.pmis.common.queue.service.IMessageHandler;
import com.njydsz.pmis.common.queue.service.IMessageSubscriber;

import lombok.extern.slf4j.Slf4j;

/**
 * 基于 Redis PubSub 的消息订阅者
 *
 * <p>使用 Redis SUBSCRIBE 命令订阅指定频道，接收发布者广播的消息。
 * 通过 {@link RedisTemplate} 复用 ydsz-pmis-common-redis 的连接。
 *
 * @author ydsz-pmis-team
 * @since 1.0.0
 */
@Slf4j
public class RedisPubSubSubscriber implements IMessageSubscriber {

    private final RedisTemplate<String, Object> redisTemplate;
    private final String channel;
    private final RedisPubSubMQ parentMQ;
    private final ConsumerRateLimiter rateLimiter;

    private final AtomicBoolean running;
    private final AtomicLong consumedCount;
    private final AtomicReference<Throwable> lastError;

    private volatile RedisMessageListenerContainer listenerContainer;

    public RedisPubSubSubscriber(RedisTemplate<String, Object> redisTemplate,
                                  String channel,
                                  RedisPubSubMQ parentMQ,
                                  QueueProperties queueProperties) {
        if (redisTemplate == null) {
            throw new IllegalArgumentException("RedisTemplate 不能为空");
        }
        if (channel == null || channel.isEmpty()) {
            throw new IllegalArgumentException("通道名称不能为空");
        }
        this.redisTemplate = redisTemplate;
        this.channel = channel;
        this.parentMQ = parentMQ;
        this.running = new AtomicBoolean(false);
        this.consumedCount = new AtomicLong(0);
        this.lastError = new AtomicReference<>();
        this.rateLimiter = queueProperties != null ? queueProperties.createRateLimiter() : new ConsumerRateLimiter(0);
    }

    @Override
    public String subscribe() {
        // PubSub 模式不支持同步订阅，需使用 subscribeAsync
        log.warn("[RedisPubSub] PubSub 模式不支持同步订阅，请使用 subscribeAsync()");
        return null;
    }

    @Override
    public String subscribeAsync(IMessageHandler handler) {
        if (!running.compareAndSet(false, true)) {
            log.warn("[RedisPubSub] 订阅者已在运行中，channel={}", channel);
            return channel;
        }
        subscribeAsyncViaRedisTemplate(handler);
        if (parentMQ != null) {
            parentMQ.registerSubscriber(channel, this);
        }
        return channel;
    }

    private void subscribeAsyncViaRedisTemplate(IMessageHandler handler) {
        listenerContainer = new RedisMessageListenerContainer();
        listenerContainer.setConnectionFactory(redisTemplate.getConnectionFactory());
        MessageListener messageListener = (Message message, byte[] pattern) -> {
            try {
                rateLimiter.acquire();
                String body = new String(message.getBody());
                processMessage(body, handler);
            } catch (Exception e) {
                lastError.set(e);
                log.error("[RedisPubSub] 处理消息异常，channel={}", channel, e);
            }
        };
        listenerContainer.addMessageListener(messageListener, new ChannelTopic(channel));
        listenerContainer.afterPropertiesSet();
        listenerContainer.start();
        log.info("[RedisPubSub] 异步订阅已启动（复用 ydsz-pmis-common-redis 连接），channel={}", channel);
    }

    private void processMessage(String message, IMessageHandler handler) {
        if (message == null) {
            return;
        }
        try {
            QueueMessage queueMessage = QueueMessage.fromPayload(message);
            if (queueMessage == null) {
                queueMessage = QueueMessage.of(message);
            }
            if (handler != null) {
                handler.onMessage(queueMessage);
            }
            consumedCount.incrementAndGet();
            lastError.set(null);
            log.debug("[RedisPubSub] 消息处理成功，channel={}", channel);
        } catch (Exception e) {
            lastError.set(e);
            log.error("[RedisPubSub] 消息处理异常，channel={}", channel, e);
        }
    }

    @Override
    public void stop() {
        if (!running.compareAndSet(true, false)) {
            return;
        }
        shutdown();
        if (parentMQ != null) {
            parentMQ.removeSubscriber(channel);
        }
        log.info("[RedisPubSub] 订阅已停止，channel={}", channel);
    }

    @Override
    public boolean isRunning() {
        return running.get();
    }

    @Override
    public Object getChannel() {
        return channel;
    }

    @Override
    public String getConsumerId() {
        return channel;
    }

    @Override
    public int getConsumedCount() {
        return (int) consumedCount.get();
    }

    @Override
    public Throwable getLastError() {
        return lastError.get();
    }

    public void shutdown() {
        if (listenerContainer != null) {
            try {
                listenerContainer.stop();
                listenerContainer.destroy();
                listenerContainer = null;
            } catch (Exception e) {
                log.warn("[RedisPubSub] 关闭 RedisMessageListenerContainer 异常", e);
            }
        }
    }
}
