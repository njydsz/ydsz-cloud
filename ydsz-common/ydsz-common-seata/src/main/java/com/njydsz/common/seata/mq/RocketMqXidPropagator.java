package com.njydsz.common.seata.mq;

import java.nio.charset.StandardCharsets;
import java.util.Map;

import org.apache.rocketmq.client.producer.SendResult;
import org.apache.rocketmq.client.producer.SendStatus;
import org.apache.rocketmq.common.message.Message;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.messaging.MessagingException;

import com.njydsz.common.seata.api.XidPropagator;
import com.njydsz.common.seata.context.XidContextHolder;

/**
 * RocketMQ XID 传播器实现
 *
 * <p>实现 {@link MqXidPropagator} 接口，提供 RocketMQ 消息的 XID 透明传播。
 * 发送消息时自动注入 XID 到消息头，确保下游消费者能通过 XID 恢复事务上下文。
 *
 * @author ydsz-team
 * @since 1.4.0
 */
public class RocketMqXidPropagator implements MqXidPropagator {

    private static final Logger LOG = LoggerFactory.getLogger(RocketMqXidPropagator.class);

    /** 消息头最大长度（RocketMQ 限制保护） */
    private static final int MAX_HEADER_LENGTH = 200;

    private final org.apache.rocketmq.client.producer.DefaultMQProducer producer;
    private final ObjectProvider<XidPropagator> xidPropagatorProvider;

    /**
     * 构造 RocketMQ XID 传播器
     *
     * @param producer               RocketMQ 生产者实例
     * @param xidPropagatorProvider  XID 传播器提供者
     */
    public RocketMqXidPropagator(org.apache.rocketmq.client.producer.DefaultMQProducer producer,
                                  ObjectProvider<XidPropagator> xidPropagatorProvider) {
        this.producer = producer;
        this.xidPropagatorProvider = xidPropagatorProvider;
    }

    @Override
    public String getMqType() {
        return "rocketmq";
    }

    @Override
    public String getCurrentXid() {
        return XidContextHolder.getXid();
    }

    @Override
    public String getCurrentTxType() {
        XidContextHolder.XidContext ctx = XidContextHolder.current();
        return ctx != null && ctx.getType() != null ? ctx.getType().name() : null;
    }

    @Override
    public String getCurrentTxName() {
        XidContextHolder.XidContext ctx = XidContextHolder.current();
        return ctx != null ? ctx.getName() : null;
    }

    @Override
    public String getCurrentTraceId() {
        return org.slf4j.MDC.get("traceId");
    }

    /**
     * 发送消息，自动携带 XID 到消息头
     *
     * @param topic  消息主题
     * @param tag    消息标签（可为 null）
     * @param keys   消息业务键（可为 null）
     * @param body   消息体（非空）
     * @return 发送结果
     * @throws MessagingException 发送失败
     */
    public SendResult send(String topic, String tag, String keys, byte[] body) {
        return send(topic, tag, keys, body, null);
    }

    /**
     * 发送消息，自动携带 XID 和自定义属性
     *
     * @param topic      消息主题
     * @param tag        消息标签（可为 null）
     * @param keys       消息业务键（可为 null）
     * @param body       消息体（非空）
     * @param properties 额外属性（可为 null）
     * @return 发送结果
     * @throws MessagingException 发送失败
     */
    public SendResult send(String topic, String tag, String keys, byte[] body,
                           Map<String, String> properties) {
        Message msg = buildMessage(topic, tag, keys, body, properties);
        try {
            SendResult result = producer.send(msg);
            validateSendResult(result);
            if (LOG.isDebugEnabled()) {
                LOG.debug("[SeataMQ-RocketMQ] Sent message: topic={}, tag={}, keys={}, msgId={}, xid={}",
                        topic, tag, keys, result.getMsgId(), getCurrentXid());
            }
            return result;
        } catch (MessagingException e) {
            throw e;
        } catch (Exception e) {
            throw new MessagingException("Failed to send Seata-aware RocketMQ message to topic: " + topic, e);
        }
    }

    /**
     * 发送字符串消息
     *
     * @param topic 消息主题
     * @param body  消息体字符串（UTF-8）
     * @return 发送结果
     */
    public SendResult send(String topic, String body) {
        return send(topic, null, null, body.getBytes(StandardCharsets.UTF_8), null);
    }

    /**
     * 发送字符串消息（带 tag）
     *
     * @param topic 消息主题
     * @param tag   消息标签
     * @param body  消息体字符串
     * @return 发送结果
     */
    public SendResult send(String topic, String tag, String body) {
        return send(topic, tag, null, body.getBytes(StandardCharsets.UTF_8), null);
    }

    /**
     * 构建 RocketMQ 消息对象
     */
    private Message buildMessage(String topic, String tag, String keys, byte[] body,
                                 Map<String, String> extraProperties) {
        Message msg = new Message();
        msg.setTopic(topic);
        msg.setBody(body);

        if (tag != null && !tag.isEmpty()) {
            msg.setTags(tag);
        }
        if (keys != null && !keys.isEmpty()) {
            msg.setKeys(keys);
        }

        // 注入 XID
        String currentXid = getCurrentXid();
        if (currentXid != null) {
            msg.putUserProperty(HEADER_XID, truncate(currentXid, MAX_HEADER_LENGTH));
        }

        // 注入 XID 事务类型/名称
        Map<String, String> xidProps = buildXidProperties();
        xidProps.forEach((k, v) -> {
            if (v != null) {
                msg.putUserProperty(k, truncate(v, MAX_HEADER_LENGTH));
            }
        });

        // 合并额外属性
        if (extraProperties != null) {
            extraProperties.forEach((k, v) -> {
                if (v != null) {
                    msg.putUserProperty(k, v);
                }
            });
        }

        return msg;
    }

    /**
     * 校验发送结果
     */
    private void validateSendResult(SendResult result) {
        SendStatus status = result.getSendStatus();
        if (status != SendStatus.SEND_OK) {
            throw new MessagingException("MQ send status: " + status);
        }
    }

    /**
     * 截断字符串到指定长度
     */
    private String truncate(String value, int maxBytes) {
        if (value == null) {
            return null;
        }
        return value.length() > maxBytes ? value.substring(0, maxBytes) : value;
    }
}
