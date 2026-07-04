package com.njydsz.pmis.system.producer;

import com.alibaba.fastjson2.JSON;
import com.njydsz.pmis.common.constant.PmisMessageTopics;
import com.njydsz.pmis.common.feign.MessageRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.rocketmq.client.producer.SendResult;
import org.apache.rocketmq.client.producer.SendStatus;
import org.apache.rocketmq.spring.core.RocketMQTemplate;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.UUID;

/**
 * RocketMQ 消息生产者封装（P0-D3）
 *
 * <p>统一封装 {@link RocketMQTemplate} 的发送入口，解决以下问题：
 * <ul>
 *   <li>原 {@code MessageConsumer} 监听 {@code pmis-message-topic} 但无生产者发送消息，处于空跑状态</li>
 *   <li>统一生成 {@code messageId}（UUID），保证消费端幂等键可用</li>
 *   <li>封装同步/异步发送，屏蔽 RocketMQTemplate API 细节</li>
 *   <li>统一 Topic 常量引用 {@link PmisMessageTopics}，避免字面量散落</li>
 * </ul>
 *
 * <p>使用方式：
 * <pre>{@code
 * @RequiredArgsConstructor
 * public class AlertDispatchServiceImpl {
 *     private final RocketMQMessageProducer producer;
 *
 *     public void submit(AlertDispatchDTO dto) {
 *         MessageRequest req = new MessageRequest();
 *         req.setChannel("EMAIL");
 *         req.setTemplateCode("BUDGET_ALERT");
 *         req.setReceiver("pmo@njydsz.com");
 *         producer.syncSend(req);
 *     }
 * }
 * }</pre>
 *
 * <p>条件装配：仅当 classpath 存在 RocketMQTemplate 且 {@code rocketmq.producer.enabled=true} 时生效。
 *
 * @author ydsz-pmis-team
 * @since 1.0.0
 */
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnClass(name = "org.apache.rocketmq.spring.core.RocketMQTemplate")
@ConditionalOnProperty(prefix = "rocketmq.producer", name = "enabled", havingValue = "true", matchIfMissing = true)
public class RocketMQMessageProducer {

    /** RocketMQ 模板（由 rocketmq-spring-boot-starter 自动装配） */
    private final RocketMQTemplate rocketMQTemplate;

    /**
     * 同步发送通知消息到 {@link PmisMessageTopics#TOPIC_MESSAGE}
     *
     * <p>同步发送会阻塞等待 Broker ACK，适用于对投递可靠性要求高的场景。
     * 发送失败会抛出 {@link RuntimeException}，由调用方决定是否重试或降级。
     *
     * <p>若 {@code request.messageId} 为空，自动生成 UUID 填充，保证消费端幂等键可用。
     *
     * @param request 消息请求
     * @return RocketMQ 消息 ID（发送成功时非空，发送失败时为 null）
     * @throws IllegalArgumentException request 为 null
     * @throws RuntimeException 发送失败
     */
    public String syncSend(MessageRequest request) {
        if (request == null) {
            throw new IllegalArgumentException("MessageRequest must not be null");
        }
        ensureMessageId(request);
        String payload = JSON.toJSONString(request);
        SendResult result;
        try {
            result = rocketMQTemplate.syncSend(PmisMessageTopics.TOPIC_MESSAGE, payload);
        } catch (Exception e) {
            log.error("[Producer] syncSend failed, messageId={} channel={} receiver={} err={}",
                    request.getMessageId(), request.getChannel(), request.getReceiver(), e.getMessage());
            throw new RuntimeException("RocketMQ syncSend failed: " + e.getMessage(), e);
        }
        if (result == null || result.getSendStatus() != SendStatus.SEND_OK) {
            String status = result == null ? "null" : result.getSendStatus().name();
            log.error("[Producer] syncSend NOT OK, status={} messageId={} channel={}",
                    status, request.getMessageId(), request.getChannel());
            throw new RuntimeException("RocketMQ syncSend status not OK: " + status);
        }
        log.info("[Producer] syncSend OK, msgId={} messageId={} channel={} receiver={}",
                result.getMsgId(), request.getMessageId(), request.getChannel(), request.getReceiver());
        return result.getMsgId();
    }

    /**
     * 异步发送通知消息（不阻塞，发送结果通过回调通知）
     *
     * <p>适用于对响应时间敏感、可容忍少量消息丢失的场景。
     * 内部使用 {@link RocketMQTemplate#asyncSend} 实现。
     *
     * @param request 消息请求
     */
    public void asyncSend(MessageRequest request) {
        if (request == null) {
            throw new IllegalArgumentException("MessageRequest must not be null");
        }
        ensureMessageId(request);
        String payload = JSON.toJSONString(request);
        try {
            rocketMQTemplate.asyncSend(PmisMessageTopics.TOPIC_MESSAGE, payload, new org.apache.rocketmq.client.producer.SendCallback() {
                @Override
                public void onSuccess(SendResult result) {
                    log.info("[Producer] asyncSend OK, msgId={} messageId={} channel={}",
                            result.getMsgId(), request.getMessageId(), request.getChannel());
                }

                @Override
                public void onException(Throwable e) {
                    log.error("[Producer] asyncSend failed, messageId={} channel={} err={}",
                            request.getMessageId(), request.getChannel(), e.getMessage());
                }
            });
        } catch (Exception e) {
            log.error("[Producer] asyncSend submit failed, messageId={} err={}",
                    request.getMessageId(), e.getMessage());
            throw new RuntimeException("RocketMQ asyncSend submit failed: " + e.getMessage(), e);
        }
    }

    /**
     * 确保消息请求携带 messageId（消费端幂等键依赖此字段）
     *
     * @param request 消息请求
     */
    private void ensureMessageId(MessageRequest request) {
        if (!StringUtils.hasText(request.getMessageId())) {
            request.setMessageId(UUID.randomUUID().toString().replace("-", ""));
        }
    }
}
