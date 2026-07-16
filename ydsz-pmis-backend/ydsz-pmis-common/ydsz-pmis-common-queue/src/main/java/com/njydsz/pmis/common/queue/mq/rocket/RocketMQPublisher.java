package com.njydsz.pmis.common.queue.mq.rocket;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.locks.ReentrantLock;

import org.apache.rocketmq.client.producer.DefaultMQProducer;
import org.apache.rocketmq.client.producer.SendCallback;
import org.apache.rocketmq.client.producer.SendResult;
import org.apache.rocketmq.client.producer.SendStatus;
import org.apache.rocketmq.common.message.Message;

import com.njydsz.pmis.common.exception.custom.BusinessException;
import com.njydsz.pmis.common.queue.domain.QueueMessage;
import com.njydsz.pmis.common.queue.service.IMessagePublisher;

import lombok.extern.slf4j.Slf4j;

/**
 * RocketMQ 消息发布者
 *
 * <p>使用 RocketMQ Producer API 实现消息发布功能。
 * 支持同步、异步和延迟发送，提供完善的失败重试和状态校验机制。
 *
 * <p><b>技术特点：</b>
 * <ul>
 *   <li>同步发送：带重试机制，失败时抛出 BusinessException</li>
 *   <li>异步发送：基于 CompletableFuture，回调中校验 SendStatus</li>
 *   <li>延迟消息：支持指定延迟级别的消息</li>
 *   <li>高可靠：发送失败自动重试 3 次，SendStatus 非 SEND_OK 时抛异常</li>
 * </ul>
 *
 * <p><b>使用示例：</b>
 * <pre>{@code
 * RocketMQPublisher publisher = new RocketMQPublisher(properties, "my-topic");
 * publisher.publish("Hello RocketMQ");
 * publisher.publish(QueueMessage.of("Hello"));
 * publisher.publishAsync(QueueMessage.of("Async"));
 * publisher.publishDelayed(QueueMessage.of("Delay"), 60000);
 * publisher.close();
 * }</pre>
 *
 * @author ydsz-pmis-team
 * @since 1.0.0
 */
@Slf4j
public class RocketMQPublisher implements IMessagePublisher {

    private static final int DEFAULT_MAX_RETRY = 3;
    private static final int DEFAULT_SEND_TIMEOUT = 5000;

    private final DefaultMQProducer producer;
    private final String topic;
    private final String tag;
    private final int maxRetryTimes;
    private final int sendTimeout;
    private volatile boolean closed = false;
    private final ReentrantLock closeLock = new ReentrantLock();

    public RocketMQPublisher(RocketMQProperties properties, String topic) {
        this(properties, topic, "*");
    }

    public RocketMQPublisher(RocketMQProperties properties, String topic, String tag) {
        if (properties == null) {
            throw new IllegalArgumentException("RocketMQ 配置不能为空");
        }
        if (topic == null || topic.isEmpty()) {
            throw new IllegalArgumentException("主题名称不能为空");
        }
        this.topic = topic;
        this.tag = tag != null ? tag : "*";
        this.maxRetryTimes = properties.getMaxRetryCount() > 0 ? properties.getMaxRetryCount() : DEFAULT_MAX_RETRY;
        this.sendTimeout = DEFAULT_SEND_TIMEOUT;
        this.producer = createProducer(properties);
        log.info("[RocketMQ] 发布者初始化完成，topic={}, tag={}, namesrvAddr={}, maxRetry={}",
                topic, this.tag, properties.resolvedNamesrvAddr(), maxRetryTimes);
    }

    @Override
    public void publish(String message) {
        if (message == null) {
            throw BusinessException.builder().key("消息内容不能为空").build();
        }
        checkNotClosed();
        try {
            QueueMessage queueMessage = QueueMessage.fromPayload(message);
            if (queueMessage == null) {
                queueMessage = QueueMessage.of(message);
            }
            publish(queueMessage);
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            log.error("[RocketMQ] 消息发布失败，topic={}", topic, e);
            throw BusinessException.builder().key("RocketMQ 消息发布失败：" + e.getMessage()).cause(e).build();
        }
    }

    @Override
    public void publish(QueueMessage message) {
        if (message == null) {
            throw BusinessException.builder().key("消息不能为空").build();
        }
        checkNotClosed();

        Exception lastException = null;
        for (int attempt = 0; attempt <= maxRetryTimes; attempt++) {
            try {
                String payload = QueueMessage.toPayload(message);
                Message msg = new Message(topic, tag, message.getTraceId(), payload.getBytes());
                SendResult result = producer.send(msg);
                validateSendStatus(result, message.getTraceId(), topic);
                if (log.isDebugEnabled()) {
                    log.debug("[RocketMQ] 消息已发送，topic={}, traceId={}, msgId={}, status={}, attempt={}",
                            topic, message.getTraceId(), result.getMsgId(), result.getSendStatus(), attempt + 1);
                }
                return;
            } catch (Exception e) {
                lastException = e;
                if (attempt < maxRetryTimes) {
                    log.warn("[RocketMQ] 消息发送失败，准备重试，topic={}, traceId={}, attempt={}/{}, error={}",
                            topic, message.getTraceId(), attempt + 1, maxRetryTimes + 1, e.getMessage());
                    sleepBeforeRetry(attempt);
                }
            }
        }

        log.error("[RocketMQ] 消息发布失败，已重试 {} 次，topic={}, traceId={}",
                maxRetryTimes, topic, message.getTraceId(), lastException);
        String errorMsg = lastException != null ? lastException.getMessage() : "未知错误";
        throw BusinessException.builder().key("RocketMQ 消息发布失败，已重试 " + maxRetryTimes + " 次：" + errorMsg).cause(lastException).build();
    }

    /**
     * 异步发送消息
     *
     * <p>使用 RocketMQ 异步发送 API，返回 CompletableFuture 供调用方处理结果。
     * 回调中会校验 SendStatus，非 SEND_OK 时记录错误日志并在 Future 中抛出异常。
     *
     * @param message 待发布的消息
     * @return CompletableFuture 用于异步获取发送结果
     */
    public CompletableFuture<SendResult> publishAsync(QueueMessage message) {
        if (message == null) {
            throw BusinessException.builder().key("消息不能为空").build();
        }
        checkNotClosed();

        CompletableFuture<SendResult> future = new CompletableFuture<>();
        try {
            String payload = QueueMessage.toPayload(message);
            Message msg = new Message(topic, tag, message.getTraceId(), payload.getBytes());
            producer.send(msg, new SendCallback() {
                @Override
                public void onSuccess(SendResult result) {
                    try {
                        validateSendStatus(result, message.getTraceId(), topic);
                        if (log.isDebugEnabled()) {
                            log.debug("[RocketMQ] 异步消息发送成功，topic={}, traceId={}, msgId={}, status={}",
                                    topic, message.getTraceId(), result.getMsgId(), result.getSendStatus());
                        }
                        future.complete(result);
                    } catch (Exception e) {
                        log.error("[RocketMQ] 异步消息发送状态异常，topic={}, traceId={}, status={}",
                                topic, message.getTraceId(), result.getSendStatus(), e);
                        future.completeExceptionally(BusinessException.builder()
                                .key("RocketMQ 异步消息发送状态异常：" + e.getMessage()).cause(e).build());
                    }
                }

                @Override
                public void onException(Throwable e) {
                    log.error("[RocketMQ] 异步消息发送失败，topic={}, traceId={}",
                            topic, message.getTraceId(), e);
                    future.completeExceptionally(BusinessException.builder()
                            .key("RocketMQ 异步消息发送失败：" + e.getMessage()).cause(e).build());
                }
            });
        } catch (Exception e) {
            log.error("[RocketMQ] 异步消息发送初始化失败，topic={}, traceId={}",
                    topic, message.getTraceId(), e);
            future.completeExceptionally(BusinessException.builder()
                    .key("RocketMQ 异步消息发送初始化失败：" + e.getMessage()).cause(e).build());
        }
        return future;
    }

    @Override
    public void publishDelayed(QueueMessage message, long delayMillis) {
        if (message == null) {
            throw BusinessException.builder().key("消息不能为空").build();
        }
        if (delayMillis <= 0) {
            throw BusinessException.builder().key("延迟时间必须大于 0").build();
        }
        checkNotClosed();

        Exception lastException = null;
        for (int attempt = 0; attempt <= maxRetryTimes; attempt++) {
            try {
                String payload = QueueMessage.toPayload(message);
                int delayLevel = calculateDelayLevel(delayMillis);
                Message msg = new Message(topic, tag, message.getTraceId(), payload.getBytes());
                msg.setDelayTimeLevel(delayLevel);
                SendResult result = producer.send(msg);
                validateSendStatus(result, message.getTraceId(), topic);
                log.debug("[RocketMQ] 延迟消息已发送，topic={}, traceId={}, delayLevel={}, msgId={}, status={}, attempt={}",
                        topic, message.getTraceId(), delayLevel, result.getMsgId(), result.getSendStatus(), attempt + 1);
                return;
            } catch (Exception e) {
                lastException = e;
                if (attempt < maxRetryTimes) {
                    log.warn("[RocketMQ] 延迟消息发送失败，准备重试，topic={}, traceId={}, attempt={}/{}, error={}",
                            topic, message.getTraceId(), attempt + 1, maxRetryTimes + 1, e.getMessage());
                    sleepBeforeRetry(attempt);
                }
            }
        }

        log.error("[RocketMQ] 延迟消息发布失败，已重试 {} 次，topic={}, traceId={}",
                maxRetryTimes, topic, message.getTraceId(), lastException);
        String errorMsg = lastException != null ? lastException.getMessage() : "未知错误";
        throw BusinessException.builder().key("RocketMQ 延迟消息发布失败，已重试 " + maxRetryTimes + " 次：" + errorMsg).cause(lastException).build();
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
                    producer.shutdown();
                    log.info("[RocketMQ] 发布者已关闭，topic={}", topic);
                }
            } catch (Exception e) {
                log.warn("[RocketMQ] 关闭发布者时发生异常", e);
            }
        } finally {
            closeLock.unlock();
        }
    }

    private DefaultMQProducer createProducer(RocketMQProperties properties) {
        try {
            DefaultMQProducer producer = new DefaultMQProducer(properties.resolvedGroupId());
            producer.setNamesrvAddr(properties.resolvedNamesrvAddr());
            producer.setRetryTimesWhenSendFailed(maxRetryTimes);
            producer.setRetryTimesWhenSendAsyncFailed(maxRetryTimes);
            producer.setSendMsgTimeout(sendTimeout);
            producer.setMaxMessageSize(1024 * 1024 * 4);
            producer.start();
            return producer;
        } catch (Exception e) {
            log.error("[RocketMQ] 创建生产者失败，namesrvAddr={}", properties.resolvedNamesrvAddr(), e);
            throw BusinessException.builder().key("RocketMQ 生产者创建失败：" + e.getMessage()).cause(e).build();
        }
    }

    /**
     * 校验发送结果状态
     *
     * <p>检查 SendResult.getSendStatus() 是否为 SEND_OK。
     * 非 SEND_OK 时抛出异常，防止消息静默丢失。
     */
    private void validateSendStatus(SendResult result, String traceId, String topicName) {
        if (result == null) {
            throw BusinessException.builder().key("RocketMQ 发送结果为空，topic=" + topicName + ", traceId=" + traceId).build();
        }
        SendStatus status = result.getSendStatus();
        if (status != SendStatus.SEND_OK) {
            String errorMsg = String.format("RocketMQ 发送状态异常，topic=%s, traceId=%s, msgId=%s, status=%s",
                    topicName, traceId, result.getMsgId(), status);
            log.error("[RocketMQ] {}", errorMsg);
            throw BusinessException.builder().key(errorMsg).build();
        }
    }

    /**
     * 重试前等待，使用指数退避策略
     *
     * @param attempt 当前重试次数（从 0 开始）
     */
    private void sleepBeforeRetry(int attempt) {
        long backoffMs = Math.min(100L * (1L << attempt), 2000L);
        try {
            Thread.sleep(backoffMs);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.warn("[RocketMQ] 重试等待被中断");
        }
    }

    /**
     * 计算延迟级别
     *
     * <p>RocketMQ 支持的延迟级别：
     * 1=1s, 2=5s, 3=10s, 4=30s, 5=1m, 6=2m, 7=3m, 8=4m, 9=5m, 10=6m,
     * 11=7m, 12=8m, 13=9m, 14=10m, 15=20m, 16=30m, 17=1h, 18=2h
     */
    private int calculateDelayLevel(long delayMillis) {
        if (delayMillis <= 1000) {
            return 1;
        } else if (delayMillis <= 5000) {
            return 2;
        } else if (delayMillis <= 10000) {
            return 3;
        } else if (delayMillis <= 30000) {
            return 4;
        } else if (delayMillis <= 60000) {
            return 5;
        } else if (delayMillis <= 120000) {
            return 6;
        } else if (delayMillis <= 180000) {
            return 7;
        } else if (delayMillis <= 240000) {
            return 8;
        } else if (delayMillis <= 300000) {
            return 9;
        } else if (delayMillis <= 360000) {
            return 10;
        } else if (delayMillis <= 420000) {
            return 11;
        } else if (delayMillis <= 480000) {
            return 12;
        } else if (delayMillis <= 540000) {
            return 13;
        } else if (delayMillis <= 600000) {
            return 14;
        } else if (delayMillis <= 1200000) {
            return 15;
        } else if (delayMillis <= 1800000) {
            return 16;
        } else if (delayMillis <= 3600000) {
            return 17;
        } else {
            return 18;
        }
    }

    private void checkNotClosed() {
        if (closed) {
            throw BusinessException.builder().key("发布者已关闭，无法继续操作").build();
        }
    }
}
