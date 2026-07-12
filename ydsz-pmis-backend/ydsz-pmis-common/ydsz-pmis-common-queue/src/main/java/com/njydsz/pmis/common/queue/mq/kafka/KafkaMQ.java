package com.njydsz.pmis.common.queue.mq.kafka;

import com.njydsz.pmis.common.exception.custom.BusinessException;
import com.njydsz.pmis.common.queue.queue.AbstractMessageQueue;
import com.njydsz.pmis.common.queue.service.IMessagePublisher;
import com.njydsz.pmis.common.queue.service.IMessageSubscriber;
import lombok.extern.slf4j.Slf4j;

import java.util.Properties;
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

/**
 * Kafka 消息队列
 *
 * <p>Kafka 是分布式事件流平台，具有高吞吐量、持久化、消息回溯等特点。
 * 适合大规模消息传递、日志收集、流处理等场景。
 *
 * <p><b>技术特点：</b>
 * <ul>
 *   <li>高吞吐量：单节点可达百万级消息/秒</li>
 *   <li>持久化：消息存储在磁盘，支持消息回溯</li>
 *   <li>分布式：支持分区和副本，数据可靠</li>
 *   <li>消息回溯：可从任意偏移量位置开始消费</li>
 * </ul>
 *
 * <p><b>适用场景：</b>
 * <ul>
 *   <li>日志收集与传输</li>
 *   <li>实时流处理</li>
 *   <li>大数据管道</li>
 *   <li>微服务间异步通信</li>
 *   <li>活动追踪和监控</li>
 * </ul>
 *
 * <p><b>与 Redis Stream 对比：</b>
 * <table border="1">
 *   <tr><th>特性</th><th>Kafka</th><th>Redis Stream</th></tr>
 *   <tr><td>吞吐量</td><td>极高</td><td>高</td></tr>
 *   <tr><td>消息持久化</td><td>是</td><td>是</td></tr>
 *   <tr><td>消息回溯</td><td>是</td><td>是</td></tr>
 *   <tr><td>部署复杂度</td><td>高（需集群）</td><td>低</td></tr>
 *   <tr><td>延迟</td><td>毫秒级</td><td>亚毫秒级</td></tr>
 * </table>
 *
 * <p><b>使用示例：</b>
 * <pre>{@code
 * IMessageQueue queue = messageQueueProvider.createMessageQueue(QueueType.kafka);
 * IMessagePublisher publisher = queue.createPublisher("my-topic");
 * IMessageSubscriber subscriber = queue.createSubscriber("my-topic");
 *
 * publisher.publish("Hello Kafka");
 *
 * subscriber.subscribeAsync(message -> {
 *     log.info("Received: {}", message.getBody());
 * });
 * }</pre>
 *
 * @author Marvin Lee
 * @email limw1888@126.com
 * @version 3.5.0
 * @since 1.0.0
 */
@Slf4j
public class KafkaMQ extends AbstractMessageQueue {

    private final KafkaQueueProperties properties;
    private final ExecutorService consumerExecutor;

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
        return new KafkaMessagePublisher(properties, channel);
    }

    @Override
    public IMessageSubscriber createSubscriber(String channel) {
        checkNotClosed();
        if (channel == null || channel.isEmpty()) {
            throw BusinessException.builder().key("主题名称不能为空").build();
        }
        return new KafkaMessageSubscriber(properties, channel, consumerExecutor);
    }

    @Override
    protected void doClose() {
    }

    private void validateConnection() {
        try {
            Properties props = new Properties();
            props.put(AdminClientConfig.BOOTSTRAP_SERVERS_CONFIG, properties.resolvedBootstrapServers());
            props.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class.getName());
            props.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, StringSerializer.class.getName());
            try (AdminClient adminClient = AdminClient.create(props)) {
                ListTopicsResult result = adminClient.listTopics(new ListTopicsOptions().timeoutMs(5000));
                result.names().get(5, TimeUnit.SECONDS);
            }
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
