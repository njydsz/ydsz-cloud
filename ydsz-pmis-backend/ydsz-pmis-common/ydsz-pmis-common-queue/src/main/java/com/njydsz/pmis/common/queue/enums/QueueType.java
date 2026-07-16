package com.njydsz.pmis.common.queue.enums;

/**
 * 消息队列类型枚举
 *
 * <p>定义系统支持的消息队列实现类型，包括 Redis 系列（List、Stream、PubSub）
 * 和主流中间件（ActiveMQ、RabbitMQ、RocketMQ、Kafka）。
 *
 * <p><b>类型说明：</b>
 * <ul>
 *   <li>{@link #LIST}：Redis List，轻量级队列，支持 FIFO</li>
 *   <li>{@link #STREAM}：Redis Stream，支持消费组、消息确认等高级特性</li>
 *   <li>{@link #PUBSUB}：Redis PubSub，支持发布/订阅模式</li>
 *   <li>{@link #ACTIVE}：ActiveMQ，面向消息的中间件</li>
 *   <li>{@link #RABBIT}：RabbitMQ，AMQP 协议实现</li>
 *   <li>{@link #ROCKET}：RocketMQ，阿里巴巴开源的分布式消息中间件</li>
 *   <li>{@link #KAFKA}：Kafka，高吞吐量分布式消息系统</li>
 * </ul>
 *
 * <p><b>选型建议：</b>
 * <ul>
 *   <li>简单队列场景：Redis List</li>
 *   <li>需要消息确认：Redis Stream 或 RabbitMQ</li>
 *   <li>高吞吐量场景：Kafka 或 RocketMQ</li>
 *   <li>事务消息：RocketMQ</li>
 * </ul>
 *
 * @author ydsz-pmis-team
 * @since 1.0.0
 */
public enum QueueType {

    /**
     * Redis List 队列
     * <p>轻量级队列，基于 Redis 的 LPUSH/BRPOP 命令实现 FIFO
     */
    LIST("list"),

    /**
     * Redis Stream 队列
     * <p>支持消费组、消息确认、持久化等高级特性，是 List 的升级版
     */
    STREAM("stream"),

    /**
     * Redis PubSub 发布/订阅
     * <p>支持多订阅者模式，但消息不持久化，适合实时通知场景
     */
    PUBSUB("pubsub"),

    /**
     * ActiveMQ
     * <p>Apache 旗下的面向消息的中间件，支持多种协议
     */
    ACTIVE("active"),

    /**
     * RabbitMQ
     * <p>实现了 AMQP 协议的消息队列，支持丰富的路由功能
     */
    RABBIT("rabbit"),

    /**
     * RocketMQ
     * <p>阿里巴巴开源的分布式消息中间件，支持事务消息和顺序消息
     */
    ROCKET("rocket"),

    /**
     * Kafka
     * <p>高吞吐量分布式消息系统，适合日志收集和实时流处理
     */
    KAFKA("kafka");

    private final String value;

    QueueType(String value) {
        this.value = value;
    }

    /**
     * 获取队列类型的字符串表示
     *
     * @return 队列类型值
     */
    public String getValue() {
        return value;
    }

    @Override
    public String toString() {
        return value;
    }

    /**
     * 根据字符串值反序列化为枚举
     *
     * @param value 队列类型字符串
     * @return 对应的枚举值
     */
    public static QueueType fromValue(String value) {
        if (value == null) {
            return null;
        }
        for (QueueType type : values()) {
            if (type.value.equalsIgnoreCase(value)) {
                return type;
            }
        }
        throw new IllegalArgumentException("未知的队列类型: " + value);
    }
}
