package com.njydsz.common.queue.queue;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutorService;

import org.springframework.data.redis.core.RedisTemplate;

import com.njydsz.common.exception.custom.BusinessException;
import com.njydsz.common.queue.config.QueueProperties;
import com.njydsz.common.queue.enums.QueueType;
import com.njydsz.common.queue.mq.active.ActiveMQ;
import com.njydsz.common.queue.mq.active.ActiveMQProperties;
import com.njydsz.common.queue.mq.kafka.KafkaMQ;
import com.njydsz.common.queue.mq.kafka.KafkaQueueProperties;
import com.njydsz.common.queue.mq.rabbit.RabbitMQ;
import com.njydsz.common.queue.mq.rabbit.RabbitMQProperties;
import com.njydsz.common.queue.mq.rocket.RocketMQ;
import com.njydsz.common.queue.mq.rocket.RocketMQProperties;

import lombok.extern.slf4j.Slf4j;

/**
 * 消息队列工厂类
 *
 * <p>根据队列类型创建对应的消息队列实例。
 * 支持 Redis List、Redis PubSub、Redis Stream、Kafka、RocketMQ、RabbitMQ、ActiveMQ 等多种消息队列。
 *
 * <p><b>Redis 连接复用：</b>
 * Redis 队列实例复用 ydsz-common-redis 的连接，由 RedisTemplate 统一管理。
 *
 * <p><b>线程池复用：</b>
 * 异步消费者统一使用 Spring 管理的 {@link ExecutorService}，避免业务代码直接创建裸线程。
 *
 * @author ydsz-team
 * @since 1.0.0
 */
@Slf4j
public class MessageQueueFactory implements IMessageQueueProvider {

    private final QueueProperties properties;
    private final RedisTemplate<String, Object> redisTemplate;
    private final ExecutorService consumerExecutor;
    private final List<IMessageQueue> createdQueues = new CopyOnWriteArrayList<>();

    /**
     * 构造函数（基于 RedisTemplate，推荐）
     *
     * @param properties       队列配置
     * @param redisTemplate    Redis 模板（可为 null，仅使用非 Redis 队列时允许）
     * @param consumerExecutor 异步消费者线程池（可为 null，将退化到裸线程，不推荐）
     */
    public MessageQueueFactory(QueueProperties properties, RedisTemplate<String, Object> redisTemplate, ExecutorService consumerExecutor) {
        if (properties == null) {
            throw BusinessException.builder().key("队列配置不能为空").build();
        }
        this.properties = properties;
        this.redisTemplate = redisTemplate;
        this.consumerExecutor = consumerExecutor;
    }

    @Override
    public void close() {
        log.info("[MessageQueueFactory] 关闭，共 {} 个队列实例", createdQueues.size());
        for (IMessageQueue queue : createdQueues) {
            try {
                queue.close();
            } catch (Exception e) {
                log.warn("[MessageQueueFactory] 关闭队列实例时异常: {}", queue.getType(), e);
            }
        }
        createdQueues.clear();
    }

    @Override
    public IMessageQueue createMessageQueue(QueueType type, String... args) {
        if (type == null) {
            throw BusinessException.builder().key("队列类型不能为空").build();
        }
        IMessageQueue queue;
        switch (type) {
            case LIST:
                queue = createRedisListMQ();
                break;
            case PUBSUB:
                queue = createRedisPubSubMQ();
                break;
            case STREAM:
                queue = createRedisStreamMQ();
                break;
            case KAFKA:
                queue = createKafkaMQ();
                break;
            case ROCKET:
                queue = createRocketMQ();
                break;
            case RABBIT:
                queue = createRabbitMQ();
                break;
            case ACTIVE:
                queue = createActiveMQ();
                break;
            default:
                throw BusinessException.builder().key("不支持的消息平台: " + type).build();
        }
        createdQueues.add(queue);
        return queue;
    }

    private IMessageQueue createRedisListMQ() {
        if (redisTemplate == null) {
            throw BusinessException.builder().key("使用 Redis 队列需引入 ydsz-common-redis 模块").build();
        }
        log.info("[Factory] 创建 Redis List 队列（复用 ydsz-common-redis 连接）");
        return new RedisListMQ(redisTemplate, properties, consumerExecutor);
    }

    private IMessageQueue createRedisPubSubMQ() {
        if (redisTemplate == null) {
            throw BusinessException.builder().key("使用 Redis 队列需引入 ydsz-common-redis 模块").build();
        }
        log.info("[Factory] 创建 Redis PubSub 队列（复用 ydsz-common-redis 连接）");
        return new RedisPubSubMQ(redisTemplate, properties);
    }

    private IMessageQueue createRedisStreamMQ() {
        if (redisTemplate == null) {
            throw BusinessException.builder().key("使用 Redis 队列需引入 ydsz-common-redis 模块").build();
        }
        log.info("[Factory] 创建 Redis Stream 队列（复用 ydsz-common-redis 连接）");
        return new RedisStreamMQ(redisTemplate, properties, consumerExecutor);
    }

    private IMessageQueue createKafkaMQ() {
        log.info("[Factory] 创建 Kafka 队列");
        KafkaQueueProperties kafkaProperties = extractKafkaProperties();
        return new KafkaMQ(kafkaProperties, consumerExecutor);
    }

    private IMessageQueue createRocketMQ() {
        log.info("[Factory] 创建 RocketMQ 队列");
        RocketMQProperties rocketProperties = extractRocketMQProperties();
        return new RocketMQ(rocketProperties);
    }

    private IMessageQueue createRabbitMQ() {
        log.info("[Factory] 创建 RabbitMQ 队列");
        RabbitMQProperties rabbitProperties = extractRabbitMQProperties();
        return new RabbitMQ(rabbitProperties);
    }

    private IMessageQueue createActiveMQ() {
        log.info("[Factory] 创建 ActiveMQ 队列");
        ActiveMQProperties activeProperties = extractActiveMQProperties();
        return new ActiveMQ(activeProperties, consumerExecutor);
    }

    private KafkaQueueProperties extractKafkaProperties() {
        KafkaQueueProperties kafkaProperties = new KafkaQueueProperties();
        kafkaProperties.setBootstrapServers(properties.resolvedHost() + ":" + properties.resolvedPort());
        kafkaProperties.setGroupId(properties.getStreamGroup());
        kafkaProperties.setTopic(properties.getStreamConsumer());
        kafkaProperties.setEnableAutoCommit(false);
        kafkaProperties.setAutoOffsetReset("earliest");
        kafkaProperties.setMaxPollRecords(properties.getStreamBatchSize());
        return kafkaProperties;
    }

    private RocketMQProperties extractRocketMQProperties() {
        RocketMQProperties rocketProperties = new RocketMQProperties();
        rocketProperties.setNamesrvAddr(properties.resolvedHost() + ":" + properties.resolvedPort());
        rocketProperties.setGroupId(properties.getStreamGroup());
        rocketProperties.setTopic(properties.getStreamConsumer());
        return rocketProperties;
    }

    private RabbitMQProperties extractRabbitMQProperties() {
        RabbitMQProperties rabbitProperties = new RabbitMQProperties();
        rabbitProperties.setHost(properties.resolvedHost());
        rabbitProperties.setRabbitPort(properties.resolvedPort());
        rabbitProperties.setUsername(properties.getUsername());
        rabbitProperties.setPassword(properties.resolvedPassword());
        rabbitProperties.setQueueName(properties.getStreamConsumer());
        return rabbitProperties;
    }

    private ActiveMQProperties extractActiveMQProperties() {
        ActiveMQProperties activeProperties = new ActiveMQProperties();
        activeProperties.setBrokerUrl("tcp://" + properties.resolvedHost() + ":" + properties.resolvedPort());
        activeProperties.setUsername(properties.getUsername());
        activeProperties.setPassword(properties.resolvedPassword());
        activeProperties.setQueueName(properties.getStreamConsumer());
        return activeProperties;
    }
}
