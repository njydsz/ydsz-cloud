package com.njydsz.pmis.message.consumer;

import com.njydsz.pmis.common.constant.PmisMessageTopics;
import com.njydsz.pmis.common.feign.MessageRequest;
import com.njydsz.pmis.common.security.TenantContext;
import com.njydsz.pmis.common.util.JsonUtils;
import com.njydsz.pmis.message.entity.core.MsgLogDO;
import com.njydsz.pmis.message.enums.core.MessageStatusEnum;
import com.njydsz.pmis.message.mapper.core.MsgLogMapper;
import com.njydsz.pmis.message.metric.MessageMetrics;
import com.njydsz.pmis.message.tracing.MessageTraceContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.rocketmq.common.message.MessageExt;
import org.apache.rocketmq.spring.annotation.RocketMQMessageListener;
import org.apache.rocketmq.spring.core.RocketMQListener;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * RocketMQ 死信队列消费者。
 *
 * <p>监听 {@link PmisMessageTopics#DLQ_MESSAGE},将重试耗尽的消息落库 status=DEAD,
 * 不抛出异常避免 DLQ 循环重投。
 *
 * @author ydsz-pmis-team
 * @since 1.0.0
 */
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnClass(name = "org.apache.rocketmq.spring.annotation.RocketMQMessageListener")
@ConditionalOnProperty(prefix = "rocketmq.consumer", name = "enabled", havingValue = "true", matchIfMissing = false)
@RocketMQMessageListener(
        topic = PmisMessageTopics.DLQ_MESSAGE,
        consumerGroup = PmisMessageTopics.GROUP_DLQ_MESSAGE,
        selectorExpression = "*",
        maxReconsumeTimes = 1
)
public class MessageDlqConsumer implements RocketMQListener<MessageExt> {

    private final MsgLogMapper msgLogMapper;
    private final MessageMetrics messageMetrics;

    @Override
    public void onMessage(MessageExt messageExt) {
        if (messageExt == null) {
            log.warn("[MessageDlqConsumer] 收到 null 消息,跳过");
            return;
        }
        String msgId = messageExt.getMsgId();
        int reconsumeTimes = messageExt.getReconsumeTimes();
        String body = new String(messageExt.getBody() == null ? new byte[0] : messageExt.getBody());
        String originTopic = messageExt.getTopic();

        // P1-3: 死信处理进入追踪上下文（无原始 traceId 时自动生成）
        try (MessageTraceContext ctx = MessageTraceContext.enter(null)) {
            MessageRequest request = null;
            try {
                request = JsonUtils.parseObject(body, MessageRequest.class);
            } catch (Exception e) {
                log.error("[MessageDlqConsumer] 死信消息体解析失败: msgId={} err={}", msgId, e.getMessage());
            }

            try {
                MsgLogDO logDO = new MsgLogDO();
                if (request != null) {
                    logDO.setChannel(request.getChannel());
                    logDO.setBizType(request.getBizType());
                    logDO.setBizId(request.getBizId());
                    logDO.setReceiver(request.getReceiver());
                    logDO.setTemplateCode(request.getTemplateCode());
                    logDO.setContent(request.getContent());
                    logDO.setMsgId(request.getMessageId());
                } else {
                    logDO.setChannel("UNKNOWN");
                    logDO.setReceiver("UNKNOWN");
                    logDO.setContent(body.length() > 500 ? body.substring(0, 500) + "..." : body);
                }
                logDO.setStatus(MessageStatusEnum.DEAD.name());
                logDO.setErrorMessage(String.format(
                        "DLQ: msgId=%s, originTopic=%s, reconsumeTimes=%d", msgId, originTopic, reconsumeTimes));
                logDO.setTopic(originTopic);
                logDO.setReconsumeTimes(reconsumeTimes);
                logDO.setTenantId(TenantContext.getTenantId());
                msgLogMapper.insert(logDO);
                messageMetrics.recordDead(logDO.getChannel());
            } catch (Exception e) {
                log.error("[MessageDlqConsumer] 死信落库失败: msgId={} err={}", msgId, e.getMessage(), e);
            }

            log.error("[MessageDlqConsumer] 死信已落库: msgId={} originTopic={} reconsumeTimes={} bizType={} bizId={} receiver={}",
                    msgId, originTopic, reconsumeTimes,
                    request == null ? null : request.getBizType(),
                    request == null ? null : request.getBizId(),
                    request == null ? null : request.getReceiver());
        }
    }
}
