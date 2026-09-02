package com.njydsz.common.queue.queue;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutorService;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.data.redis.core.RedisTemplate;

import com.njydsz.common.exception.custom.BusinessException;
import com.njydsz.common.queue.config.QueueProperties;
import com.njydsz.common.queue.enums.QueueType;
import com.njydsz.common.queue.mq.kafka.KafkaMQ;
import com.njydsz.common.queue.mq.kafka.KafkaQueueProperties;
import com.njydsz.common.queue.mq.rabbit.RabbitMQ;
import com.njydsz.common.queue.mq.rabbit.RabbitMQProperties;
import com.njydsz.common.queue.mq.rocket.RocketMQ;
import com.njydsz.common.queue.mq.rocket.RocketMQProperties;

/**
 * 消息队列工厂类
 *
 * <p>根据队列类型创建对应的消息队列实例。 支持 Redis List、Redis PubSub、Redis Stream、Kafka、RocketMQ、RabbitMQ 等多种消息队列。
 *
 * <p><b>Redis 连接复用：</b> Redis 队列实例复用 ydsz-common-redis 的连接，由 RedisTemplate 统一管理。
 *
 * <p><b>线程池复用：</b> 异步消费者统一使用 Spring 管理的 {@link ExecutorService}，避免业务代码直接创建裸线程。
 *
 * <p><b>生命周期管理：</b> 工厂持有所有创建的队列实例引用，实现 {@link DisposableBean}，确保 Spring 容器关闭时统一释放。 单工厂创建的队列实例上限为
 * {@link #MAX_HELD_QUEUES}，超过时记录 WARN 日志并触发部分关闭。
 *
 * @author ydsz-team
 * @since 26.09.01
 */
@Slf4j
public class MessageQueueFactory implements IMessageQueueProvider, DisposableBean {

  /**
   * 单工厂持有的队列实例上限
   *
   * <p>超过此阈值时记录告警并触发兜底关闭最老的实例，防止连接泄漏。
   */
  private static final int MAX_HELD_QUEUES = 100;

  private final QueueProperties properties;
  private final RedisTemplate<String, Object> redisTemplate;
  private final ExecutorService consumerExecutor;
  private final List<IMessageQueue> createdQueues = new CopyOnWriteArrayList<>();

  /**
   * 构造函数（基于 RedisTemplate，推荐）
   *
   * @param properties 队列配置
   * @param redisTemplate Redis 模板（可为 null，仅使用非 Redis 队列时允许）
   * @param consumerExecutor 异步消费者线程池（可为 null，将退化到裸线程，不推荐）
   */
  public MessageQueueFactory(
      QueueProperties properties,
      RedisTemplate<String, Object> redisTemplate,
      ExecutorService consumerExecutor) {
    if (properties == null) {
      throw BusinessException.builder().key("队列配置不能为空").build();
    }
    this.properties = properties;
    this.redisTemplate = redisTemplate;
    this.consumerExecutor = consumerExecutor;
  }

  /** Spring 容器关闭时兜底关闭所有持有的队列实例，防止连接泄漏。 */
  @Override
  public void destroy() {
    close();
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
    // 容量上限检查，防止无限增长
    if (createdQueues.size() >= MAX_HELD_QUEUES) {
      evictOldestQueue();
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
      default:
        throw BusinessException.builder().key("不支持的消息平台: " + type).build();
    }
    createdQueues.add(queue);
    return queue;
  }

  /**
   * 关闭并移除最老的队列实例（兜底策略）。
   *
   * <p>当持有的队列数量超过 {@link #MAX_HELD_QUEUES} 时触发， 关闭最老的实例以释放资源，避免 OOM / 连接泄漏。
   */
  private void evictOldestQueue() {
    if (createdQueues.isEmpty()) {
      return;
    }
    IMessageQueue oldest = createdQueues.get(0);
    log.warn(
        "[MessageQueueFactory] 队列实例数量达到上限 {}，关闭最老实例 type={}, " + "请关注是否存在队列泄漏（创建后未 close）",
        MAX_HELD_QUEUES,
        oldest.getType());
    try {
      oldest.close();
    } catch (Exception e) {
      log.warn("[MessageQueueFactory] 关闭最老队列实例时异常", e);
    }
    createdQueues.remove(0);
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

  private KafkaQueueProperties extractKafkaProperties() {
    KafkaQueueProperties kafkaProperties = new KafkaQueueProperties();
    kafkaProperties.setBootstrapServers(properties.getHost() + ":" + properties.getPort());
    kafkaProperties.setGroupId(properties.getStreamGroup());
    kafkaProperties.setTopic(properties.getStreamConsumer());
    kafkaProperties.setEnableAutoCommit(false);
    kafkaProperties.setAutoOffsetReset("earliest");
    kafkaProperties.setMaxPollRecords(properties.getStreamBatchSize());
    return kafkaProperties;
  }

  private RocketMQProperties extractRocketMQProperties() {
    RocketMQProperties rocketProperties = new RocketMQProperties();
    rocketProperties.setNamesrvAddr(properties.getHost() + ":" + properties.getPort());
    rocketProperties.setGroupId(properties.getStreamGroup());
    rocketProperties.setTopic(properties.getStreamConsumer());
    return rocketProperties;
  }

  private RabbitMQProperties extractRabbitMQProperties() {
    RabbitMQProperties rabbitProperties = new RabbitMQProperties();
    rabbitProperties.setHost(properties.getHost());
    rabbitProperties.setRabbitPort(properties.getPort());
    rabbitProperties.setUsername(properties.getUsername());
    rabbitProperties.setPassword(properties.getPassword());
    rabbitProperties.setQueueName(properties.getStreamConsumer());
    return rabbitProperties;
  }
}
