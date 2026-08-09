package com.njydsz.common.event.gateway;

import java.util.ArrayList;
import java.util.List;

import org.apache.rocketmq.client.producer.SendResult;
import org.apache.rocketmq.client.producer.SendStatus;
import org.apache.rocketmq.spring.core.RocketMQTemplate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.messaging.support.MessageBuilder;

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
 * <p>批量投递利用 RocketMQTemplate 的批量发送能力。
 *
 * @author ydsz-team
 * @since 1.0.0
 */
public class RocketMqEventPublishGateway implements EventPublishGateway {

    /** 日志实例 */
    private static final Logger log = LoggerFactory.getLogger(RocketMqEventPublishGateway.class);

    /** 默认 Topic 名称 */
    private static final String DEFAULT_TOPIC = "ydsz-outbox-events";

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
            MessageBuilder<String> builder = MessageBuilder.withPayload(message.getPayload());
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
     * <p>当前实现逐条投递，后续可优化为使用 RocketMQ 批量发送能力。
     *
     * @param messages Outbox 消息列表
     * @return 每条消息的投递结果，顺序与输入一致
     */
    @Override
    public List<Boolean> publishBatch(List<OutboxMessage> messages) {
        List<Boolean> results = new ArrayList<>(messages.size());
        for (OutboxMessage message : messages) {
            Response.add(publish(message));
        }
        return results;
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
