package com.njydsz.common.queue.mq.kafka;

import java.time.Duration;
import java.util.ArrayDeque;
import java.util.Collections;
import java.util.Properties;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.clients.consumer.OffsetAndMetadata;
import org.apache.kafka.common.TopicPartition;
import org.apache.kafka.common.serialization.StringDeserializer;

import com.njydsz.common.queue.domain.QueueMessage;
import com.njydsz.common.queue.rate.ConsumerRateLimiter;
import com.njydsz.common.queue.recovery.ConsumerThreadGuard;
import com.njydsz.common.queue.service.IMessageHandler;
import com.njydsz.common.queue.service.IMessageSubscriber;

import lombok.extern.slf4j.Slf4j;

/**
 * Kafka 消息订阅者
 *
 * <p>使用 Kafka Consumer API 实现消息消费功能。
 * 支持同步拉取和异步持续消费两种模式。
 *
 * <p><b>技术特点：</b>
 * <ul>
 *   <li>消费组支持：多个消费者协同消费实现负载均衡</li>
 *   <li>偏移量管理：支持手动提交偏移量实现精确控制</li>
 *   <li>消息回溯：从earliest或latest位置开始消费</li>
 *   <li>分区策略：同一分区消息有序</li>
 * </ul>
 *
 * <p><b>消费模式：</b>
 * <ul>
 *   <li>同步拉取：调用 subscribe() 阻塞等待单条消息</li>
 *   <li>异步持续消费：调用 subscribeAsync(handler) 后台线程持续消费</li>
 * </ul>
 *
 * <p><b>偏移量提交：</b>
 * <ul>
 *   <li>enableAutoCommit=false：手动提交偏移量</li>
 *   <li>消息处理成功后自动提交</li>
 *   <li>保证至少一次消费语义</li>
 * </ul>
 *
 * @author ydsz-team
 * @since 1.0.0
 */
@Slf4j
public class KafkaMessageSubscriber implements IMessageSubscriber {

    private final KafkaConsumer<String, String> consumer;
    private final String topic;
    private final String groupId;

    private final AtomicBoolean running;
    private final AtomicLong consumedCount;
    private final AtomicReference<Throwable> lastError;

    private final ArrayDeque<ConsumerRecord<String, String>> recordBuffer = new ArrayDeque<>();
    private final ConsumerRateLimiter rateLimiter;
    private final ExecutorService consumerExecutor;

    private volatile Thread consumerThread;
    private volatile ConsumerThreadGuard threadGuard;

    public KafkaMessageSubscriber(KafkaQueueProperties properties, String topic, ExecutorService consumerExecutor) {
        if (properties == null) {
            throw new IllegalArgumentException("Kafka 配置不能为空");
        }
        if (topic == null || topic.isEmpty()) {
            throw new IllegalArgumentException("主题名称不能为空");
        }
        this.topic = topic;
        this.groupId = properties.resolvedGroupId();
        this.consumer = createConsumer(properties);
        this.running = new AtomicBoolean(false);
        this.consumedCount = new AtomicLong(0);
        this.lastError = new AtomicReference<>();
        this.rateLimiter = new ConsumerRateLimiter(properties.getConsumerRateLimitPerSecond());
        this.consumerExecutor = consumerExecutor;
        log.info("[Kafka] 订阅者初始化完成，topic={}, groupId={}", topic, groupId);
    }

    @Override
    public String subscribe() {
        try {
            if (recordBuffer.isEmpty()) {
                ConsumerRecords<String, String> records = consumer.poll(Duration.ofMillis(1000));
                if (records != null) {
                    for (ConsumerRecord<String, String> record : records) {
                        if (record != null && record.value() != null) {
                            recordBuffer.addLast(record);
                        }
                    }
                }
            }
            if (recordBuffer.isEmpty()) {
                return null;
            }
            ConsumerRecord<String, String> record = recordBuffer.removeFirst();
            consumedCount.incrementAndGet();
            consumer.commitSync(Collections.singletonMap(
                    new TopicPartition(record.topic(), record.partition()),
                    new OffsetAndMetadata(record.offset() + 1)
            ));
            return record.value();
        } catch (Exception e) {
            lastError.set(e);
            log.error("[Kafka] 拉取消息异常，topic={}, groupId={}", topic, groupId, e);
            return null;
        }
    }

    @Override
    public String subscribeAsync(IMessageHandler handler) {
        if (!running.compareAndSet(false, true)) {
            log.warn("[Kafka] 订阅者已在运行中，topic={}, groupId={}", topic, groupId);
            return groupId;
        }
        if (consumerExecutor != null) {
            threadGuard = new ConsumerThreadGuard("kafka-" + groupId, 10, consumerExecutor);
            threadGuard.start(() -> consumeLoop(handler));
            log.info("[Kafka] 异步消费已启动（线程池托管），topic={}, groupId={}", topic, groupId);
        } else {
            consumerThread = new Thread(() -> consumeLoop(handler), "ydsz-queue-kafka-" + groupId);
            consumerThread.setDaemon(true);
            consumerThread.start();
            log.warn("[Kafka] 异步消费已启动（裸线程，不推荐），topic={}, groupId={}", topic, groupId);
        }
        return groupId;
    }

    @Override
    public void stop() {
        if (!running.compareAndSet(true, false)) {
            return;
        }
        log.info("[Kafka] 收到停止信号，topic={}, groupId={}", topic, groupId);
        if (threadGuard != null) {
            threadGuard.stop();
        }
        if (consumerThread != null) {
            consumerThread.interrupt();
        }
    }

    @Override
    public boolean isRunning() {
        return running.get();
    }

    @Override
    public Object getChannel() {
        return topic;
    }

    @Override
    public String getConsumerId() {
        return groupId;
    }

    @Override
    public int getConsumedCount() {
        return (int) consumedCount.get();
    }

    @Override
    public Throwable getLastError() {
        return lastError.get();
    }

    private void consumeLoop(IMessageHandler handler) {
        try {
            consumer.subscribe(Collections.singletonList(topic));
            log.debug("[Kafka] 已订阅主题，topic={}", topic);

            while (running.get()) {
                try {
                    ConsumerRecords<String, String> records = consumer.poll(Duration.ofMillis(1000));
                    if (records == null || records.isEmpty()) {
                        continue;
                    }

                    for (ConsumerRecord<String, String> record : records) {
                        if (!running.get()) {
                            break;
                        }
                        rateLimiter.acquire();
                        processRecord(record, handler);
                    }
                } catch (Exception e) {
                    lastError.set(e);
                    log.error("[Kafka] 消费循环异常，topic={}, groupId={}", topic, groupId, e);
                    sleepQuietly(1000);
                }
            }
        } finally {
            running.set(false);
            log.info("[Kafka] 消费循环已退出，topic={}, groupId={}", topic, groupId);
        }
    }

    private void processRecord(ConsumerRecord<String, String> record, IMessageHandler handler) {
        if (record == null || record.value() == null) {
            return;
        }
        try {
            QueueMessage message = QueueMessage.fromPayload(record.value());
            if (message == null) {
                message = QueueMessage.of(record.value());
            }
            if (handler != null) {
                handler.onMessage(message);
            }
            consumer.commitSync();
            consumedCount.incrementAndGet();
            lastError.set(null);
            log.debug("[Kafka] 消息处理成功，topic={}, partition={}, offset={}, traceId={}",
                    record.topic(), record.partition(), record.offset(), message.getTraceId());
        } catch (Exception e) {
            lastError.set(e);
            log.error("[Kafka] 消息处理异常，topic={}, partition={}, offset={}",
                    record.topic(), record.partition(), record.offset(), e);
        }
    }

    private KafkaConsumer<String, String> createConsumer(KafkaQueueProperties properties) {
        Properties props = new Properties();
        props.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, properties.resolvedBootstrapServers());
        props.put(ConsumerConfig.GROUP_ID_CONFIG, properties.resolvedGroupId());
        props.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class.getName());
        props.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class.getName());
        props.put(ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, properties.isEnableAutoCommit());
        props.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, properties.getAutoOffsetReset());
        props.put(ConsumerConfig.MAX_POLL_RECORDS_CONFIG, properties.getMaxPollRecords());
        props.put(ConsumerConfig.SESSION_TIMEOUT_MS_CONFIG, properties.getSessionTimeoutMs());
        props.put(ConsumerConfig.HEARTBEAT_INTERVAL_MS_CONFIG, properties.getHeartbeatIntervalMs());
        return new KafkaConsumer<>(props);
    }

    private void sleepQuietly(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException ignored) {
            Thread.currentThread().interrupt();
        }
    }
}
