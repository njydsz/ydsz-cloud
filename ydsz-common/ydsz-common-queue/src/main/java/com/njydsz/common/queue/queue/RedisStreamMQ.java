package com.njydsz.common.queue.queue;

import java.util.concurrent.ExecutorService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import com.njydsz.common.exception.custom.BusinessException;
import com.njydsz.common.queue.config.QueueProperties;
import com.njydsz.common.queue.service.IMessagePublisher;
import com.njydsz.common.queue.service.IMessageSubscriber;
import com.njydsz.common.queue.service.impl.RedisStreamPublisher;
import com.njydsz.common.queue.service.impl.RedisStreamSubscriber;

/**
 * 基于 Redis Stream 实现的消息队列
 *
 * <p>Redis Stream 是 Redis 5.0 引入的数据结构，提供了更强大的消息持久化和消费组功能。
 * 它是 List 的增强版，支持消息确认、消费组、消息ID排序等高级特性。
 *
 * <p><b>连接复用：</b>通过 RedisTemplate 复用 ydsz-common-redis 的连接。
 *
 * @author ydsz-team
 * @since 1.0.0
 */
@Slf4j
public class RedisStreamMQ extends AbstractMessageQueue {

    private final QueueProperties queueProperties;
    private final RedisTemplate<String, Object> redisTemplate;
    private final ExecutorService consumerExecutor;

    /**
     * 基于 RedisTemplate 构造（复用 ydsz-common-redis 连接，推荐）
     *
     * @param redisTemplate    Redis 模板
     * @param config           队列配置
     * @param consumerExecutor 异步消费者线程池（可为 null，将退化到裸线程，不推荐）
     */
    public RedisStreamMQ(RedisTemplate<String, Object> redisTemplate,
                         QueueProperties config,
                         ExecutorService consumerExecutor) {
        super("Redis-Stream");
        if (config == null) {
            throw BusinessException.builder().key("队列配置不能为空").build();
        }
        this.queueProperties = config;
        this.redisTemplate = redisTemplate;
        this.consumerExecutor = consumerExecutor;
        log.info("[RedisStreamMQ] 初始化成功（复用 ydsz-common-redis 连接），消费者组: {}",
                config.getStreamGroup());
    }

    @Override
    public IMessagePublisher createPublisher(String channel) {
        checkNotClosed();
        if (channel == null || channel.isEmpty()) {
            throw BusinessException.builder().key("通道名称不能为空").build();
        }
        return new RedisStreamPublisher(redisTemplate, channel);
    }

    @Override
    public IMessageSubscriber createSubscriber(String channel) {
        checkNotClosed();
        if (channel == null || channel.isEmpty()) {
            throw BusinessException.builder().key("通道名称不能为空").build();
        }
        return new RedisStreamSubscriber(redisTemplate, channel, queueProperties, consumerExecutor);
    }

    @Override
    public String[] getChannels() {
        return new String[0];
    }

    @Override
    protected void doClose() {
        // RedisTemplate 由 ydsz-common-redis 管理，无需关闭
        log.info("[Redis-Stream] 队列已关闭");
    }
}
