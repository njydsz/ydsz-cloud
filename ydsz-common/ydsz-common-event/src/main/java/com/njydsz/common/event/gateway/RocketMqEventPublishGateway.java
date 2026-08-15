package com.njydsz.common.event.gateway;

import java.util.ArrayList;
import java.util.List;

import org.apache.rocketmq.client.producer.SendResult;
import org.apache.rocketmq.client.producer.SendStatus;
import org.apache.rocketmq.common.message.Message;
import org.apache.rocketmq.spring.core.RocketMQTemplate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.njydsz.common.event.model.OutboxMessage;

/**
 * RocketMQ 事件投递网关
 *
 * <p>基于 {@link RocketMQTemplate} 实现，将 Outbox 消息投递到 RocketMQ。
 * 仅当 classpath 存在 {@code RocketMQTemplate} 且容器中有 {@link RocketMQTemplate} Bean 时自动装配。
 *
 * <p>投递策略：
 * <ul>
 *   <li>Topic：固定为 {@code ydsz-outbox-events}（可通过配置覆盖）</li>
 *   <li>Tag：使用 {@code eventType} 作为 Tag，消费端可按事件类型订阅</li>
 *   <li>Body：使用 {@code payload} 作为消息体</li>
 *   <li>headers：作为 RocketMQ 用户自定义属性传递</li>
 * </ul>
 *
 * <p>批量投递使用 RocketMQ 原生批量发送能力（{@code DefaultMQProducer.send(Collection<Message>)}），
 * 单批总大小限制 4MB，超重自动分包。
 *
 * @author ydsz-team
 * @since 1.0.0
 * @since 1.6.0 publishBatch 使用 RocketMQ 原生批量 API，支持自动分包
 */
public class RocketMqEventPublishGateway implements EventPublishGateway {

    /** 日志实例 */
    private static final Logger log = LoggerFactory.getLogger(RocketMqEventPublishGateway.class);

    /** 默认 Topic 名称 */
    private static final String DEFAULT_TOPIC = "ydsz-outbox-events";

    /** RocketMQ 批量发送单批最大字节数（4MB 安全阈值） */
    private static final int BATCH_MAX_BYTES = 4 * 1024 * 1024;

    /** RocketMQ 模板 */
    private final RocketMQTemplate rocketMQTemplate;

    /** 目标 Topic */
    private final String topic;

    /**
     * 构造函数
     *
     * @param rocketMQTemplate RocketMQ 模板
     * @param topic            目标 Topic（null 或空时使用默认值 ydsz-outbox-events）
     */
    public RocketMqEventPublishGateway(RocketMQTemplate rocketMQTemplate, String topic) {
        this.rocketMQTemplate = rocketMQTemplate;
        this.topic = (topic != null && !topic.isBlank()) ? topic : DEFAULT_TOPIC;
    }

    /**
     * 投递单条消息到 RocketMQ
     *
     * @param message Outbox 消息
     * @return true 投递成功，false 投递失败
     */
    @Override
    public boolean publish(OutboxMessage message) {
        String destination = buildDestination(message);
        try {
            org.springframework.messaging.support.MessageBuilder<String> builder =
                    org.springframework.messaging.support.MessageBuilder.withPayload(message.getPayload());
            if (message.getHeaders() != null) {
                message.getHeaders().forEach(builder::setHeader);
            }
            if (message.getTenantId() != null) {
                builder.setHeader("tenantId", message.getTenantId());
            }
            if (message.getTraceId() != null) {
                builder.setHeader("traceId", message.getTraceId());
            }
            if (message.getDeduplicationId() != null) {
                builder.setHeader("deduplicationId", message.getDeduplicationId());
            }

            SendResult result = rocketMQTemplate.syncSend(destination, builder.build());
            boolean success = result != null && result.getSendStatus() == SendStatus.SEND_OK;
            if (success) {
                log.debug("RocketMQ publish OK: id={}, topic={}, tag={}, msgId={}",
                        message.getId(), topic, message.getEventType(),
                        result != null ? result.getMsgId() : "N/A");
            } else {
                String status = result != null ? result.getSendStatus().name() : "null";
                log.warn("RocketMQ publish failed: id={}, status={}", message.getId(), status);
            }
            return success;
        } catch (Exception e) {
            log.error("RocketMQ publish error: id={}, destination={}, error={}",
                    message.getId(), destination, e.getMessage());
            return false;
        }
    }

    /**
     * 批量投递消息到 RocketMQ
     *
     * <p>使用 RocketMQ 原生批量发送能力（{@code DefaultMQProducer.send(Collection<Message>)}）。
     * 当消息总大小超过 4MB 时自动分包，每包独立发送。
     *
     * @param messages Outbox 消息列表
     * @return 每条消息的投递结果（true=成功，false=失败），顺序与输入一致
     */
    @Override
    public List<Boolean> publishBatch(List<OutboxMessage> messages) {
        if (messages == null || messages.isEmpty()) {
            return List.of();
        }
        if (messages.size() == 1) {
            return List.of(publish(messages.get(0)));
        }

        List<Boolean> results = new ArrayList<>(messages.size());
        try {
            // 按大小分包
            List<List<OutboxMessage>> batches = splitBySize(messages);
            for (List<OutboxMessage> batch : batches) {
                results.addAll(publishBatchInternal(batch));
            }
        } catch (Exception e) {
            // 批量发送失败，降级为逐条投递
            log.warn("RocketMQ batch publish failed, falling back to single publish: err={}", e.getMessage());
            for (OutboxMessage message : messages) {
                results.add(publish(message));
            }
        }
        return results;
    }

    /**
     * 内部批量发送（单批不超过 4MB）
     *
     * @param batch 单批消息列表
     * @return 投递结果列表
     */
    private List<Boolean> publishBatchInternal(List<OutboxMessage> batch) throws Exception {
        List<Message> mqMessages = new ArrayList<>(batch.size());
        for (OutboxMessage msg : batch) {
            String destination = buildDestination(msg);
            Message mqMsg = new Message(topic, destination.split(":")[1], msg.getPayload());
            if (msg.getHeaders() != null) {
                msg.getHeaders().forEach((k, v) -> mqMsg.putUserProperty(k, v));
            }
            if (msg.getTenantId() != null) {
                mqMsg.putUserProperty("tenantId", msg.getTenantId());
            }
            if (msg.getTraceId() != null) {
                mqMsg.putUserProperty("traceId", msg.getTraceId());
            }
            if (msg.getDeduplicationId() != null) {
                mqMsg.putUserProperty("deduplicationId", msg.getDeduplicationId());
            }
            mqMsg.putUserProperty("outboxId", msg.getId());
            mqMessages.add(mqMsg);
        }

        SendResult result = rocketMQTemplate.getProducer().send(mqMessages);
        boolean batchSuccess = result != null && result.getSendStatus() == SendStatus.SEND_OK;

        List<Boolean> results = new ArrayList<>(batch.size());
        if (batchSuccess) {
            // 批量成功，所有消息标记为成功
            for (int i = 0; i < batch.size(); i++) {
                results.add(true);
            }
            log.debug("RocketMQ batch publish OK: count={}, topic={}", batch.size(), topic);
        } else {
            // 批量失败，降级为逐条投递
            log.warn("RocketMQ batch publish returned non-OK status: {}, falling back to single",
                    result != null ? result.getSendStatus() : "null");
            for (OutboxMessage msg : batch) {
                results.add(publish(msg));
            }
        }
        return results;
    }

    /**
     * 按消息大小分包（每包不超过 4MB）
     *
     * @param messages 消息列表
     * @return 分包后的消息批次列表
     */
    private List<List<OutboxMessage>> splitBySize(List<OutboxMessage> messages) {
        List<List<OutboxMessage>> batches = new ArrayList<>();
        List<OutboxMessage> currentBatch = new ArrayList<>();
        int currentBytes = 0;

        for (OutboxMessage msg : messages) {
            int msgBytes = estimateSize(msg);
            if (msgBytes > BATCH_MAX_BYTES) {
                // 单条消息超过 4MB，单独成批
                if (!currentBatch.isEmpty()) {
                    batches.add(currentBatch);
                    currentBatch = new ArrayList<>();
                    currentBytes = 0;
                }
                batches.add(List.of(msg));
                continue;
            }
            if (currentBytes + msgBytes > BATCH_MAX_BYTES && !currentBatch.isEmpty()) {
                batches.add(currentBatch);
                currentBatch = new ArrayList<>();
                currentBytes = 0;
            }
            currentBatch.add(msg);
            currentBytes += msgBytes;
        }
        if (!currentBatch.isEmpty()) {
            batches.add(currentBatch);
        }
        return batches;
    }

    /**
     * 估算消息字节数
     *
     * @param msg Outbox 消息
     * @return 估算的字节数
     */
    private int estimateSize(OutboxMessage msg) {
        int size = 0;
        if (msg.getPayload() != null) {
            size += msg.getPayload().getBytes().length;
        }
        if (msg.getEventType() != null) {
            size += msg.getEventType().getBytes().length;
        }
        if (msg.getHeaders() != null) {
            size += msg.getHeaders().size() * 64; // 估算每对 header 约 64 字节
        }
        return size;
    }

    /**
     * 构建 RocketMQ 目标地址（topic:tag）
     *
     * @param message Outbox 消息
     * @return 目标地址字符串
     */
    private String buildDestination(OutboxMessage message) {
        String tag = message.getEventType() != null ? message.getEventType() : "*";
        return topic + ":" + tag;
    }
}
