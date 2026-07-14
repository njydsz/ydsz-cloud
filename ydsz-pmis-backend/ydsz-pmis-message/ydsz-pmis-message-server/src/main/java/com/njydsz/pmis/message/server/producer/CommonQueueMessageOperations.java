package com.njydsz.pmis.message.server.producer;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import com.njydsz.pmis.common.constant.PmisMessageTopics;
import com.njydsz.pmis.common.feign.MessageRequest;
import com.njydsz.pmis.common.queue.domain.QueueMessage;
import com.njydsz.pmis.common.queue.enums.QueueType;
import com.njydsz.pmis.common.queue.queue.IMessageQueue;
import com.njydsz.pmis.common.queue.queue.IMessageQueueProvider;
import com.njydsz.pmis.common.queue.service.IMessagePublisher;
import com.njydsz.pmis.common.util.SnowflakeIdGenerator;
import com.njydsz.pmis.common.json.YdszJson;

import lombok.extern.slf4j.Slf4j;


/**
 * 基于 common-queue 抽象的消息队列操作实现。
 *
 * <p>当 {@code pmis.message.mq-type=common-queue} 且 classpath 存在
 * {@link IMessageQueueProvider} 时激活，通过 common-queue 的
 * {@link IMessagePublisher} 发送消息，底层可切换 RocketMQ / Kafka / RabbitMQ。
 *
 * <p>事务消息降级为同步发送（common-queue 抽象暂不支持事务消息）。
 *
 * @author ydsz-pmis-team
 * @since 1.3.0
 */
@Slf4j
@Component
@ConditionalOnClass(IMessageQueueProvider.class)
@ConditionalOnProperty(prefix = "pmis.message", name = "mq-type", havingValue = "common-queue")
@ConditionalOnMissingBean(MessageQueueOperations.class)
public class CommonQueueMessageOperations implements MessageQueueOperations {

    private final IMessagePublisher publisher;

    /**
     * 构造方法，通过 {@link IMessageQueueProvider} 创建 publisher。
     *
     * @param queueProviderProvider common-queue 队列提供者
     */
    public CommonQueueMessageOperations(ObjectProvider<IMessageQueueProvider> queueProviderProvider) {
        IMessageQueueProvider provider = queueProviderProvider.getIfAvailable();
        if (provider == null) {
            throw new IllegalStateException("IMessageQueueProvider 未配置，无法使用 common-queue 抽象");
        }
        IMessageQueue queue = provider.createMessageQueue(
                QueueType.ROCKET);
        this.publisher = queue.createPublisher(PmisMessageTopics.TOPIC_MESSAGE);
        log.info("[CommonQueueMQ] 使用 common-queue 抽象发送消息, topic={}", PmisMessageTopics.TOPIC_MESSAGE);
    }

    @Override
    public String syncSend(MessageRequest req) {
        if (req == null) {
            throw new IllegalArgumentException("MessageRequest must not be null");
        }
        ensureMessageId(req);
        String payload = YdszJson.toJson(req);
        QueueMessage message = QueueMessage.of(payload);
        message.addHeader("messageId", req.getMessageId());
        message.addHeader("channel", req.getChannel());
        message.addHeader("topic", PmisMessageTopics.TOPIC_MESSAGE);
        publisher.publish(message);
        log.info("[CommonQueueMQ] syncSend OK: messageId={} channel={}", req.getMessageId(), req.getChannel());
        return req.getMessageId();
    }

    @Override
    public void asyncSend(MessageRequest req) {
        // common-queue 的 publish 本身是异步的（取决于底层实现）
        syncSend(req);
    }

    @Override
    public String sendTransactionMessage(MessageRequest req) {
        log.warn("[CommonQueueMQ] 事务消息降级为同步发送（common-queue 抽象暂不支持事务消息）");
        return syncSend(req);
    }

    private void ensureMessageId(MessageRequest req) {
        if (!StringUtils.hasText(req.getMessageId())) {
            req.setMessageId(SnowflakeIdGenerator.nextIdStr());
        }
    }
}
