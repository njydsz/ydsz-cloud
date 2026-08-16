package com.njydsz.common.queue.enums;

/**
 * 消息队列类型枚举。
 *
 * <p>定义系统支持的消息队列实现类型，包括 Redis 系列（List、Stream、PubSub）
 * 和主流中间件（RabbitMQ、RocketMQ、Kafka）。
 *
 * <p><b>推荐选型：</b>
 * <ul>
 *   <li>{@link #STREAM}（推荐）：支持消费组、消息确认、持久化等高级特性</li>
 *   <li>{@link #KAFKA}（推荐）：高吞吐量分布式消息系统，适合日志收集和实时流处理</li>
 *   <li>{@link #ROCKET}：支持事务消息和顺序消息</li>
 * </ul>
 *
 * <p><b>已废弃（将在后续版本移除）：</b>
 * <ul>
 *   <li>{@link #LIST}：建议使用 {@link #STREAM} 替代</li>
 *   <li>{@link #PUBSUB}：建议使用 {@link #STREAM} 替代</li>
 *   <li>{@link #RABBIT}：建议使用 {@link #KAFKA} 或 {@link #ROCKET} 替代</li>
 * </ul>
 *
 * @author ydsz-team
 * @since 1.0.0
 */
public enum QueueType {

    /**
     * Redis List 队列。
     *
     * <p>轻量级队列，基于 Redis 的 LPUSH/BRPOP 命令实现 FIFO。
     * 不具备消息 ACK/重试/死信能力。
     *
     * @deprecated 建议使用 {@link #STREAM} 替代
     */
    @Deprecated
    LIST("list"),

    /**
     * Redis Stream 队列（推荐）。
     *
     * <p>支持消费组、消息确认、持久化等高级特性。
     */
    STREAM("stream"),

    /**
     * Redis PubSub 发布/订阅。
     *
     * <p>支持多订阅者模式，但消息不持久化，订阅者离线时消息丢失。
     *
     * @deprecated 建议使用 {@link #STREAM} 替代
     */
    @Deprecated
    PUBSUB("pubsub"),

    /**
     * RabbitMQ。
     *
     * <p>实现了 AMQP 协议的消息队列，支持丰富的路由功能。
     *
     * @deprecated 建议使用 {@link #KAFKA} 或 {@link #ROCKET} 替代
     */
    @Deprecated
    RABBIT("rabbit"),

    /**
     * RocketMQ。
     *
     * <p>支持事务消息和顺序消息。
     */
    ROCKET("rocket"),

    /**
     * Kafka（推荐）。
     *
     * <p>高吞吐量分布式消息系统，适合日志收集和实时流处理。
     */
    KAFKA("kafka");

    private final String value;

    QueueType(String value) {
        this.value = value;
    }

    /**
     * 获取队列类型的字符串表示。
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
     * 根据字符串值反序列化为枚举。
     *
     * @param value 队列类型字符串
     * @return 对应的枚举值，value 为 null 时返回 null
     * @throws IllegalArgumentException 如果值不匹配任何枚举
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
