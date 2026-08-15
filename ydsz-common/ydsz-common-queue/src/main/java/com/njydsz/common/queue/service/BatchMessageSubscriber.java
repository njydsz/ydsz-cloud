package com.njydsz.common.queue.service;

import java.util.List;

import com.njydsz.common.queue.domain.QueueMessage;

/**
 * 批量消息订阅者接口
 *
 * <p>提供批量消费消息的能力，与 {@link IMessagePublisher#publishBatch(List)} 对称，
 * 实现消费端批量获取多条消息以提升吞吐量。
 *
 * <p><b>设计目的：</b>
 * <ul>
 *   <li>批量消费减少网络往返开销，提升消费端吞吐量</li>
 *   <li>与批量发布接口对称，形成完整的批量操作契约</li>
 *   <li>实现对底层批量 API 的灵活利用（如 Redis MGET、Kafka poll）</li>
 * </ul>
 *
 * <p><b>使用示例：</b>
 * <pre>{@code
 * BatchMessageSubscriber batchSubscriber = ...;
 * List<QueueMessage> messages = batchSubscriber.subscribeBatch(10);
 * for (QueueMessage msg : messages) {
 *     processMessage(msg);
 * }
 * }</pre>
 *
 * @author ydsz-team
 * @since 1.0.0
 */
public interface BatchMessageSubscriber extends IMessageSubscriber {

    /**
     * 批量同步消费消息
     *
     * <p>此方法为阻塞调用，将等待直到有消息可用，返回最多 maxBatchSize 条消息。
     * 如果当前可用消息数少于 maxBatchSize，返回实际可用的消息列表（可能为空）。
     *
     * @param maxBatchSize 单次批量获取的最大消息数
     * @return 消费到的消息列表，无消息时返回空列表
     */
    List<QueueMessage> subscribeBatch(int maxBatchSize);

    /**
     * 批量异步订阅消息
     *
     * <p>启动后台线程持续监听消息，以批量方式获取消息并回调 handler。
     * 每次回调传入一批消息。
     *
     * @param handler       批量消息处理回调，不能为 null
     * @param maxBatchSize  单次批量获取的最大消息数
     * @return 消费者 ID，可用于停止消费
     */
    String subscribeBatchAsync(BatchMessageHandler handler, int maxBatchSize);

    /**
     * 批量确认消息
     *
     * <p>确认一批消息已被成功消费，底层 MQ 可能需要调用 ACK 操作。
     *
     * @param messageIds 待确认的消息 ID 列表
     * @return 成功确认的消息数量
     */
    int acknowledgeBatch(List<String> messageIds);
}
