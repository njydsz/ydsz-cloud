package com.remisoft.common.queue.mq.rabbit;

import com.remisoft.common.queue.config.QueueProperties;

import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * RabbitMQ 消息队列配置属性
 *
 * <p>封装 RabbitMQ 消息队列的连接和行为配置参数。
 *
 * <p><b>配置示例：</b>
 * <pre>{@code
 * remi:
 *   queue:
 *     rabbitmq:
 *       host: localhost
 *       port: 5672
 *       username: guest
 *       password: guest
 *       virtual-host: /
 *       queue-name: remi-queue
 *       exchange-name: remi-exchange
 *       routing-key: remi.routing.key
 * }</pre>
 *
 * @author remi-team
 * @since 1.0.0
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class RabbitMQProperties extends QueueProperties {

    /**
     * RabbitMQ 服务器地址
     */
    private String host = "localhost";

    /**
     * RabbitMQ 服务器端口
     */
    private int rabbitPort = 5672;

    /**
     * 用户名
     *
     * <p>默认值为 "guest"，仅用于本地开发环境。生产环境务必通过配置覆盖。
     */
    private String username = "guest";

    /**
     * 密码
     *
     * <p>默认值为 "guest"，仅用于本地开发环境。生产环境务必通过配置覆盖，
     * 否则连接将使用弱密码，存在安全风险。
     */
    private String password = "guest";

    /**
     * 虚拟主机
     */
    private String virtualHost = "/";

    /**
     * 默认队列名称
     */
    private String queueName = "remi-rabbitmq-queue";

    /**
     * 交换机名称
     */
    private String exchangeName = "remi-exchange";

    /**
     * 路由键
     */
    private String routingKey = "remi.routing.key";

    /**
     * 是否启用消息确认（ACK）
     */
    private boolean acknowledgeMode = true;

    /**
     * 每次最大拉取消息数
     */
    private int prefetchCount = 10;

    /**
     * 是否持久化队列
     */
    private boolean durable = true;

    /**
     * 消费者并发数
     */
    private int concurrentConsumers = 5;

    /**
     * 最大并发消费者数
     */
    private int maxConcurrentConsumers = 10;

    /**
     * 解析获取 host
     */
    public String resolvedHost() {
        return isNotBlank(host) ? host : "localhost";
    }

    /**
     * 解析获取 port
     */
    public int resolvedPort() {
        return rabbitPort > 0 ? rabbitPort : 5672;
    }

    /**
     * 解析获取 username
     */
    public String resolvedUsername() {
        return isNotBlank(username) ? username : "guest";
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
     * 解析获取 virtualHost
     */
    public String resolvedVirtualHost() {
        return isNotBlank(virtualHost) ? virtualHost : "/";
    }

    /**
     * 解析获取 queueName
     */
    public String resolvedQueueName() {
        return isNotBlank(queueName) ? queueName : "remi-rabbitmq-queue";
    }

    private boolean isNotBlank(String str) {
        return str != null && !str.trim().isEmpty();
    }
}