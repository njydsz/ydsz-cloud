package com.njydsz.common.queue.config;

import com.njydsz.common.queue.enums.QueueType;
import jakarta.annotation.PostConstruct;
import jakarta.validation.constraints.Min;
import java.util.Arrays;
import java.util.List;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.properties.ConfigurationProperties;

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
 * @since 1.0.0
 */
@Slf4j
@Data
@ConfigurationProperties(prefix = "ydsz.queue")
public class QueueProperties {

  /** 是否启用消息队列模块 */
  private boolean enabled = true;

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
  private boolean deadLetterRetryEnabled = true;

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
  private boolean dedupEnabled = false;

  /** 消息去重窗口（毫秒，默认 300000 = 5 分钟） */
  private long dedupWindowMillis = 300_000L;

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
