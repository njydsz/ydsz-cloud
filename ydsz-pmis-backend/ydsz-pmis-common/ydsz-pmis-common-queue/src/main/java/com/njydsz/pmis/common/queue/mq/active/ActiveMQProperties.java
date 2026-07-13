package com.njydsz.pmis.common.queue.mq.active;

import com.njydsz.pmis.common.queue.config.QueueProperties;

import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * ActiveMQ 消息队列配置属性
 *
 * <p>封装 ActiveMQ 消息队列的连接和行为配置参数。
 * 支持 ActiveMQ Classic 和 ActiveMQ Artemis（推荐）两种模式。
 *
 * <p><b>配置示例：</b>
 * <pre>{@code
 * ydsz:
 *   queue:
 *     activemq:
 *       broker-url: tcp://localhost:61616
 *       username: admin
 *       password: admin
 *       queue-name: ydsz-activemq-queue
 *       concurrent-consumers: 5
 *       max-concurrent-consumers: 10
 * }</pre>
 *
 * @author Marvin Lee
 * @email limw1888@126.com
 * @version 3.5.0
 * @since 1.0.0
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class ActiveMQProperties extends QueueProperties {

    /**
     * ActiveMQ Broker 地址
     */
    private String brokerUrl = "tcp://localhost:61616";

    /**
     * 用户名
     *
     * <p>默认值为 "admin"，仅用于本地开发环境。生产环境务必通过配置覆盖。
     */
    private String username = "admin";

    /**
     * 密码
     *
     * <p>默认值为 "admin"，仅用于本地开发环境。生产环境务必通过配置覆盖，
     * 否则连接将使用弱密码，存在安全风险。
     */
    private String password = "admin";

    /**
     * 默认队列名称
     */
    private String queueName = "ydsz-activemq-queue";

    /**
     * 是否使用 Artemis（推荐）
     */
    private boolean artemis = true;

    /**
     * 消费者并发数
     */
    private int concurrentConsumers = 5;

    /**
     * 最大并发消费者数
     */
    private int maxConcurrentConsumers = 10;

    /**
     * 每次最大拉取消息数
     */
    private int maxMessagesPerTask = -1;

    /**
     * 是否持久化消息
     */
    private boolean persistent = true;

    /**
     * 解析获取 brokerUrl
     */
    public String resolvedBrokerUrl() {
        return isNotBlank(brokerUrl) ? brokerUrl : "tcp://localhost:61616";
    }

    /**
     * 解析获取 username
     */
    public String resolvedUsername() {
        return isNotBlank(username) ? username : "admin";
    }

    /**
     * 解析获取 password
     *
     * <p>若显式配置为空则直接返回空，由连接层报错，不再回退到默认弱密码。
     */
    public String resolvedPassword() {
        return password;
    }

    /**
     * 解析获取 queueName
     */
    public String resolvedQueueName() {
        return isNotBlank(queueName) ? queueName : "ydsz-activemq-queue";
    }

    private boolean isNotBlank(String str) {
        return str != null && !str.trim().isEmpty();
    }
}