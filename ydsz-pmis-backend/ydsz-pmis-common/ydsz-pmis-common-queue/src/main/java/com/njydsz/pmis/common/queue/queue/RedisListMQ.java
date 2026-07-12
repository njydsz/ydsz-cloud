package com.njydsz.pmis.common.queue.queue;

import com.njydsz.pmis.common.exception.custom.BusinessException;
import com.njydsz.pmis.common.queue.config.QueueProperties;
import com.njydsz.pmis.common.queue.service.IMessagePublisher;
import com.njydsz.pmis.common.queue.service.IMessageSubscriber;
import com.njydsz.pmis.common.queue.service.impl.RedisListPublisher;
import com.njydsz.pmis.common.queue.service.impl.RedisListSubscriber;
import com.njydsz.pmis.common.redis.service.RedisService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.locks.ReentrantLock;

/**
 * 基于 Redis List 实现的消息队列
 *
 * <p>Redis List 队列是最轻量的消息队列实现，基于 LPUSH/BRPOP 命令实现 FIFO（先进先出）语义。
 * 适用于简单的任务队列场景，不支持消息确认、重试、死信等高级特性。
 *
 * <p><b>连接复用：</b>通过 {@link RedisService} 复用 ydsz-pmis-common-redis 的连接。
 *
 * <p><b>生产环境警告：</b>
 * <ul>
 *   <li>Redis List 不具备消息持久化确认机制，broker 宕机可能丢失未消费消息</li>
 *   <li>不支持消息 ACK / 重试 / 死信队列，不适合关键业务场景</li>
 *   <li>生产环境关键业务请使用 Kafka / RocketMQ / RabbitMQ 等专业消息中间件</li>
 *   <li>仅适用于轻量级任务队列、临时缓冲等非关键场景</li>
 * </ul>
 *
 * @author Marvin Lee
 * @email limw1888@126.com
 * @version 3.5.0
 * @since 1.0.0
 */
@Slf4j
public class RedisListMQ implements IMessageQueue {

    private final QueueProperties queueProperties;
    private final RedisTemplate<String, Object> redisTemplate;
    private final ExecutorService consumerExecutor;
    private volatile boolean closed = false;
    private final ReentrantLock closeLock = new ReentrantLock();

    /**
     * 基于 RedisService 构造（复用 ydsz-pmis-common-redis 连接，推荐）
     *
     * @param redisService     Redis 服务
     * @param config           队列配置
     * @param consumerExecutor 异步消费者线程池（可为 null，将退化到裸线程，不推荐）
     */
    public RedisListMQ(RedisService redisService, QueueProperties config, ExecutorService consumerExecutor) {
        if (config == null) {
            throw BusinessException.builder().key("队列配置不能为空").build();
        }
        this.queueProperties = config;
        this.redisTemplate = redisService.getRedisTemplate();
        this.consumerExecutor = consumerExecutor;
        log.info("[RedisListMQ] 初始化成功（复用 ydsz-pmis-common-redis 连接）");
        log.warn("[RedisListMQ] 警告: Redis List 队列不适合生产环境关键业务场景，" +
                "不具备消息 ACK/重试/死信能力，关键业务请使用 Kafka/RocketMQ/RabbitMQ");
    }

    @Override
    public IMessagePublisher createPublisher(String channel) {
        checkNotClosed();
        if (channel == null || channel.isEmpty()) {
            throw BusinessException.builder().key("通道名称不能为空").build();
        }
        return new RedisListPublisher(redisTemplate, channel);
    }

    @Override
    public IMessageSubscriber createSubscriber(String channel) {
        checkNotClosed();
        if (channel == null || channel.isEmpty()) {
            throw BusinessException.builder().key("通道名称不能为空").build();
        }
        return new RedisListSubscriber(redisTemplate, channel,
                (int) queueProperties.resolvedListBlockTimeoutSeconds(), 10000, queueProperties, consumerExecutor);
    }

    @Override
    public String[] getChannels() {
        return new String[0];
    }

    @Override
    public boolean isClosed() {
        return closed;
    }

    @Override
    public String getType() {
        return "Redis-List";
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
            // RedisTemplate 由 ydsz-pmis-common-redis 管理，无需关闭
            log.info("[Redis-List] 队列已关闭");
        } finally {
            closeLock.unlock();
        }
    }
}
