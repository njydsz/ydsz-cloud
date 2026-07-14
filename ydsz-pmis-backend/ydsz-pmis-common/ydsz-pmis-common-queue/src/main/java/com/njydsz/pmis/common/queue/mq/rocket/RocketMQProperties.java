package com.njydsz.pmis.common.queue.mq.rocket;

import com.njydsz.pmis.common.queue.config.QueueProperties;

import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * RocketMQ 消息队列配置属性
 *
 * <p>封装 RocketMQ 消息队列的连接和行为配置参数。
 *
 * <p><b>配置示例：</b>
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
 * @author ydsz-pmis-team
 * @since 1.0.0
 * @since 1.0.0
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class RocketMQProperties extends QueueProperties {

    /**
     * NameServer 地址
     */
    private String namesrvAddr = "localhost:9876";

    /**
     * 消费者组ID
     */
    private String groupId = "ydsz-consumer-group";

    /**
     * 默认主题
     */
    private String topic = "ydsz-rocketmq-topic";

    /**
     * 消息标签
     */
    private String tag = "*";

    /**
     * 接入密钥（阿里云 MQ 使用）
     */
    private String accessKey;

    /**
     * 密钥（阿里云 MQ 使用）
     */
    private String secretKey;

    /**
     * 是否启用顺序消息
     */
    private boolean orderly = false;

    /**
     * 消费线程数
     */
    private int consumeThreadMin = 10;
    private int consumeThreadMax = 20;

    /**
     * 批量消费大小
     */
    private int consumeMessageBatchMaxSize = 1;

    /**
     * 最大重试次数
     */
    private int maxRetryCount = 3;

    /**
     * 解析获取 namesrvAddr
     */
    public String resolvedNamesrvAddr() {
        return isNotBlank(namesrvAddr) ? namesrvAddr : "localhost:9876";
    }

    /**
     * 解析获取 groupId
     */
    public String resolvedGroupId() {
        return isNotBlank(groupId) ? groupId : "ydsz-consumer-group";
    }

    /**
     * 解析获取 topic
     */
    public String resolvedTopic() {
        return isNotBlank(topic) ? topic : "ydsz-rocketmq-topic";
    }

    private boolean isNotBlank(String str) {
        return str != null && !str.trim().isEmpty();
    }
}