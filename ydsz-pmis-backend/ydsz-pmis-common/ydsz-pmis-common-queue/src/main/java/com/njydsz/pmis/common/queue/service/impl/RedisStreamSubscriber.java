package com.njydsz.pmis.common.queue.service.impl;

import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

import org.springframework.data.redis.connection.stream.Consumer;
import org.springframework.data.redis.connection.stream.MapRecord;
import org.springframework.data.redis.connection.stream.ObjectRecord;
import org.springframework.data.redis.connection.stream.ReadOffset;
import org.springframework.data.redis.connection.stream.StreamOffset;
import org.springframework.data.redis.connection.stream.StreamReadOptions;
import org.springframework.data.redis.connection.stream.StreamRecords;
import org.springframework.data.redis.core.RedisTemplate;

import com.njydsz.pmis.common.queue.config.QueueProperties;
import com.njydsz.pmis.common.queue.domain.QueueMessage;
import com.njydsz.pmis.common.queue.metrics.MessageMetrics;
import com.njydsz.pmis.common.queue.rate.ConsumerRateLimiter;
import com.njydsz.pmis.common.queue.recovery.ConsumerThreadGuard;
import com.njydsz.pmis.common.queue.service.IMessageHandler;
import com.njydsz.pmis.common.queue.service.IMessageSubscriber;
import com.njydsz.pmis.common.queue.trace.MessageTracer;

import lombok.extern.slf4j.Slf4j;

/**
 * 基于 Redis Stream 的消息订阅者
 *
 * <p>使用 Redis XREADGROUP 命令以消费组模式读取消息，支持消息确认、重试和死信队列。
 * 通过 {@link RedisTemplate} 复用 ydsz-pmis-common-redis 的连接。
 *
 * @author ydsz-pmis-team
 * @since 1.0.0
 * @since 1.0.0
 */
@Slf4j
public class RedisStreamSubscriber implements IMessageSubscriber {

    private static final String FIELD_PAYLOAD = "payload";
    private static final String FIELD_RETRY_COUNT = "retryCount";
    private static final String FIELD_GROUP_KEY = "groupKey";
    private static final String FIELD_SEQUENCE = "sequence";

    private final RedisTemplate<String, Object> redisTemplate;
    private final String channel;
    private final String group;
    private final String consumer;
    private final int retryMax;
    private final int blockMillis;
    private final int batchSize;
    private final String dlqChannel;

    private final AtomicBoolean running;
    private final AtomicLong consumedCount;
    private final AtomicReference<Throwable> lastError;
    private final MessageMetrics messageMetrics;
    private final ConsumerRateLimiter rateLimiter;
    private final ExecutorService consumerExecutor;
    private volatile ConsumerThreadGuard threadGuard;

    public RedisStreamSubscriber(RedisTemplate<String, Object> redisTemplate,
                                  String channel,
                                  QueueProperties queueProperties,
                                  ExecutorService consumerExecutor) {
        if (redisTemplate == null) {
            throw new IllegalArgumentException("RedisTemplate 不能为空");
        }
        if (channel == null || channel.isEmpty()) {
            throw new IllegalArgumentException("通道名称不能为空");
        }
        if (queueProperties == null) {
            throw new IllegalArgumentException("队列配置不能为空");
        }
        this.redisTemplate = redisTemplate;
        this.channel = channel;
        this.group = queueProperties.resolvedStreamGroup();
        this.consumer = queueProperties.resolvedStreamConsumer() + "-" + generateShortId();
        this.retryMax = queueProperties.resolvedStreamRetryMax();
        this.blockMillis = Math.toIntExact(queueProperties.resolvedStreamBlockMillis());
        this.batchSize = queueProperties.resolvedStreamBatchSize();
        this.dlqChannel = channel + queueProperties.resolvedStreamDeadLetterSuffix();
        this.running = new AtomicBoolean(false);
        this.consumedCount = new AtomicLong(0);
        this.lastError = new AtomicReference<>();
        this.messageMetrics = new MessageMetrics(channel, "redis-stream");
        this.rateLimiter = queueProperties.createRateLimiter();
        this.consumerExecutor = consumerExecutor;
        ensureGroup();
        log.info("[RedisStream] 订阅者初始化完成（复用 ydsz-pmis-common-redis 连接），channel={}, group={}, consumer={}",
                channel, group, consumer);
    }

    @Override
    public String subscribe() {
        QueueMessage message = poll();
        return message != null ? QueueMessage.toPayload(message) : null;
    }

    @Override
    public String subscribeAsync(IMessageHandler handler) {
        if (!running.compareAndSet(false, true)) {
            log.warn("[RedisStream] 订阅者已在运行中，consumer={}", consumer);
            return consumer;
        }
        threadGuard = new ConsumerThreadGuard("redis-stream-" + consumer, 10, consumerExecutor);
        threadGuard.start(() -> consumeLoop(handler));
        log.info("[RedisStream] 异步消费已启动（ConsumerThreadGuard守护），channel={}, consumer={}", channel, consumer);
        return consumer;
    }

    @Override
    public void stop() {
        if (!running.compareAndSet(true, false)) {
            return;
        }
        if (threadGuard != null) {
            threadGuard.stop();
        }
        log.info("[RedisStream] 收到停止信号，consumer={}", consumer);
    }

    @Override
    public boolean isRunning() {
        return running.get();
    }

    @Override
    public Object getChannel() {
        return channel;
    }

    @Override
    public String getConsumerId() {
        return consumer;
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
        while (running.get()) {
            try {
                StreamReadOptions readOptions = StreamReadOptions.empty()
                        .count(batchSize)
                        .block(Duration.ofMillis(blockMillis));
                Consumer consumerObj = Consumer.from(group, consumer);
                StreamOffset<String> offset = StreamOffset.create(channel, ReadOffset.lastConsumed());
                List<MapRecord<String, Object, Object>> records =
                        redisTemplate.opsForStream().read(consumerObj, readOptions, offset);
                if (records == null || records.isEmpty()) {
                    continue;
                }
                for (MapRecord<String, Object, Object> entry : records) {
                    rateLimiter.acquire();
                    if (!running.get()) {
                        break;
                    }
                    processEntry(entry, handler);
                }
            } catch (Exception ex) {
                lastError.set(ex);
                log.error("[RedisStream] 消费循环异常，channel={}, group={}, consumer={}",
                        channel, group, consumer, ex);
                long backoff = Math.min(1000L * (1L << Math.min(5, 5)), 30000L);
                sleepQuietly(backoff);
            }
        }
        log.info("[RedisStream] 消费循环已退出，consumer={}", consumer);
    }

    private QueueMessage poll() {
        try {
            StreamReadOptions readOptions = StreamReadOptions.empty().count(1).block(Duration.ofMillis(blockMillis));
            Consumer consumerObj = Consumer.from(group, consumer);
            StreamOffset<String> offset = StreamOffset.create(channel, ReadOffset.lastConsumed());
            List<MapRecord<String, Object, Object>> records =
                    redisTemplate.opsForStream().read(consumerObj, readOptions, offset);
            if (records == null || records.isEmpty()) {
                return null;
            }
            MapRecord<String, Object, Object> entry = records.get(0);
            QueueMessage message = parseMessage(entry.getValue());
            if (message == null) {
                redisTemplate.opsForStream().acknowledge(channel, group, entry.getId());
                log.warn("[RedisStream] 消息解析失败，已ACK，entryId={}", entry.getId());
                return null;
            }
            return message;
        } catch (Exception ex) {
            lastError.set(ex);
            log.error("[RedisStream] 拉取消息异常，channel={}, consumer={}", channel, consumer, ex);
            return null;
        }
    }

    private void processEntry(MapRecord<String, Object, Object> entry, IMessageHandler handler) {
        QueueMessage message = parseMessage(entry.getValue());
        if (message == null) {
            redisTemplate.opsForStream().acknowledge(channel, group, entry.getId());
            log.warn("[RedisStream] 消息解析失败，已ACK，entryId={}", entry.getId());
            return;
        }
        MessageTracer.injectTraceId(message.getTraceId());
        long startMillis = System.currentTimeMillis();
        try {
            if (handler != null) {
                handler.onMessage(message);
            }
            redisTemplate.opsForStream().acknowledge(channel, group, entry.getId());
            consumedCount.incrementAndGet();
            lastError.set(null);
            long latency = System.currentTimeMillis() - startMillis;
            messageMetrics.recordConsume(true, latency);
            log.debug("[RedisStream] 消息处理成功，channel={}, traceId={}", channel, message.getTraceId());
        } catch (Exception ex) {
            long latency = System.currentTimeMillis() - startMillis;
            messageMetrics.recordConsume(false, latency);
            handleFailedMessage(entry, message, ex);
        } finally {
            MessageTracer.clearTraceId();
        }
    }

    private void handleFailedMessage(MapRecord<String, Object, Object> entry,
                                      QueueMessage message, Exception ex) {
        int nextRetry = message.incrementRetryCount();
        lastError.set(ex);
        try {
            if (nextRetry > retryMax) {
                writeStream(dlqChannel, message);
                redisTemplate.opsForStream().acknowledge(channel, group, entry.getId());
                log.error("[RedisStream] 消息已转入死信队列，channel={}, dlq={}, traceId={}, retryCount={}",
                        channel, dlqChannel, message.getTraceId(), nextRetry, ex);
            } else {
                writeStream(channel, message);
                redisTemplate.opsForStream().acknowledge(channel, group, entry.getId());
                log.warn("[RedisStream] 消息将重试，channel={}, traceId={}, retry={}/{}/max={}",
                        channel, message.getTraceId(), nextRetry, retryMax, ex.getMessage());
            }
        } catch (Exception writeEx) {
            log.error("[RedisStream] 死信处理异常，channel={}, traceId={}", channel, message.getTraceId(), writeEx);
        }
    }

    private void writeStream(String streamKey, QueueMessage message) {
        Map<String, String> fields = new HashMap<>(4);
        fields.put(FIELD_PAYLOAD, QueueMessage.toPayload(message));
        fields.put(FIELD_RETRY_COUNT, String.valueOf(message.getRetryCount()));
        ObjectRecord<String, Map<String, String>> record = StreamRecords.newRecord()
                .ofObject(fields)
                .withStreamKey(streamKey);
        redisTemplate.opsForStream().add(record);
    }

    private QueueMessage parseMessage(Map<Object, Object> fields) {
        if (fields == null || fields.isEmpty()) {
            return null;
        }
        Object payloadObj = fields.get(FIELD_PAYLOAD);
        String payload = payloadObj != null ? String.valueOf(payloadObj) : null;
        QueueMessage message = QueueMessage.fromPayload(payload);
        if (message == null) {
            message = QueueMessage.of(payload);
        }
        Object retryObj = fields.get(FIELD_RETRY_COUNT);
        if (retryObj != null) {
            try {
                message.setRetryCount(Integer.parseInt(String.valueOf(retryObj)));
            } catch (NumberFormatException ignored) {
                message.setRetryCount(0);
            }
        }
        
        // 解析顺序消息字段
        Object groupKeyObj = fields.get(FIELD_GROUP_KEY);
        if (groupKeyObj != null) {
            String groupKey = String.valueOf(groupKeyObj);
            Object sequenceObj = fields.get(FIELD_SEQUENCE);
            Long sequence = null;
            if (sequenceObj != null) {
                try {
                    sequence = Long.parseLong(String.valueOf(sequenceObj));
                } catch (NumberFormatException ignored) {
                    sequence = null;
                }
            }
            message.setSequential(groupKey, sequence);
        }
        
        return message;
    }

    private void ensureGroup() {
        try {
            redisTemplate.opsForStream().createGroup(channel, group);
            log.debug("[RedisStream] 消费组已创建，channel={}, group={}", channel, group);
        } catch (Exception ex) {
            String msg = ex.getMessage();
            if (msg != null && msg.contains("BUSYGROUP")) {
                log.debug("[RedisStream] 消费组已存在，channel={}, group={}", channel, group);
            } else {
                throw ex;
            }
        }
    }

    private void sleepQuietly(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException ignored) {
            Thread.currentThread().interrupt();
        }
    }

    private String generateShortId() {
        return UUID.randomUUID().toString().replace("-", "").substring(0, 8);
    }
}
