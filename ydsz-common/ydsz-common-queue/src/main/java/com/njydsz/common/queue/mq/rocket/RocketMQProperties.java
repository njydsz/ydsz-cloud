package com.njydsz.common.queue.mq.rocket;

import lombok.Data;
import lombok.EqualsAndHashCode;
import org.springframework.boot.context.properties.ConfigurationProperties;

import com.njydsz.common.queue.config.QueueProperties;

/**
 * RocketMQ 消息队列配置属性
 *
 * <p>封装 RocketMQ 消息队列的连接和行为配置参数。绑定前缀 {@code ydsz.queue.rocketmq}，
 * 与通用 {@link QueueProperties}（前缀 {@code ydsz.queue}）独立绑定，避免字段映射错位。
 *
 * <p><b>配置示例：</b>
 *
 * <pre>{@code
 * ydsz:
 *   queue:
 *     rocketmq:
 *       namesrv-addr: localhost:9876
 *       group-id: ydsz-consumer-group
 *       topic: ydsz-topic
 *       access-key: your-access-key
 *       secret-key: your-secret-key
 * }</pre>
 *
 * @author ydsz-team
 * @since 26.09.01
 */
@Data
@EqualsAndHashCode(callSuper = true)
@ConfigurationProperties(prefix = "ydsz.queue.rocketmq")
public class RocketMQProperties extends QueueProperties {

  /** NameServer 地址 */
  private String namesrvAddr = "localhost:9876";

  /** 消费者组ID */
  private String groupId = "ydsz-consumer-group";

  /** 默认主题 */
  private String topic = "ydsz-rocketmq-topic";

  /** 消息标签 */
  private String tag = "*";

  /** 接入密钥（阿里云 MQ 使用） */
  private String accessKey;

  /** 密钥（阿里云 MQ 使用） */
  private String secretKey;

  /**
   * 是否启用顺序消息
   *
   * <p>开启后生产者通过队列选择器按 {@code messageGroupKey} 哈希路由到同一 MessageQueue，保证单队列内的顺序消费。
   * 不同分组键仍可能路由到同一队列，互相之间不保证顺序。
   */
  private boolean isOrderly = false;

  /** 消费线程数 */
  private int consumeThreadMin = 10;

  private int consumeThreadMax = 20;

  /** 批量消费大小 */
  private int consumeMessageBatchMaxSize = 1;

  /** 最大重试次数 */
  private int maxRetryCount = 3;

  /**
   * 解析获取 namesrvAddr。
   *
   * @return NameServer 地址（未配置时默认 {@code "localhost:9876"}）
   */
  public String resolvedNamesrvAddr() {
    return isNotBlank(namesrvAddr) ? namesrvAddr : "localhost:9876";
  }

  /**
   * 解析获取 groupId。
   *
   * @return 消费者组 ID（未配置时默认 {@code "ydsz-consumer-group"}）
   */
  public String resolvedGroupId() {
    return isNotBlank(groupId) ? groupId : "ydsz-consumer-group";
  }

  /**
   * 解析获取 topic。
   *
   * @return topic 名称（未配置时默认 {@code "ydsz-rocketmq-topic"}）
   */
  public String resolvedTopic() {
    return isNotBlank(topic) ? topic : "ydsz-rocketmq-topic";
  }

  private boolean isNotBlank(String str) {
    return str != null && !str.trim().isEmpty();
  }
}
