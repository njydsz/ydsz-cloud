package com.njydsz.pmis.common.queue.service.impl;

import com.njydsz.pmis.common.queue.domain.QueueMessage;
import com.njydsz.pmis.common.queue.service.IMessagePublisher;
import org.springframework.data.redis.connection.stream.ObjectRecord;
import org.springframework.data.redis.connection.stream.StreamRecords;
import org.springframework.data.redis.core.RedisTemplate;

import java.util.HashMap;
import java.util.Map;

/**
 * 基于 Redis Stream 的消息发布者
 *
 * <p>使用 Redis XADD 命令将消息写入 Stream，实现可靠的消息发布。
 * 通过 {@link RedisTemplate} 复用 ydsz-pmis-common-redis 的连接。
 *
 * @author Marvin Lee
 * @email limw1888@126.com
 * @version 3.5.0
 * @since 1.0.0
 */
public class RedisStreamPublisher implements IMessagePublisher {

    private static final String FIELD_PAYLOAD = "payload";
    private static final String FIELD_TRACE_ID = "traceId";
    private static final String FIELD_RETRY_COUNT = "retryCount";
    private static final String FIELD_GROUP_KEY = "groupKey";
    private static final String FIELD_SEQUENCE = "sequence";

    private final RedisTemplate<String, Object> redisTemplate;
    private final String channel;

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
            if (message.getSequenceNumber() != null) {
                fields.put(FIELD_SEQUENCE, String.valueOf(message.getSequenceNumber()));
            }
        }
        
        ObjectRecord<String, Map<String, String>> record = StreamRecords.newRecord()
                .ofObject(fields)
                .withStreamKey(channel);
        redisTemplate.opsForStream().add(record);
    }

    @Override
    public String getChannel() {
        return channel;
    }
}
