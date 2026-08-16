package com.njydsz.common.queue.service.impl;

import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.connection.Message;
import org.springframework.data.redis.connection.MessageListener;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.listener.ChannelTopic;
import org.springframework.data.redis.listener.RedisMessageListenerContainer;
import com.njydsz.common.queue.config.QueueProperties;
import com.njydsz.common.queue.domain.QueueMessage;
import com.njydsz.common.queue.queue.RedisPubSubMQ;
import com.njydsz.common.queue.rate.ConsumerRateLimiter;
import com.njydsz.common.queue.service.IMessageHandler;
import com.njydsz.common.queue.service.IMessageSubscriber;

/**
 * 基于 Redis PubSub 的消息订阅者。
 *
 * <p>使用 Redis <b>SUBSCRIBE</b> 命令订阅指定频道，接收发布者广播的消息。
 * 与 {@link RedisStreamSubscriber} 不同，PubSub 模式为「即发即忘」广播，
 * 不支持消息确认、重试和死信队列，适用于实时通知、缓存失效等允许丢消息的场景。
 *
 * <h3>工作原理</h3>
 * <ol>
 *   <li>通过 {@link RedisMessageListenerContainer} 注册 {@link MessageListener}</li>
 *   <li>消息到达时调用 {@link ConsumerRateLimiter#acquire()} 限流后处理</li>
 *   <li>处理异常仅记录日志和 lastError，不重试（PubSub 无 ACK 机制）</li>
 * </ol>
 *
 * <h3>线程安全</h3>
 * <p>{@link AtomicBoolean} 控制运行状态，{@link RedisMessageListenerContainer}
 * 内部使用 Spring TaskExecutor 调度消息回调。
 *
 * @author ydsz-team
 * @since 1.0.0
 */
@Slf4j
public class RedisPubSubSubscriber implements IMessageSubscriber {

    /** 复用 ydsz-common-redis 的 Redis 连接 */
    private final RedisTemplate<String, Object> redisTemplate;
    /** 订阅的 Redis PubSub 频道名称 */
    private final String channel;
    /** 父级 MQ 引用，用于注册/注销订阅者 */
    private final RedisPubSubMQ parentMQ;
    /** 消费端限流器 */
    private final ConsumerRateLimiter rateLimiter;

    /** 订阅运行状态标志 */
    private final AtomicBoolean running;
    /** 累计处理的消息数 */
    private final AtomicLong consumedCount;
    /** 最近一次异常 */
    private final AtomicReference<Throwable> lastError;

    /** Spring Redis 消息监听容器，负责底层 SUBSCRIBE 连接管理 */
    private volatile RedisMessageListenerContainer listenerContainer;

    /**
     * 构造 Redis PubSub 订阅者。
     *
     * @param redisTemplate   Redis 连接模板，不可为空
     * @param channel         频道名称，不可为空
     * @param parentMQ        父级 MQ 引用（可为 null），用于注册/注销
     * @param queueProperties 队列配置（可为 null，此时不限流）
     * @throws IllegalArgumentException redisTemplate 或 channel 为空时抛出
     */
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
        this.rateLimiter = queueProperties != null
                ? new ConsumerRateLimiter(queueProperties.getConsumerRateLimitPerSecond())
                : new ConsumerRateLimiter(0);
    }

    /**
     * {@inheritDoc}
     *
     * <p>PubSub 模式为异步推送，不支持同步拉取。调用此方法仅记录警告。
     *
     * @return 始终返回 {@code null}
     */
    @Override
    public String subscribe() {
        // PubSub 模式不支持同步订阅，需使用 subscribeAsync
        log.warn("[RedisPubSub] PubSub 模式不支持同步订阅，请使用 subscribeAsync()");
        return null;
    }

    /**
     * {@inheritDoc}
     *
     * <p>创建 {@link RedisMessageListenerContainer} 并注册 {@link MessageListener}，
     * 底层通过 Redis SUBSCRIBE 命令建立长连接。消息到达时先限流再处理。
     * 重复调用时仅记录警告。
     *
     * @param handler 消息处理回调
     * @return 频道名称
     */
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

    /**
     * 通过 {@link RedisMessageListenerContainer} 注册异步监听。
     *
     * <p>消息回调流程：限流 → 解析消息体 → 调用 handler → 记录指标。
     * 异常仅记录 lastError，不重试（PubSub 无 ACK 机制）。
     *
     * @param handler 消息处理回调
     */
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
        log.info("[RedisPubSub] 异步订阅已启动（复用 ydsz-common-redis 连接），channel={}", channel);
    }

    /**
     * 处理单条 PubSub 消息。
     *
     * <p>将原始字符串解析为 {@link QueueMessage}，然后调用 handler.onMessage()。
     * 解析失败时降级为原始字符串消息。
     *
     * @param message 原始消息体字符串
     * @param handler 消息处理回调
     */
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

    /**
     * {@inheritDoc}
     *
     * <p>停止监听容器并从父级 MQ 注销订阅者。
     */
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

    /**
     * 关闭 {@link RedisMessageListenerContainer}，释放 Redis SUBSCRIBE 连接。
     *
     * <p>依次调用 stop() 和 destroy()，异常仅记录警告不向上抛出，
     * 确保关闭流程不会因单个订阅者失败而中断。
     */
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
