package com.njydsz.pmis.message.producer;

import com.njydsz.pmis.common.constant.PmisMessageTopics;
import com.njydsz.pmis.common.feign.MessageRequest;
import com.njydsz.pmis.common.util.JsonUtils;
import com.njydsz.pmis.common.util.SnowflakeIdGenerator;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.rocketmq.client.producer.SendCallback;
import org.apache.rocketmq.client.producer.SendResult;
import org.apache.rocketmq.client.producer.SendStatus;
import org.apache.rocketmq.spring.core.RocketMQTemplate;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

/**
 * RocketMQ 消息生产者封装。
 *
 * <p>统一封装 {@link RocketMQTemplate} 同步/异步发送,自动生成雪花 messageId 保证消费端幂等。
 * 条件装配:仅当 classpath 存在 RocketMQTemplate 且 {@code rocketmq.producer.group} 配置时生效。
 *
 * @author ydsz-pmis-team
 * @since 1.0.0
 */
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnClass(name = "org.apache.rocketmq.spring.core.RocketMQTemplate")
@ConditionalOnProperty(prefix = "rocketmq.producer", name = "group")
public class RocketMQMessageProducer {

    private final RocketMQTemplate rocketMQTemplate;

    /**
     * 同步发送消息到 {@link PmisMessageTopics#TOPIC_MESSAGE}。
     *
     * @param req 消息请求
     * @return RocketMQ 消息 ID
     */
    public String syncSend(MessageRequest req) {
        if (req == null) {
            throw new IllegalArgumentException("MessageRequest must not be null");
        }
        ensureMessageId(req);
        String payload = JsonUtils.toJson(req);
        SendResult result;
        try {
            result = rocketMQTemplate.syncSend(PmisMessageTopics.TOPIC_MESSAGE, payload);
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
    public void asyncSend(MessageRequest req) {
        if (req == null) {
            throw new IllegalArgumentException("MessageRequest must not be null");
        }
        ensureMessageId(req);
        String payload = JsonUtils.toJson(req);
        try {
            rocketMQTemplate.asyncSend(PmisMessageTopics.TOPIC_MESSAGE, payload, new SendCallback() {
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
            req.setMessageId(SnowflakeIdGenerator.nextIdStr());
        }
    }
}
