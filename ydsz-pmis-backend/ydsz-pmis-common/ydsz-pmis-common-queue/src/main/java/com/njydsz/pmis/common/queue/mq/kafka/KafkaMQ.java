package com.njydsz.pmis.common.queue.mq.kafka;

import java.util.List;
import java.util.Properties;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import org.apache.kafka.clients.admin.AdminClient;
import org.apache.kafka.clients.admin.AdminClientConfig;
import org.apache.kafka.clients.admin.ListTopicsOptions;
import org.apache.kafka.clients.admin.ListTopicsResult;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.common.serialization.StringSerializer;

import com.njydsz.pmis.common.exception.custom.BusinessException;
import com.njydsz.pmis.common.queue.queue.AbstractMessageQueue;
import com.njydsz.pmis.common.queue.service.IMessagePublisher;
import com.njydsz.pmis.common.queue.service.IMessageSubscriber;

import lombok.extern.slf4j.Slf4j;

/**
 * Kafka 消息队列
 *
 * <p>Kafka 是分布式事件流平台，具有高吞吐量、持久化、消息回溯等特点。
 * 适合大规模消息传递、日志收集、流处理等场景。
 *
 * <p><b>资源管理：</b>
 * <ul>
 *   <li>每个 Publisher 持有独立的 KafkaProducer</li>
 *   <li>每个 Subscriber 持有独立的 KafkaConsumer</li>
 *   <li>{@link #doClose()} 关闭所有已创建的 Producer 和 Consumer，防止资源泄漏</li>
 * </ul>
 *
 * @author ydsz-pmis-team
 * @since 1.0.0
 */
@Slf4j
public class KafkaMQ extends AbstractMessageQueue {

    private final KafkaQueueProperties properties;
    private final ExecutorService consumerExecutor;
    private final List<KafkaMessagePublisher> publishers = new CopyOnWriteArrayList<>();
    private final List<KafkaMessageSubscriber> subscribers = new CopyOnWriteArrayList<>();

    public KafkaMQ(KafkaQueueProperties properties, ExecutorService consumerExecutor) {
        super("Kafka");
        if (properties == null) {
            throw BusinessException.builder().key("Kafka 配置不能为空").build();
        }
        this.properties = properties;
        this.consumerExecutor = consumerExecutor;
        validateConnection();
        log.info("[KafkaMQ] 初始化成功，bootstrapServers={}", properties.resolvedBootstrapServers());
    }

    @Override
    public IMessagePublisher createPublisher(String channel) {
        checkNotClosed();
        if (channel == null || channel.isEmpty()) {
            throw BusinessException.builder().key("主题名称不能为空").build();
        }
        KafkaMessagePublisher publisher = new KafkaMessagePublisher(properties, channel);
        publishers.add(publisher);
        return publisher;
    }

    @Override
    public IMessageSubscriber createSubscriber(String channel) {
        checkNotClosed();
        if (channel == null || channel.isEmpty()) {
            throw BusinessException.builder().key("主题名称不能为空").build();
        }
        KafkaMessageSubscriber subscriber = new KafkaMessageSubscriber(properties, channel, consumerExecutor);
        subscribers.add(subscriber);
        return subscriber;
    }

    @Override
    protected void doClose() {
        for (KafkaMessagePublisher publisher : publishers) {
            try {
                publisher.close();
            } catch (Exception e) {
                log.warn("[KafkaMQ] 关闭 Publisher 时异常", e);
            }
        }
        publishers.clear();

        for (KafkaMessageSubscriber subscriber : subscribers) {
            try {
                subscriber.stop();
            } catch (Exception e) {
                log.warn("[KafkaMQ] 关闭 Subscriber 时异常", e);
            }
        }
        subscribers.clear();

        log.info("[KafkaMQ] 所有资源已释放");
    }

    private void validateConnection() {
        Properties props = new Properties();
        props.put(AdminClientConfig.BOOTSTRAP_SERVERS_CONFIG, properties.resolvedBootstrapServers());
        props.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class.getName());
        props.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, StringSerializer.class.getName());
        try (AdminClient adminClient = AdminClient.create(props)) {
            ListTopicsResult result = adminClient.listTopics(new ListTopicsOptions().timeoutMs(5000));
            result.names().get(5, TimeUnit.SECONDS);
            log.debug("[KafkaMQ] 连接验证成功");
        } catch (TimeoutException | ExecutionException e) {
            log.error("[KafkaMQ] 连接验证失败，bootstrapServers={}", properties.resolvedBootstrapServers(), e);
            throw BusinessException.builder().key("Kafka 连接失败，请检查配置：" + e.getMessage()).build();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw BusinessException.builder().key("Kafka 连接验证被中断").build();
        }
    }
}
