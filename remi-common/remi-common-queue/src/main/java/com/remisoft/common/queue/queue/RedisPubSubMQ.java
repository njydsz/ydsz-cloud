package com.remisoft.common.queue.queue;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.locks.ReentrantLock;

import org.springframework.data.redis.core.RedisTemplate;

import com.remisoft.common.exception.custom.BusinessException;
import com.remisoft.common.queue.config.QueueProperties;
import com.remisoft.common.queue.service.IMessagePublisher;
import com.remisoft.common.queue.service.IMessageSubscriber;
import com.remisoft.common.queue.service.impl.RedisPubSubPublisher;
import com.remisoft.common.queue.service.impl.RedisPubSubSubscriber;
import com.remisoft.common.redis.service.RedisService;

import lombok.extern.slf4j.Slf4j;

/**
 * 基于 Redis PubSub 实现的消息队列
 *
 * <p>Redis PubSub（发布/订阅）是一种广播模式的消息通信机制。
 * 发布者将消息发送到指定频道，所有订阅该频道的订阅者都能收到消息。
 *
 * <p><b>连接复用：</b>通过 {@link RedisService} 复用 remi-common-redis 的连接。
 *
 * @author remi-team
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
     * 基于 RedisService 构造（复用 remi-common-redis 连接）
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
        log.info("[RedisPubSubMQ] 初始化成功（复用 remi-common-redis 连接）");
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
            // RedisTemplate 由 remi-common-redis 管理，无需关闭
            log.info("[Redis-PubSub] 队列已关闭");
        } finally {
            closeLock.unlock();
        }
    }

    /**
     * 登记订阅者到内部映射。
     *
     * <p>便于 {@link #close()} 时统一关闭所有订阅者、防止连接泄漏。
     * 仅登记由本 MQ 创建的订阅者；{@code subscriberId} 重复会覆盖旧条目（旧订阅者不再被本 MQ 管理，需调用方自行关闭）。
     * {@code subscriberId} 或 {@code subscriber} 为 {@code null} 时静默忽略。
     *
     * @param subscriberId 订阅者唯一 ID，用于后续移除/关闭
     * @param subscriber   订阅者实例
     */
    public void registerSubscriber(String subscriberId, RedisPubSubSubscriber subscriber) {
        if (subscriberId != null && subscriber != null) {
            subscribers.put(subscriberId, subscriber);
            log.debug("[RedisPubSubMQ] 订阅者已注册，subscriberId={}", subscriberId);
        }
    }

    /**
     * 从内部映射移除订阅者登记。
     *
     * <p>通常在订阅者主动取消订阅时调用。注意：本方法<b>仅移除引用、不主动关闭底层订阅连接</b>，
     * 以避免影响仍持有该订阅者的其它引用；真正的资源释放交由 {@link #close()} 或订阅者自身的 {@code shutdown()}。
     * {@code subscriberId} 为 {@code null} 时忽略。
     *
     * @param subscriberId 待移除的订阅者 ID
     */
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
