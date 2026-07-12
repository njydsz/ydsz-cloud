package com.njydsz.pmis.common.queue.mq.kafka;

import com.njydsz.pmis.common.queue.config.QueueProperties;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * Kafka 消息队列配置属性
 *
 * <p>封装 Kafka 消息队列的连接和行为配置参数。
 *
 * <p><b>配置示例：</b>
 * <pre>{@code
 * remi:
 *   queue:
 *     kafka:
 *       bootstrap-servers: localhost:9092
 *       group-id: remi-consumer-group
 *       topic: remi-topic
 *       enable-auto-commit: false
 *       auto-offset-reset: earliest
 *       max-poll-records: 10
 * }</pre>
 *
 * @author Marvin Lee
 * @email limw1888@126.com
 * @version 3.5.0
 * @since 1.0.0
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class KafkaQueueProperties extends QueueProperties {

    /**
     * Kafka 服务地址
     */
    private String bootstrapServers = "localhost:9092";

    /**
     * 消费者组ID
     */
    private String groupId = "remi-consumer-group";

    /**
     * 默认主题
     */
    private String topic = "remi-kafka-topic";

    /**
     * 是否自动提交偏移量
     */
    private boolean enableAutoCommit = false;

    /**
     * 自动偏移量重置策略
     * <p>可选值：earliest、latest
     */
    private String autoOffsetReset = "earliest";

    /**
     * 每次最大拉取消息数
     */
    private int maxPollRecords = 10;

    /**
     * 消费者会话超时时间（毫秒）
     */
    private int sessionTimeoutMs = 30000;

    /**
     * 消费者心跳间隔时间（毫秒）
     */
    private int heartbeatIntervalMs = 10000;

    /**
     * 消息键序列化器
     */
    private String keySerializer = "org.apache.kafka.common.serialization.StringSerializer";

    /**
     * 消息值序列化器
     */
    private String valueSerializer = "org.apache.kafka.common.serialization.StringSerializer";

    /**
     * 消息键反序列化器
     */
    private String keyDeserializer = "org.apache.kafka.common.serialization.StringDeserializer";

    /**
     * 消息值反序列化器
     */
    private String valueDeserializer = "org.apache.kafka.common.serialization.StringDeserializer";

    /**
     * 解析获取 bootstrap-servers
     */
    public String resolvedBootstrapServers() {
        return isNotBlank(bootstrapServers) ? bootstrapServers : "localhost:9092";
    }

    /**
     * 解析获取 group-id
     */
    public String resolvedGroupId() {
        return isNotBlank(groupId) ? groupId : "remi-consumer-group";
    }

    /**
     * 解析获取 topic
     */
    public String resolvedTopic() {
        return isNotBlank(topic) ? topic : "remi-kafka-topic";
    }

    private boolean isNotBlank(String str) {
        return str != null && !str.trim().isEmpty();
    }
}