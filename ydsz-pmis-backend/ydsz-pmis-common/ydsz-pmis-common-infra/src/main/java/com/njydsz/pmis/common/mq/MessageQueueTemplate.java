package com.njydsz.pmis.common.mq;

/**
 * 消息队列抽象层
 *
 * <p>统一消息队列接口，支持 RocketMQ/Kafka/RabbitMQ/Redis Stream 等多种后端。
 *
 * @author ydsz-pmis-team
 * @since 1.5.0
 */
public interface MessageQueueTemplate {

    /**
     * 同步发送消息
     *
     * @param topic   主题
     * @param message 消息内容
     * @return 消息 ID
     */
    String send(String topic, Object message);

    /**
     * 异步发送消息
     *
     * @param topic   主题
     * @param message 消息内容
     * @param callback 发送回调
     */
    void sendAsync(String topic, Object message, SendCallback callback);

    /**
     * 发送延迟消息
     *
     * @param topic     主题
     * @param message   消息内容
     * @param delayLevel 延迟级别（1=1s, 2=5s, 3=10s, ...）
     * @return 消息 ID
     */
    String sendDelayed(String topic, Object message, int delayLevel);

    /**
     * 发送顺序消息
     *
     * @param topic   主题
     * @param message 消息内容
     * @param shardingKey 分片键（相同 key 的消息发送到同一队列）
     * @return 消息 ID
     */
    String sendOrdered(String topic, Object message, String shardingKey);

    /**
     * 发送回调
     */
    interface SendCallback {
        /**
         * 发送成功
         *
         * @param messageId 消息 ID
         */
        void onSuccess(String messageId);

        /**
         * 发送失败
         *
         * @param throwable 异常
         */
        void onFailure(Throwable throwable);
    }
}
