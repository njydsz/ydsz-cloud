package com.njydsz.message.server.producer;

import org.apache.rocketmq.client.producer.SendCallback;
import org.apache.rocketmq.client.producer.SendResult;
import org.apache.rocketmq.client.producer.SendStatus;
import org.apache.rocketmq.client.producer.TransactionSendResult;
import org.apache.rocketmq.spring.core.RocketMQTemplate;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import com.njydsz.common.core.constant.YdszMessageTopics;
import com.njydsz.common.feign.MessageRequest;
import com.njydsz.common.util.id.SnowflakeUtils;
import com.njydsz.common.json.YdszJson;
import com.njydsz.message.server.util.MessageCompressor;

import lombok.extern.slf4j.Slf4j;

/**
 * RocketMQ 消息生产者封装。
 *
 * <p>统一封装 {@link RocketMQTemplate} 同步/异步发送,自动生成雪花 messageId 保证消费端幂等。
 * 条件装配:仅当 classpath 存在 RocketMQTemplate 且 {@code rocketmq.producer.group} 配置时生效。
 *
 * @author ydsz-team
 * @since 1.0.0
 */
@Slf4j
@Component
@ConditionalOnClass(name = "org.apache.rocketmq.spring.core.RocketMQTemplate")
@ConditionalOnProperty(prefix = "rocketmq.producer", name = "group")
public class RocketMQMessageProducer implements MessageQueueOperations {

    private final RocketMQTemplate rocketMQTemplate;

    public RocketMQMessageProducer(RocketMQTemplate rocketMQTemplate) {
        this.rocketMQTemplate = rocketMQTemplate;
        // 预热 ASM 序列化器，避免首次请求时的类型推断开销
        YdszJson.warmup(MessageRequest.class);
    }

    /** P1-6: 优先级 → RocketMQ Tag 映射 */
    private static final String TAG_URGENT = "URGENT";
    private static final String TAG_HIGH = "HIGH";
    private static final String TAG_NORMAL = "NORMAL";
    private static final String TAG_LOW = "LOW";

    /**
     * 同步发送消息到 {@link YdszMessageTopics#TOPIC_MESSAGE}。
     *
     * @param req 消息请求
     * @return RocketMQ 消息 ID
     */
    @Override
    public String syncSend(MessageRequest req) {
        if (req == null) {
            throw new IllegalArgumentException("MessageRequest must not be null");
        }
        ensureMessageId(req);
        String payload = MessageCompressor.compressIfNeeded(YdszJson.toJson(req));
        String destination = buildDestination(req);
        SendResult result;
        try {
            result = rocketMQTemplate.syncSend(destination, payload);
        } catch (Exception e) {
            log.error("[Producer] syncSend 失败: messageId={} channel={} err={}",
                    req.getMessageId(), req.getChannel(), e.getMessage());
            throw new RuntimeException("RocketMQ syncSend failed: " + e.getMessage(), e);
        }
        if (result == null || result.getSendStatus() != SendStatus.SEND_OK) {
            String status = result == null ? "null" : result.getSendStatus().name();
            throw new RuntimeException("RocketMQ syncSend 状态异常: " + status);
        }
        log.info("[Producer] syncSend OK: msgId={} messageId={} channel={}",
                result.getMsgId(), req.getMessageId(), req.getChannel());
        return result.getMsgId();
    }

    /**
     * 异步发送消息(不阻塞,结果通过回调通知)。
     *
     * @param req 消息请求
     */
    @Override
    public void asyncSend(MessageRequest req) {
        if (req == null) {
            throw new IllegalArgumentException("MessageRequest must not be null");
        }
        ensureMessageId(req);
        String payload = MessageCompressor.compressIfNeeded(YdszJson.toJson(req));
        String destination = buildDestination(req);
        try {
            rocketMQTemplate.asyncSend(destination, payload, new SendCallback() {
                @Override
                public void onSuccess(SendResult result) {
                    log.info("[Producer] asyncSend OK: msgId={} messageId={}",
                            result.getMsgId(), req.getMessageId());
                }

                @Override
                public void onException(Throwable e) {
                    log.error("[Producer] asyncSend 失败: messageId={} err={}",
                            req.getMessageId(), e.getMessage());
                }
            });
        } catch (Exception e) {
            log.error("[Producer] asyncSend 提交失败: messageId={} err={}",
                    req.getMessageId(), e.getMessage());
            throw new RuntimeException("RocketMQ asyncSend 提交失败: " + e.getMessage(), e);
        }
    }

    private void ensureMessageId(MessageRequest req) {
        if (!StringUtils.hasText(req.getMessageId())) {
            req.setMessageId(SnowflakeUtils.nextIdStr());
        }
    }

    /**
     * P1-6: 构建带优先级 Tag 的 destination。
     *
     * <p>RocketMQ destination 格式：{@code topic:tag}，消费端可按 Tag 过滤优先级。
     * <ul>
     *   <li>URGENT → tag:URGENT（最高优先级，独立消费线程池）</li>
     *   <li>HIGH → tag:HIGH</li>
     *   <li>NORMAL → tag:NORMAL（默认）</li>
     *   <li>LOW → tag:LOW</li>
     * </ul>
     *
     * @param req 消息请求
     * @return destination 字符串（topic:tag）
     */
    private String buildDestination(MessageRequest req) {
        String priority = req.getPriority();
        String tag = resolvePriorityTag(priority);
        return YdszMessageTopics.TOPIC_MESSAGE + ":" + tag;
    }

    /**
     * 解析优先级 Tag。
     *
     * @param priority 优先级字符串
     * @return RocketMQ Tag
     */
    private String resolvePriorityTag(String priority) {
        if (priority == null || priority.isBlank()) {
            return TAG_NORMAL;
        }
        return switch (priority.trim().toUpperCase()) {
            case TAG_URGENT -> TAG_URGENT;
            case TAG_HIGH -> TAG_HIGH;
            case TAG_LOW -> TAG_LOW;
            default -> TAG_NORMAL;
        };
    }

    /**
     * P2-3: 发送事务消息（半消息）。
     *
     * <p>发送半消息后,RocketMQ 会回调 {@link com.njydsz.message.server.producer.MessageTransactionListener}
     * 执行本地事务（校验模板/通道）,根据结果 COMMIT / ROLLBACK。
     * 适用于业务侧需要确保通知请求仅在本地事务成功后才投递的场景。
     *
     * @param req 消息请求
     * @return RocketMQ 半消息 ID（后续 commit/rollback 由 TransactionListener 决定）
     */
    @Override
    public String sendTransactionMessage(MessageRequest req) {
        if (req == null) {
            throw new IllegalArgumentException("MessageRequest must not be null");
        }
        ensureMessageId(req);
        String payload = MessageCompressor.compressIfNeeded(YdszJson.toJson(req));
        try {
            TransactionSendResult result =
                    rocketMQTemplate.sendMessageInTransaction(
                            buildDestination(req),
                            MessageBuilder.withPayload(payload).build(),
                            req);
            log.info("[Producer] sendTransactionMessage: msgId={} messageId={} state={}",
                    result.getMsgId(), req.getMessageId(), result.getLocalTransactionState());
            return result.getMsgId();
        } catch (Exception e) {
            log.error("[Producer] sendTransactionMessage 失败: messageId={} err={}",
                    req.getMessageId(), e.getMessage());
            throw new RuntimeException("RocketMQ sendTransactionMessage failed: " + e.getMessage(), e);
        }
    }
}
