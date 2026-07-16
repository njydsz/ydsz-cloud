package com.njydsz.common.queue.mq.kafka;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;
import java.util.concurrent.locks.ReentrantLock;

import org.apache.kafka.clients.producer.Callback;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.clients.producer.RecordMetadata;
import org.apache.kafka.common.serialization.StringSerializer;

import com.njydsz.common.exception.custom.InfrastructureException;
import com.njydsz.common.queue.domain.QueueMessage;
import com.njydsz.common.queue.service.IMessagePublisher;

import lombok.extern.slf4j.Slf4j;

/**
 * Kafka 消息发布者
 *
 * <p>使用 Kafka Producer API 实现消息发布功能。
 * 支持同步和异步发送，自动处理消息序列化。
 *
 * <p><b>技术特点：</b>
 * <ul>
 *   <li>高吞吐量：适合大规模消息传递场景</li>
 *   <li>异步发送：支持异步发送提高性能</li>
 *   <li>分区支持：可根据消息键实现消息分区</li>
 *   <li>批量发送：支持批量发送减少网络开销</li>
 * </ul>
 *
 * <p><b>使用示例：</b>
 * <pre>{@code
 * KafkaMessagePublisher publisher = new KafkaMessagePublisher(properties, "my-topic");
 * publisher.publish("Hello Kafka");
 * publisher.publish(QueueMessage.of("Hello"));
 * publisher.close();
 * }</pre>
 *
 * @author ydsz-team
 * @since 1.0.0
 */
@Slf4j
public class KafkaMessagePublisher implements IMessagePublisher {

    private final KafkaProducer<String, String> producer;
    private final String topic;
    private volatile boolean closed = false;
    private final ReentrantLock closeLock = new ReentrantLock();

    public KafkaMessagePublisher(KafkaQueueProperties properties, String topic) {
        if (properties == null) {
            throw new IllegalArgumentException("Kafka 配置不能为空");
        }
        if (topic == null || topic.isEmpty()) {
            throw new IllegalArgumentException("主题名称不能为空");
        }
        this.topic = topic;
        this.producer = createProducer(properties);
        log.info("[Kafka] 发布者初始化完成，topic={}, bootstrapServers={}",
                topic, properties.resolvedBootstrapServers());
    }

    @Override
    public void publish(String message) {
        if (message == null || closed) {
            return;
        }
        try {
            QueueMessage queueMessage = QueueMessage.fromPayload(message);
            if (queueMessage == null) {
                queueMessage = QueueMessage.of(message);
            }
            publish(queueMessage);
        } catch (Exception e) {
            log.error("[Kafka] 消息发布失败，topic={}", topic, e);
            throw new InfrastructureException("Kafka 消息发布失败：" + e.getMessage(), e);
        }
    }

    @Override
    public void publish(QueueMessage message) {
        if (message == null || closed) {
            return;
        }
        try {
            String payload = QueueMessage.toPayload(message);
            String key = message.getTraceId();
            ProducerRecord<String, String> record = new ProducerRecord<>(topic, key, payload);
            producer.send(record, new Callback() {
                @Override
                public void onCompletion(RecordMetadata metadata, Exception exception) {
                    if (exception != null) {
                        log.error("[Kafka] 消息发送失败回调，topic={}, traceId={}, partition={}, error={}",
                                topic, key,
                                metadata != null ? metadata.partition() : -1,
                                exception.getMessage());
                    } else if (log.isDebugEnabled()) {
                        log.debug("[Kafka] 消息发送成功，topic={}, traceId={}, partition={}, offset={}",
                                topic, key, metadata.partition(), metadata.offset());
                    }
                }
            });
        } catch (Exception e) {
            log.error("[Kafka] 消息发布失败，topic={}, traceId={}", topic, message.getTraceId(), e);
            throw new InfrastructureException("Kafka 消息发布失败：" + e.getMessage(), e);
        }
    }

    @Override
    public void publishDelayed(QueueMessage message, long delayMillis) {
        log.warn("[Kafka] 延迟消息暂不支持，topic={}", topic);
        publish(message);
    }

    @Override
    public void publishSequential(QueueMessage message) {
        if (message == null || !message.isSequential()) {
            throw new IllegalArgumentException("顺序消息必须设置 messageGroupKey");
        }
        if (closed) {
            return;
        }
        try {
            String payload = QueueMessage.toPayload(message);
            String key = message.getMessageGroupKey();
            ProducerRecord<String, String> record = new ProducerRecord<>(topic, key, payload);
            producer.send(record, new Callback() {
                @Override
                public void onCompletion(RecordMetadata metadata, Exception exception) {
                    if (exception != null) {
                        log.error("[Kafka] 顺序消息发送失败回调，topic={}, groupKey={}, partition={}, error={}",
                                topic, key,
                                metadata != null ? metadata.partition() : -1,
                                exception.getMessage());
                    } else if (log.isDebugEnabled()) {
                        log.debug("[Kafka] 顺序消息发送成功，topic={}, groupKey={}, partition={}, offset={}",
                                topic, key, metadata.partition(), metadata.offset());
                    }
                }
            });
        } catch (Exception e) {
            log.error("[Kafka] 顺序消息发布失败，topic={}, groupKey={}", topic, message.getMessageGroupKey(), e);
            throw new InfrastructureException("Kafka 顺序消息发布失败：" + e.getMessage(), e);
        }
    }

    @Override
    public void publishBatch(List<QueueMessage> messages) {
        if (messages == null || messages.isEmpty()) {
            return;
        }
        if (closed) {
            return;
        }
        try {
            List<ProducerRecord<String, String>> records = new ArrayList<>(messages.size());
            for (QueueMessage message : messages) {
                if (message == null) {
                    continue;
                }
                String payload = QueueMessage.toPayload(message);
                String key = message.getTraceId();
                ProducerRecord<String, String> record = new ProducerRecord<>(topic, key, payload);
                records.add(record);
            }
            for (ProducerRecord<String, String> record : records) {
                producer.send(record, new Callback() {
                    @Override
                    public void onCompletion(RecordMetadata metadata, Exception exception) {
                        if (exception != null) {
                            log.error("[Kafka] 批量消息发送失败回调，topic={}, partition={}, error={}",
                                    topic,
                                    metadata != null ? metadata.partition() : -1,
                                    exception.getMessage());
                        } else if (log.isDebugEnabled()) {
                            log.debug("[Kafka] 批量消息发送成功，topic={}, partition={}, offset={}",
                                    topic, metadata.partition(), metadata.offset());
                        }
                    }
                });
            }
            producer.flush();
        } catch (Exception e) {
            log.error("[Kafka] 批量消息发布失败，topic={}", topic, e);
            throw new InfrastructureException("Kafka 批量消息发布失败：" + e.getMessage(), e);
        }
    }

    @Override
    public String getChannel() {
        return topic;
    }

    @Override
    public boolean isActive() {
        return !closed && producer != null;
    }

    @Override
    public void close() {
        if (closed) {
            return;
        }
        closeLock.lock();
        try {
            if (closed) {
                return;
            }
            closed = true;
            try {
                if (producer != null) {
                    producer.flush();
                    producer.close(Duration.ofSeconds(5));
                    log.info("[Kafka] 发布者已关闭，topic={}", topic);
                }
            } catch (Exception e) {
                log.warn("[Kafka] 关闭发布者时发生异常", e);
            }
        } finally {
            closeLock.unlock();
        }
    }

    private KafkaProducer<String, String> createProducer(KafkaQueueProperties properties) {
        Properties props = new Properties();
        props.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, properties.resolvedBootstrapServers());
        props.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class.getName());
        props.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, StringSerializer.class.getName());
        props.put(ProducerConfig.ACKS_CONFIG, "all");
        props.put(ProducerConfig.RETRIES_CONFIG, 3);
        props.put(ProducerConfig.BATCH_SIZE_CONFIG, 16384);
        props.put(ProducerConfig.LINGER_MS_CONFIG, 5);
        props.put(ProducerConfig.BUFFER_MEMORY_CONFIG, 33554432);
        props.put(ProducerConfig.MAX_BLOCK_MS_CONFIG, 5000);
        return new KafkaProducer<>(props);
    }
}