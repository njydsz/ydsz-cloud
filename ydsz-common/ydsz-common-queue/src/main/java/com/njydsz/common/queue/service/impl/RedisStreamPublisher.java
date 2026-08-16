package com.njydsz.common.queue.service.impl;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.springframework.data.redis.connection.stream.MapRecord;
import org.springframework.data.redis.connection.stream.ObjectRecord;
import org.springframework.data.redis.connection.stream.StreamRecords;
import org.springframework.data.redis.core.RedisOperations;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.SessionCallback;
import com.njydsz.common.queue.domain.QueueMessage;
import com.njydsz.common.queue.service.IMessagePublisher;
import com.njydsz.common.queue.service.MessagePublisherHelper;

/**
 * 基于 Redis Stream 的消息发布者。
 *
 * <p>使用 Redis <b>XADD</b> 命令将消息写入 Stream，实现可靠的消息发布。
 * 与 PubSub 的「即发即忘」不同，Stream 模式消息持久化在 Redis 中，
 * 消费者通过消费组读取并确认，保证消息不丢失。
 *
 * <h3>支持的消息属性</h3>
 * <ul>
 *   <li>{@code payload}：消息体（JSON 序列化）</li>
 *   <li>{@code traceId}：链路追踪 ID，贯穿全链路日志</li>
 *   <li>{@code retryCount}：重试计数，由消费端在重试时回写</li>
 *   <li>{@code groupKey} + {@code sequence}：顺序消息分组键和序号</li>
 * </ul>
 *
 * <h3>批量发布</h3>
 * <p>{@link #publishBatch(List)} 使用 Redis Pipeline 批量写入，
 * 减少网络往返开销，提升吞吐量。
 *
 * @author ydsz-team
 * @since 1.0.0
 * @see IMessagePublisher
 * @see RedisStreamSubscriber
 */
public class RedisStreamPublisher implements IMessagePublisher {

    /** Stream Entry 中存放消息体的字段名 */
    private static final String FIELD_PAYLOAD = "payload";
    /** Stream Entry 中存放链路追踪 ID 的字段名 */
    private static final String FIELD_TRACE_ID = "traceId";
    /** Stream Entry 中存放已重试次数的字段名 */
    private static final String FIELD_RETRY_COUNT = "retryCount";
    /** Stream Entry 中存放顺序消息分组键的字段名 */
    private static final String FIELD_GROUP_KEY = "groupKey";
    /** Stream Entry 中存放顺序消息序号的字段名 */
    private static final String FIELD_SEQUENCE = "sequence";

    /** 复用 ydsz-common-redis 的 Redis 连接 */
    private final RedisTemplate<String, Object> redisTemplate;
    /** 目标 Stream Key（频道名称） */
    private final String channel;

    /**
     * 构造 Redis Stream 发布者。
     *
     * @param redisTemplate Redis 连接模板，不可为空
     * @param channel       目标 Stream Key，不可为空
     * @throws IllegalArgumentException redisTemplate 或 channel 为空时抛出
     */
    public RedisStreamPublisher(RedisTemplate<String, Object> redisTemplate, String channel) {
        if (redisTemplate == null) {
            throw new IllegalArgumentException("RedisTemplate 不能为空");
        }
        if (channel == null || channel.isEmpty()) {
            throw new IllegalArgumentException("通道名称不能为空");
        }
        this.redisTemplate = redisTemplate;
        this.channel = channel;
    }

    /**
     * {@inheritDoc}
     *
     * <p>将原始字符串包装为 {@link QueueMessage} 后发布。如果传入的字符串
     * 可被反序列化为 QueueMessage 则保留原有属性，否则创建新消息。
     *
     * @param message 消息体字符串（JSON 或纯文本）
     */
    @Override
    public void publish(String message) {
        if (message == null) {
            return;
        }
        QueueMessage queueMessage = QueueMessage.fromPayload(message);
        if (queueMessage == null) {
            queueMessage = QueueMessage.of(message);
        }
        publish(queueMessage);
    }

    /**
     * {@inheritDoc}
     *
     * <p>将消息序列化为字段 Map 后通过 XADD 写入 Stream。包含 payload、traceId、
     * retryCount 以及顺序消息字段（如果消息标记为 sequential）。
     *
     * @param message 消息对象，为 null 时静默忽略
     */
    @Override
    public void publish(QueueMessage message) {
        if (message == null) {
            return;
        }
        Map<String, String> fields = new HashMap<>(8);
        fields.put(FIELD_PAYLOAD, QueueMessage.toPayload(message));
        fields.put(FIELD_TRACE_ID, message.getTraceId());
        fields.put(FIELD_RETRY_COUNT, String.valueOf(message.getRetryCount() != null ? message.getRetryCount() : 0));

        // 顺序消息支持：添加分组键和序号
        if (message.isSequential()) {
            fields.put(FIELD_GROUP_KEY, message.getMessageGroupKey());
            String sequence = message.getHeader("sequence");
            if (sequence != null) {
                fields.put(FIELD_SEQUENCE, sequence);
            }
        }

        ObjectRecord<String, Map<String, String>> record = StreamRecords.newRecord()
                .ofObject(fields)
                .withStreamKey(channel);
        redisTemplate.opsForStream().add(record);
    }

    /**
     * {@inheritDoc}
     *
     * <p>使用 Redis Pipeline 批量写入多条消息，减少网络往返开销。
     * 每条消息的字段组装逻辑与 {@link #publish(QueueMessage)} 一致。
     *
     * @param messages 消息列表，为空或 null 时静默忽略
     */
    @Override
    public void publishBatch(List<QueueMessage> messages) {
        if (messages == null || messages.isEmpty()) {
            return;
        }
        List<MapRecord<String, String, String>> records = new ArrayList<>(messages.size());
        for (QueueMessage message : messages) {
            if (message == null) {
                continue;
            }
            Map<String, String> fields = new HashMap<>(8);
            fields.put(FIELD_PAYLOAD, QueueMessage.toPayload(message));
            fields.put(FIELD_TRACE_ID, message.getTraceId());
            fields.put(FIELD_RETRY_COUNT,
                    String.valueOf(message.getRetryCount() != null ? message.getRetryCount() : 0));
            if (message.isSequential()) {
                fields.put(FIELD_GROUP_KEY, message.getMessageGroupKey());
                String sequence = message.getHeader("sequence");
                if (sequence != null) {
                    fields.put(FIELD_SEQUENCE, sequence);
                }
            }
            MapRecord<String, String, String> record = StreamRecords.mapBacked(fields).withStreamKey(channel);
            records.add(record);
        }
        redisTemplate.executePipelined(new SessionCallback<>() {
            @Override
            public <K, V> Object execute(RedisOperations<K, V> operations) {
                for (MapRecord<String, String, String> record : records) {
                    operations.opsForStream().add((MapRecord) record);
                }
                return null;
            }
        });
    }

    /**
     * {@inheritDoc}
     *
     * <p>Redis Stream 发布者无需显式关闭资源。
     */
    @Override
    public void close() {
        // Redis Stream 发布者无需显式关闭资源
    }
}
