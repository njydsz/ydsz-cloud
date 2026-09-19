package com.njydsz.common.queue.config;

import java.util.Arrays;
import java.util.List;

import jakarta.annotation.PostConstruct;
import jakarta.validation.constraints.Min;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.properties.ConfigurationProperties;

import com.njydsz.common.queue.enums.QueueType;
import com.njydsz.common.queue.mq.kafka.KafkaQueueProperties;
import com.njydsz.common.queue.mq.rabbit.RabbitMQProperties;

/**
 * 消息队列配置属性类
 *
 * <p>绑定前缀为 {@code ydsz.queue} 的 YAML 配置，提供消息队列引擎的连接参数、 消费策略、死信队列、消息去重等配置项的声明式管理。
 *
 * <p>支持通过 Nacos 动态推送配置变更，Spring Boot 自动热加载生效。
 *
 * <p><b>最小配置示例：</b>
 *
 * <pre>{@code
 * ydsz:
 *   queue:
 *     enabled: true
 *     type: STREAM
 * }</pre>
 *
 * @author ydsz-team
 * @since 26.09.01
 */
@Slf4j
@Data
@ConfigurationProperties(prefix = "ydsz.queue")
public class QueueProperties {

  /** 是否启用消息队列模块 */
  private boolean isEnabled = true;

  /**
   * 队列引擎类型
   *
   * <p>支持：STREAM / KAFKA / ROCKET / LIST(已废弃) / PUBSUB(已废弃) / RABBIT(已废弃) / ACTIVE(已废弃)
   */
  private QueueType type;

  /** 队列类型字符串（兼容旧配置） */
  private String typeStr;

  /** Redis 服务器地址（也用于非 Redis 队列的通用主机配置） */
  private String host = "127.0.0.1";

  /** Redis 服务器端口（也用于非 Redis 队列的通用端口配置） */
  private int port = 6379;

  /** Redis 密码 */
  private String password;

  /** Redis 用户名 */
  private String username;

  /** 连接超时时间（毫秒） */
  @Min(1)
  private int timeout = 3000;

  /** List 队列阻塞超时时间（秒） */
  private long listBlockTimeoutSeconds = 5;

  /** Stream 队列消费者组名称 */
  private String streamGroup = "group-1";

  /** Stream 队列消费者名称 */
  private String streamConsumer = "consumer-1";

  /** Stream 队列消费失败最大重试次数 */
  private int streamRetryMax = 3;

  /** Stream 队列阻塞读取时间（毫秒） */
  private long streamBlockMillis = 2000;

  /** Stream 队列批量拉取大小 */
  private int streamBatchSize = 10;

  /** Stream 队列死信队列后缀 */
  private String streamDeadLetterSuffix = ":dlq";

  /** 消费者限流速率（每秒消息数，0=不限流） */
  private int consumerRateLimitPerSecond = 0;

  /** 异步消费者线程池核心线程数 */
  private int consumerExecutorCoreSize = 2;

  /** 异步消费者线程池最大线程数 */
  private int consumerExecutorMaxSize = 16;

  /** 异步消费者线程池任务队列容量 */
  private int consumerExecutorQueueCapacity = 256;

  /** 异步消费者线程池线程名前缀 */
  private String consumerExecutorThreadNamePrefix = "queue-consumer-";

  /** 异步消费者线程池优雅停机等待秒数 */
  private int consumerExecutorAwaitTerminationSeconds = 30;

  /** 是否启用死信队列自动重试 */
  private boolean isDeadLetterRetryEnabled = true;

  /** 死信队列最大重试次数 */
  @Min(1)
  private int deadLetterMaxRetries = 3;

  /** 死信队列重试间隔（毫秒） */
  private long deadLetterRetryInterval = 60000;

  /**
   * 死信队列重试抖动百分比（0-100，0=无抖动）
   *
   * <p>多实例部署时，各实例在基础延迟上附加 [0, interval * jitterPercent / 100] 的随机抖动， 避免所有实例同时扫描死信队列造成惊群。
   */
  private int deadLetterRetryJitterPercent = 30;

  /** 是否启用消息去重（默认 false，分布式场景推荐使用 ydsz-common-redis 的 RedisMessageDeduplicator） */
  private boolean isDedupEnabled = false;

  /** 消息去重窗口（毫秒，默认 300000 = 5 分钟） */
  private long dedupWindowMillis = 300_000L;

  /**
   * Kafka 专属配置（独立前缀 {@code ydsz.queue.kafka.*}）。
   *
   * <p>使用 {@link org.springframework.boot.context.properties.NestedConfigurationProperty} 既保留 IDE 自动补全又兼容 relaxed binding。
   * 当用户未配置任何 {@code ydsz.queue.kafka.*} 字段时，相关访问器回退到通用 host:port 配置。
   */
  @org.springframework.boot.context.properties.NestedConfigurationProperty
  private KafkaQueueProperties kafka;

  /**
   * RabbitMQ 专属配置（独立前缀 {@code ydsz.queue.rabbitmq.*}）。
   */
  @org.springframework.boot.context.properties.NestedConfigurationProperty
  private RabbitMQProperties rabbitmq;

  /**
   * 配置初始化校验
   *
   * <p>在 Spring 容器初始化完成后验证配置的基本有效性。
   */
  @PostConstruct
  public void validate() {
    if (type == null && (typeStr == null || typeStr.trim().isEmpty())) {
      throw new IllegalStateException("ydsz.queue.type 必须配置有效的队列引擎类型");
    }
    if (port <= 0 || port > 65535) {
      throw new IllegalArgumentException("ydsz.queue.port 无效: " + port);
    }
    if (timeout <= 0) {
      throw new IllegalArgumentException("ydsz.queue.timeout 必须大于 0");
    }
    if (deadLetterMaxRetries <= 0) {
      throw new IllegalArgumentException("ydsz.queue.deadLetterMaxRetries 必须大于 0");
    }
    if (deadLetterRetryInterval <= 0) {
      throw new IllegalArgumentException("ydsz.queue.deadLetterRetryInterval 必须大于 0");
    }
    if (deadLetterRetryJitterPercent < 0 || deadLetterRetryJitterPercent > 100) {
      throw new IllegalArgumentException("ydsz.queue.deadLetterRetryJitterPercent 必须在 0-100 之间");
    }
    if (dedupWindowMillis <= 0) {
      throw new IllegalArgumentException("ydsz.queue.dedupWindowMillis 必须大于 0");
    }
  }

  /**
   * 获取解析后的队列类型
   *
   * <p>优先返回 {@link #type} 枚举值；若为 null 则从 {@link #typeStr} 解析。
   *
   * @return 队列类型枚举
   * @throws IllegalStateException 当 type 和 typeStr 均为空时抛出
   */
  public QueueType getResolvedType() {
    if (type != null) {
      return type;
    }
    if (typeStr != null && !typeStr.trim().isEmpty()) {
      return QueueType.fromValue(typeStr);
    }
    throw new IllegalStateException("队列类型不能为空，请配置 ydsz.queue.type");
  }

  /**
   * 获取 Kafka bootstrap-servers（优先使用独立前缀 {@code ydsz.queue.kafka.bootstrap-servers}）。
   *
   * @return Kafka bootstrap-servers，未配置时返回 host:port 拼接
   */
  public String getKafkaBootstrapServers() {
    if (kafka != null && kafka.getBootstrapServers() != null
        && !kafka.getBootstrapServers().isBlank()
        && !"localhost:9092".equals(kafka.getBootstrapServers())) {
      return kafka.getBootstrapServers();
    }
    return host + ":" + port;
  }

  /**
   * 获取 RabbitMQ 虚拟主机（优先使用独立前缀 {@code ydsz.queue.rabbitmq.virtual-host}）。
   *
   * @return 虚拟主机，未配置时返回 "/"
   */
  public String getRabbitVirtualHost() {
    if (rabbitmq != null && rabbitmq.getVirtualHost() != null
        && !rabbitmq.getVirtualHost().isBlank()) {
      return rabbitmq.getVirtualHost();
    }
    return "/";
  }

  /**
   * 获取 RabbitMQ 用户名（优先使用独立前缀 {@code ydsz.queue.rabbitmq.username}）。
   *
   * @return 用户名，未配置时返回 "guest"
   */
  public String getRabbitUsername() {
    if (rabbitmq != null && rabbitmq.getUsername() != null
        && !rabbitmq.getUsername().isBlank()) {
      return rabbitmq.getUsername();
    }
    return username != null ? username : "guest";
  }

  /**
   * 获取 RabbitMQ 密码（优先使用独立前缀 {@code ydsz.queue.rabbitmq.password}）。
   *
   * @return 密码，未配置时返回空字符串
   */
  public String getRabbitPassword() {
    if (rabbitmq != null && rabbitmq.getPassword() != null) {
      return rabbitmq.getPassword();
    }
    return password != null ? password : "";
  }

  /**
   * 多实例引擎配置（绑定前缀 {@code ydsz.queue.engines[*]}）。
   *
   * <p>当应用需要同时使用多个 MQ 引擎时（例如事件流走 Kafka、任务队列走 Redis Stream），
   * 通过本列表声明每个引擎的实例名称与队列类型。{@link
   * com.njydsz.common.queue.config.QueueEngineRegistrar} 会为每个声明的引擎注册独立的
   * {@code IMessagePublisher} / {@code IMessageSubscriber} Bean，名称格式为 {@code <name>Publisher} /
   * {@code <name>Subscriber}，便于通过 {@code @Qualifier} 注入。
   *
   * <p><b>配置示例：</b>
   *
   * <pre>{@code
   * ydsz:
   *   queue:
   *     type: KAFKA
   *     engines:
   *       - name: order
   *         type: KAFKA
   *         topic: order-events
   *       - name: notify
   *         type: STREAM
   *         topic: notification-stream
   * }</pre>
   */
  @org.springframework.boot.context.properties.NestedConfigurationProperty
  private List<EngineDefinition> engines = new java.util.ArrayList<>();

  /** 多实例引擎定义（内嵌类，不导出独立 POJO）。 */
  public static class EngineDefinition {
    /** 实例名称，用作注册 Bean 名称的前缀（{@code <name>Publisher} / {@code <name>Subscriber}）。 */
    private String name;
    /** 该引擎实例的队列类型。 */
    private QueueType type;
    /** 该引擎默认发布/订阅的 topic 名称。 */
    private String topic;

    public String getName() {
      return name;
    }

    public void setName(String name) {
      this.name = name;
    }

    public QueueType getType() {
      return type;
    }

    public void setType(QueueType type) {
      this.type = type;
    }

    public String getTopic() {
      return topic;
    }

    public void setTopic(String topic) {
      this.topic = topic;
    }
  }

  /**
   * 获取解析后的参与者 MQ 类型列表（逗号分隔字符串转枚举列表）
   *
   * @param participants 逗号分隔的 MQ 类型字符串，如 "STREAM,KAFKA"
   * @return MQ 类型枚举列表
   */
  public static List<QueueType> parseParticipants(String participants) {
    if (participants == null || participants.trim().isEmpty()) {
      return List.of();
    }
    return Arrays.stream(participants.split(","))
        .map(String::trim)
        .filter(s -> !s.isEmpty())
        .map(QueueType::fromValue)
        .toList();
  }
}
