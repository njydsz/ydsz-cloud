package com.njydsz.common.seata.mq;

import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

import org.apache.rocketmq.client.producer.SendResult;
import org.apache.rocketmq.client.producer.SendStatus;
import org.apache.rocketmq.common.message.Message;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.messaging.MessagingException;

import com.njydsz.common.seata.api.XidPropagator;

/**
 * Seata MQ 消息发送模板
 *
 * <p>基于 RocketMQ 的事务消息，实现 XID 跨服务传播的透明化发送。
 * 通过消息头传递 XID，确保下游消费者能通过 {@link XidPropagator} 自动恢复事务上下文。
 *
 * <p><b>P2-1 新增</b>：解决 RocketMQ 事务消息与分布式事务 XID 传播的集成问题，
 * 使得数据库操作与 MQ 消息处于同一全局事务之中。
 *
 * <p>消息协议约定：
 * <ul>
 *   <li>{@code XID} - 全局事务 ID（P0-4 起可能包含签名）</li>
 *   <li>{@code BRANCH_ID} - 分支事务 ID（可选）</li>
 *   <li>{@code TX_TYPE} - 事务类型（LOCAL/TCC/SAGA）</li>
 *   <li>{@code TX_NAME} - 事务名称（便于日志追踪）</li>
 * </ul>
 *
 * <p>消费者端配合：
 * <pre>{@code
 * @RocketMQMessageListener(topic = "ORDER_TOPIC")
 * public class OrderConsumer implements RocketMQListener&lt;String&gt; {
 *     &#64;Override
 *     public void onMessage(String message) {
 *         // 1. 从 MDP / RocketMQ message 恢复 XID
 *         // 2. DefaultXidPropagator.restoreXid(message);
 *         // 3. 执行业务逻辑
 *     }
 * }
 * }</pre>
 *
 * <p>注意：此 Bean 仅在类路径存在 RocketMQ 时才可用，
 * 业务方应通过 {@code @Autowired(required=false)} 注入。
 *
 * @author ydsz-team
 * @since 1.3.0
 */
public class SeataMQSendTemplate {

    private static final Logger log = LoggerFactory.getLogger(SeataMQSendTemplate.class);

    /**
     * 消息头 - 全局事务 ID
     */
    public static final String HEADER_XID = "XID";

    /**
     * 消息头 - 分支事务 ID
     */
    public static final String HEADER_BRANCH_ID = "BRANCH_ID";

    /**
     * 消息头 - 事务类型
     */
    public static final String HEADER_TX_TYPE = "TX_TYPE";

    /**
     * 消息头 - 事务名称
     */
    public static final String HEADER_TX_NAME = "TX_NAME";

    /**
     * 消息头 - 链路追踪 ID
     */
    public static final String HEADER_TRACE_ID = "TRACE_ID";

    /**
     * RocketMQ 最大消息头长度（255 字节限制保护）
     */
    private static final int MAX_HEADER_LENGTH = 200;

    private final org.apache.rocketmq.client.producer.DefaultMQProducer producer;
    private final ObjectProvider<XidPropagator> xidPropagatorProvider;

    /**
     * 构造 MQ 发送模板
     *
     * @param producer                 RocketMQ 生产者实例（非空）
     * @param xidPropagatorProvider    XID 传播器（非空）
     */
    public SeataMQSendTemplate(
            org.apache.rocketmq.client.producer.DefaultMQProducer producer,
            ObjectProvider<XidPropagator> xidPropagatorProvider) {
        this.producer = producer;
        this.xidPropagatorProvider = xidPropagatorProvider;
    }

    /**
     * 发送普通消息，自动携带 XID 和 txName 到消息头
     *
     * @param topic   消息主题
     * @param tag     消息标签（可为 null）
     * @param keys    消息业务键（可为 null）
     * @param body    消息体（非空）
     * @return 发送结果
     * @throws MessagingException 发送失败
     */
    public SendResult send(String topic, String tag, String keys, byte[] body) {
        return send(topic, tag, keys, body, null);
    }

    /**
     * 发送普通消息，自动携带 XID 和自定义属性
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
            if (log.isDebugEnabled()) {
                log.debug("[SeataMQ] Sent message: topic={}, tag={}, keys={}, msgId={}, xid={}",
                        topic, tag, keys, result.getMsgId(), getCurrentXid());
            }
            return result;
        } catch (MessagingException e) {
            throw e;
        } catch (Exception e) {
            throw new MessagingException("Failed to send Seata-aware MQ message to topic: " + topic, e);
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
        return send topic, null, null, body.getBytes(StandardCharsets.UTF_8), null);
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
     * 构建 RocketMQ 消息对象，自动注入 XID 等信息
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
        XidPropagator propagator = xidPropagatorProvider.getIfAvailable();
        String currentXid = propagator != null ? propagator.getCurrentXid() : null;
        if (currentXid != null) {
            msg.putUserProperty(HEADER_XID, truncate(currentXid, MAX_HEADER_LENGTH));
        }

        // 注入事务类型和名称
        com.njydsz.common.seata.api.TransactionContext ctx =
                com.njydsz.common.seata.api.TransactionContext.current();
        if (ctx != null) {
            msg.putUserProperty(HEADER_TX_TYPE,
                    com.njydsz.common.seata.api.TransactionContext.getRequiredType().name());
            String txName = com.njydsz.common.seata.api.TransactionContext.getTransactionName();
            if (txName != null) {
                msg.putUserProperty(HEADER_TX_NAME, truncate(txName, MAX_HEADER_LENGTH));
            }
        }

        // 注入 traceId（P1-7）
        String traceId = org.slf4j.MDC.get("traceId");
        if (traceId != null) {
            msg.putUserProperty(HEADER_TRACE_ID, traceId);
        }

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
     * 获取当前线程 XID（用于日志）
     */
    private String getCurrentXid() {
        XidPropagator propagator = xidPropagatorProvider.getIfAvailable();
        return propagator != null ? propagator.getCurrentXid() : null;
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
     * 截断字符串到指定字节长度
     */
    private String truncate(String value, int maxBytes) {
        if (value == null) {
            return null;
        }
        return value.length() > maxBytes ? value.substring(0, maxBytes) : value;
    }
}
