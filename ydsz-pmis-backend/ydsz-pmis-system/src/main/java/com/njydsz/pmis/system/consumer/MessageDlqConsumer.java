package com.njydsz.pmis.system.consumer;

import com.alibaba.fastjson2.JSON;
import com.njydsz.pmis.common.constant.PmisMessageTopics;
import com.njydsz.pmis.common.feign.MessageRequest;
import com.njydsz.pmis.system.entity.MessageLogDO;
import com.njydsz.pmis.system.mapper.MessageLogMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.rocketmq.common.message.MessageExt;
import org.apache.rocketmq.spring.annotation.RocketMQMessageListener;
import org.apache.rocketmq.spring.core.RocketMQListener;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * RocketMQ 死信队列消费者（P0-D3）
 *
 * <p>监听 {@link PmisMessageTopics#DLQ_MESSAGE}（{@code %DLQ%pmis-message-consumer}），
 * 消费 {@link MessageConsumer} 重试 {@code maxReconsumeTimes=3} 次后仍失败的消息。
 *
 * <h3>死信处理策略</h3>
 * <ul>
 *   <li><b>落库</b>：写入 {@code pmis_message_log} 表，{@code status=DEAD}，保留原始消息体与失败原因</li>
 *   <li><b>告警</b>：ERROR 级别日志输出（运维通过日志聚合监控 DLQ 堆积）</li>
 *   <li><b>不重投</b>：DLQ 消费者不抛出异常，避免 RocketMQ 对 DLQ Topic 再次重投形成循环</li>
 *   <li><b>转人工</b>：运维通过 {@code pmis_message_log} 表 {@code status=DEAD} 筛选死信，人工排查后重投</li>
 * </ul>
 *
 * <h3>注意</h3>
 * <ul>
 *   <li>DLQ 消费组 {@link PmisMessageTopics#GROUP_DLQ_MESSAGE} 独立于业务消费组，避免循环死信</li>
 *   <li>DLQ Topic 由 RocketMQ 自动创建（{@code autoCreateTopicEnable=true} 时），
 *       生产环境若关闭 autoCreateTopicEnable，需手动创建 {@code %DLQ%pmis-message-consumer} Topic</li>
 *   <li>消息体为原始 {@link MessageRequest} JSON，由 {@link MessageConsumer} 投递时生成</li>
 * </ul>
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

    /** 消息日志 Mapper（死信落库） */
    private final MessageLogMapper messageLogMapper;

    /**
     * 消费死信消息：落库 + 告警，不抛出异常（避免 DLQ 循环重投）。
     *
     * @param messageExt RocketMQ 原始消息扩展（含 msgId、reconsumeTimes、body 等）
     */
    @Override
    public void onMessage(MessageExt messageExt) {
        if (messageExt == null) {
            log.warn("[MessageDlqConsumer] 收到 null 消息, 跳过");
            return;
        }
        String msgId = messageExt.getMsgId();
        int reconsumeTimes = messageExt.getReconsumeTimes();
        String body = new String(messageExt.getBody() == null ? new byte[0] : messageExt.getBody());
        String originTopic = messageExt.getTopic();

        // 尝试解析为 MessageRequest，提取业务元信息
        MessageRequest request = null;
        try {
            request = JSON.parseObject(body, MessageRequest.class);
        } catch (Exception e) {
            log.error("[MessageDlqConsumer] 死信消息体解析失败, msgId={} err={}", msgId, e.getMessage());
        }

        // 落库到 pmis_message_log(status=DEAD)
        try {
            MessageLogDO logDO = new MessageLogDO();
            if (request != null) {
                logDO.setChannel(request.getChannel());
                logDO.setBizType(request.getBizType());
                logDO.setBizId(request.getBizId());
                logDO.setReceiver(request.getReceiver());
                logDO.setTemplateCode(request.getTemplateCode());
                logDO.setContent(request.getContent());
                logDO.setMsgId(request.getMessageId());
            } else {
                // 消息体无法解析，记录原始 body 前 500 字符
                logDO.setChannel("UNKNOWN");
                logDO.setReceiver("UNKNOWN");
                logDO.setContent(body.length() > 500 ? body.substring(0, 500) + "..." : body);
            }
            logDO.setStatus("DEAD");
            logDO.setErrorMessage(String.format(
                    "DLQ: msgId=%s, originTopic=%s, reconsumeTimes=%d",
                    msgId, originTopic, reconsumeTimes));
            logDO.setTopic(originTopic);
            logDO.setReconsumeTimes(reconsumeTimes);
            logDO.setTenantId(1L);
            messageLogMapper.insert(logDO);
        } catch (Exception e) {
            // 落库失败仅记录日志，不抛出（避免 DLQ 循环重投）
            log.error("[MessageDlqConsumer] 死信落库失败, msgId={} err={}", msgId, e.getMessage(), e);
        }

        // ERROR 告警（运维通过日志聚合监控 DLQ 堆积）
        log.error("[MessageDlqConsumer] 死信消息已落库, msgId={} originTopic={} reconsumeTimes={} bizType={} bizId={} receiver={}",
                msgId, originTopic, reconsumeTimes,
                request == null ? null : request.getBizType(),
                request == null ? null : request.getBizId(),
                request == null ? null : request.getReceiver());
    }
}
