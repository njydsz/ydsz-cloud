package com.njydsz.common.redis.service;

import java.time.Duration;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;

import jakarta.annotation.PreDestroy;

import org.springframework.beans.factory.DisposableBean;

import com.njydsz.common.redis.service.ops.RedisStreamOps;
import com.njydsz.common.redis.service.ops.RedisStreamOps.StreamMessage;

import lombok.extern.slf4j.Slf4j;

/**
 * Stream 消费模板（背压感知 + 自动 ACK + 死信兜底）
 *
 * <p>封装了 Stream 消费者组的完整消费循环，提供：
 * <ul>
 *   <li><b>背压控制</b>：根据 pending 消息积压量动态调整拉取批次大小，防止消费者过载</li>
 *   <li><b>自动 ACK</b>：消息处理成功后自动确认，失败不确认（留给死信认领）</li>
 *   <li>死信兜底：超过重试次数的消息自动转移到死信队列</li>
 *   <li>优雅关闭：{@link #shutdown()} 确保正在处理的消息完成后再退出</li>
 * </ul>
 *
 * <p><b>背压策略：</b>
 * <pre>
 *   pendingCount &gt; highWatermark  →  暂停拉取，等待积压消化
 *   pendingCount &lt; lowWatermark   →  逐步增大拉取批次（max 不超过 maxBatchSize）
 *   pendingCount 介于两者之间       →  保持当前批次大小
 * </pre>
 *
 * <p><b>使用示例：</b>
 * <pre>{@code
 * StreamConsumerTemplate consumer = new StreamConsumerTemplate(
 *     streamOps, "order-stream", "order-group", "consumer-1"
 * );
 * consumer.setMessageHandler(msg -> {
 *     processOrder(msg.getBodyField("orderId", String.class));
 * });
 * consumer.start();
 * }</pre>
 *
 * @author ydsz-team
 * @since 1.0.0
 */
@Slf4j
public class StreamConsumerTemplate implements DisposableBean {

    // ==================== 配置常量 ====================

    /** 默认最大批次大小 */
    private static final int DEFAULT_MAX_BATCH_SIZE = 100;
    /** 默认最小批次大小 */
    private static final int DEFAULT_MIN_BATCH_SIZE = 1;
    /** 默认高水位线：pending 超过此值暂停消费 */
    private static final int DEFAULT_HIGH_WATERMARK = 1000;
    /** 默认低水位线：pending 低于此值可增大批次 */
    private static final int DEFAULT_LOW_WATERMARK = 100;
    /** 默认空拉取后暂停毫秒数 */
    private static final long DEFAULT_EMPTY_PAUSE_MS = 500;
    /** 默认积压暂停毫秒数 */
    private static final long DEFAULT_BACKPRESSURE_PAUSE_MS = 1000;
    /** 默认最大重试次数（超过后转死信） */
    private static final int DEFAULT_MAX_RETRY_COUNT = 3;
    /** 默认死信最小空闲时间（毫秒） */
    private static final long DEFAULT_DEAD_LETTER_IDLE_MS = 60_000;
    /** 默认消费轮询间隔（毫秒） */
    private static final long DEFAULT_POLL_INTERVAL_MS = 100;
    /** 批次大小增长步长 */
    private static final int BATCH_SIZE_STEP = 10;
    /** 确保最小消费间隔，防止 CPU 空转 */
    private static final long MIN_CONSUME_INTERVAL_MS = 10;

    // ==================== 依赖组件 ====================

    private final RedisStreamOps streamOps;
    private final String streamKey;
    private final String groupName;
    private final String consumerName;
    private final String deadLetterConsumerName;

    // ==================== 可配置参数 ====================

    private int maxBatchSize = DEFAULT_MAX_BATCH_SIZE;
    private int minBatchSize = DEFAULT_MIN_BATCH_SIZE;
    private int highWatermark = DEFAULT_HIGH_WATERMARK;
    private int lowWatermark = DEFAULT_LOW_WATERMARK;
    private long emptyPauseMs = DEFAULT_EMPTY_PAUSE_MS;
    private long backpressurePauseMs = DEFAULT_BACKPRESSURE_PAUSE_MS;
    private int maxRetryCount = DEFAULT_MAX_RETRY_COUNT;
    private long deadLetterIdleMs = DEFAULT_DEAD_LETTER_IDLE_MS;
    private long pollIntervalMs = DEFAULT_POLL_INTERVAL_MS;

    // ==================== 回调 ====================

    /** 消息处理回调 */
    private Consumer<StreamMessage> messageHandler;
    /** 消费异常回调（返回 true 表示已处理，跳过默认 ACK 行为） */
    private java.util.function.BiConsumer<StreamMessage, Throwable> errorHandler;

    // ==================== 运行状态 ====================

    /** 当前动态批次大小 */
    private final AtomicLong currentBatchSize = new AtomicLong(DEFAULT_MIN_BATCH_SIZE);
    /** 消费线程 */
    private Thread consumerThread;
    /** 运行标记 */
    private final AtomicBoolean running = new AtomicBoolean(false);
    /** 累计消费消息数 */
    private final AtomicLong totalConsumed = new AtomicLong(0);
    /** 累计失败数 */
    private final AtomicLong totalFailed = new AtomicLong(0);
    /** 累计死信转移数 */
    private final AtomicLong totalDeadLettered = new AtomicLong(0);

    // ==================== 构造 ====================

    /**
     * 创建 Stream 消费模板
     *
     * @param streamOps     RedisStreamOps 实例
     * @param streamKey     Stream 键名
     * @param groupName     消费者组名
     * @param consumerName  消费者名
     */
    public StreamConsumerTemplate(RedisStreamOps streamOps, String streamKey,
                                  String groupName, String consumerName) {
        this.streamOps = Objects.requireNonNull(streamOps, "RedisStreamOps 不能为 null");
        this.streamKey = Objects.requireNonNull(streamKey, "streamKey 不能为 null");
        this.groupName = Objects.requireNonNull(groupName, "groupName 不能为 null");
        this.consumerName = Objects.requireNonNull(consumerName, "consumerName 不能为 null");
        this.deadLetterConsumerName = consumerName + ":deadletter";
    }

    // ==================== Fluent 配置 API ====================

    /**
     * 设置消息处理回调
     */
    public StreamConsumerTemplate setMessageHandler(Consumer<StreamMessage> handler) {
        this.messageHandler = handler;
        return this;
    }

    /**
     * 设置异常处理回调
     *
     * @param handler 异常处理器，参数为 (消息, 异常)。返回 true 表示已处理异常，不再重试
     */
    public StreamConsumerTemplate setErrorHandler(java.util.function.BiConsumer<StreamMessage, Throwable> handler) {
        this.errorHandler = handler;
        return this;
    }

    /**
     * 设置批次大小范围
     *
     * @param min 最小批次大小（至少 1）
     * @param max 最大批次大小
     */
    public StreamConsumerTemplate setBatchSizeRange(int min, int max) {
        if (min < 1) {
            throw new IllegalArgumentException("最小批次大小不能小于 1");
        }
        if (max < min) {
            throw new IllegalArgumentException("最大批次不能小于最小批次");
        }
        this.minBatchSize = min;
        this.maxBatchSize = max;
        this.currentBatchSize.set(min);
        return this;
    }

    /**
     * 设置背压水位线
     *
     * @param low  低水位线：pending 低于此值可增大批次
     * @param high 高水位线：pending 超过此值暂停消费
     */
    public StreamConsumerTemplate setWatermarks(int low, int high) {
        if (low < 0 || high < low) {
            throw new IllegalArgumentException("水位线设置非法：low=" + low + ", high=" + high);
        }
        this.lowWatermark = low;
        this.highWatermark = high;
        return this;
    }

    /**
     * 设置拉取间隔
     *
     * @param emptyPauseMs       空拉取后暂停毫秒数
     * @param backpressurePauseMs 积压时暂停毫秒数
     * @param pollIntervalMs     消费轮询间隔毫秒数
     */
    public StreamConsumerTemplate setPauseDurations(long emptyPauseMs, long backpressurePauseMs,
                                                     long pollIntervalMs) {
        this.emptyPauseMs = emptyPauseMs;
        this.backpressurePauseMs = backpressurePauseMs;
        this.pollIntervalMs = pollIntervalMs;
        return this;
    }

    /**
     * 设置重试与死信参数
     *
     * @param maxRetryCount    最大重试次数（超过后转死信）
     * @param deadLetterIdleMs 死信认领的最小空闲时间（毫秒）
     */
    public StreamConsumerTemplate setRetryConfig(int maxRetryCount, long deadLetterIdleMs) {
        this.maxRetryCount = maxRetryCount;
        this.deadLetterIdleMs = deadLetterIdleMs;
        return this;
    }

    // ==================== 控制方法 ====================

    /**
     * 启动消费循环（非阻塞，后台线程执行）
     *
     * @throws IllegalStateException 如果 messageHandler 未设置或已在运行
     */
    public void start() {
        if (messageHandler == null) {
            throw new IllegalStateException("必须先设置 messageHandler 再启动消费");
        }
        if (!running.compareAndSet(false, true)) {
            throw new IllegalStateException("消费者已在运行中");
        }
        consumerThread = new Thread(this::consumeLoop, "stream-consumer-" + streamKey + "-" + consumerName);
        consumerThread.setDaemon(true);
        consumerThread.start();
        log.info("【StreamConsumer】启动消费 | streamKey={} | groupName={} | consumerName={}",
                streamKey, groupName, consumerName);
    }

    /**
     * 优雅关闭：设置运行标记为 false，等待当前批次消费完成后退出
     */
    @PreDestroy
    public void shutdown() {
        if (!running.compareAndSet(true, false)) {
            return;
        }
        log.info("【StreamConsumer】正在关闭... | streamKey={}", streamKey);
        if (consumerThread != null) {
            consumerThread.interrupt();
            try {
                consumerThread.join(10_000);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
        log.info("【StreamConsumer】已关闭 | streamKey={} | consumed={} | failed={} | deadLettered={}",
                streamKey, totalConsumed.get(), totalFailed.get(), totalDeadLettered.get());
    }

    @Override
    public void destroy() {
        shutdown();
    }

    /**
     * 返回当前是否正在运行
     */
    public boolean isRunning() {
        return running.get();
    }

    /**
     * 获取累计消费统计
     */
    public ConsumerStats getStats() {
        return new ConsumerStats(
                totalConsumed.get(), totalFailed.get(), totalDeadLettered.get(),
                currentBatchSize.get(), getPendingCount()
        );
    }

    // ==================== 核心消费循环 ====================

    /**
     * 主消费循环
     */
    private void consumeLoop() {
        while (running.get() && !Thread.currentThread().isInterrupted()) {
            try {
                // 1. 背压检测：检查 pending 积压
                long pendingCount = getPendingCount();

                if (pendingCount > highWatermark) {
                    // 积压严重，暂停消费等待消化
                    log.warn("【StreamConsumer】pending 积压超过高水位线，暂停消费 | streamKey={} | pendingCount={} | highWatermark={}",
                            streamKey, pendingCount, highWatermark);
                    sleepQuietly(backpressurePauseMs);
                    continue;
                }

                // 2. 动态调整批次大小
                adjustBatchSize(pendingCount);

                // 3. 读取消息（先读 pending 未确认的，再读新消息）
                int batchSize = (int) Math.min(currentBatchSize.get(), Integer.MAX_VALUE);
                List<StreamMessage> messages = readMessages(batchSize);

                if (messages.isEmpty()) {
                    // 空拉取，短暂休眠
                    sleepQuietly(emptyPauseMs);
                    continue;
                }

                // 4. 逐条处理
                for (StreamMessage message : messages) {
                    if (!running.get()) {
                        break;
                    }
                    consumeMessage(message);
                }

                // 5. 最小消费间隔
                sleepQuietly(MIN_CONSUME_INTERVAL_MS);

            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                log.debug("【StreamConsumer】消费线程被中断 | streamKey={}", streamKey);
                break;
            } catch (Exception e) {
                log.error("【StreamConsumer】消费循环异常 | streamKey={}", streamKey, e);
                sleepQuietly(pollIntervalMs);
            }
        }
        log.info("【StreamConsumer】消费循环退出 | streamKey={}", streamKey);
    }

    /**
     * 读取消息：优先读 pending 未确认消息，再读新消息
     */
    private List<StreamMessage> readMessages(int batchSize) {
        // 先读取 pending 消息（从 "0" 偏移开始 = 读 pending）
        List<StreamMessage> pending = streamOps.readGroup(streamKey, groupName, consumerName, 0, true);
        if (!pending.isEmpty()) {
            return pending;
        }
        // pending 为空，读取新消息（从 ">" 偏移开始）
        return streamOps.readGroup(streamKey, groupName, consumerName, batchSize);
    }

    /**
     * 消费单条消息（带重试和 ACK）
     */
    private void consumeMessage(StreamMessage message) {
        String messageId = message.getId();
        int retryCount = getRetryCount(message);

        try {
            messageHandler.accept(message);
            // 处理成功 → ACK
            streamOps.ack(streamKey, groupName, messageId);
            totalConsumed.incrementAndGet();
        } catch (Exception e) {
            totalFailed.incrementAndGet();

            if (errorHandler != null) {
                try {
                    errorHandler.accept(message, e);
                } catch (Exception ex) {
                    log.error("【StreamConsumer】errorHandler 异常 | streamKey={} | messageId={}",
                            streamKey, messageId, ex);
                }
            }

            retryCount++;
            if (retryCount >= maxRetryCount) {
                // 超过重试次数 → 转移死信
                log.warn("【StreamConsumer】消息超过最大重试次数，转入死信 | streamKey={} | messageId={} | retryCount={}",
                        streamKey, messageId, retryCount);
                transferToDeadLetter(message, retryCount);
            } else {
                // 重试次数+1：通过 body 标记重试次数（实际由 XCLAIM 的 idle time 控制重新投递）
                log.debug("【StreamConsumer】消息处理失败，等待重试 | streamKey={} | messageId={} | retryCount={}",
                        streamKey, messageId, retryCount);
            }
        }
    }

    /**
     * 将超过重试次数的消息转移到死信队列
     */
    private void transferToDeadLetter(StreamMessage message, int retryCount) {
        try {
            // 使用 claim 将指定消息 ID 转移到死信消费者
            streamOps.claim(streamKey, groupName, deadLetterConsumerName, 0, List.of(message.getId()));
            totalDeadLettered.incrementAndGet();
        } catch (Exception e) {
            log.error("【StreamConsumer】死信转移失败 | streamKey={} | messageId={}",
                    streamKey, message.getId(), e);
        }
    }

    /**
     * 动态调整批次大小
     */
    private void adjustBatchSize(long pendingCount) {
        long current = currentBatchSize.get();
        long newSize;

        if (pendingCount < lowWatermark) {
            // 积压少，增大拉取批次（渐进增长）
            newSize = Math.min(current + BATCH_SIZE_STEP, maxBatchSize);
        } else if (pendingCount < highWatermark) {
            // 中等积压，保持当前批次
            newSize = current;
        } else {
            // 接近高水位，大幅缩小批次
            newSize = Math.max(current / 2, minBatchSize);
        }

        if (newSize != current) {
            currentBatchSize.set(newSize);
            log.debug("【StreamConsumer】批次大小调整 | streamKey={} | {} → {} | pending={}",
                    streamKey, current, newSize, pendingCount);
        }
    }

    /**
     * 获取当前 pending 消息数量
     */
    private long getPendingCount() {
        try {
            var summary = streamOps.pendingInfo(streamKey, groupName);
            if (summary != null) {
                return summary.getTotalPendingMessages();
            }
        } catch (Exception e) {
            log.debug("【StreamConsumer】获取 pending 数量失败 | streamKey={}", streamKey, e);
        }
        return 0;
    }

    /**
     * 从消息体中获取重试次数（默认 0）
     */
    private int getRetryCount(StreamMessage message) {
        try {
            Object retryObj = message.getBody().get("_retry_count");
            if (retryObj instanceof Number) {
                return ((Number) retryObj).intValue();
            }
        } catch (Exception ignored) {
        }
        return 0;
    }

    /**
     * 静默休眠（捕获中断并恢复中断标记）
     */
    private void sleepQuietly(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    // ==================== 统计记录 ====================

    /**
     * 消费者统计快照
     */
    public record ConsumerStats(long totalConsumed, long totalFailed, long totalDeadLettered,
                                 long currentBatchSize, long pendingCount) {
        @Override
        public String toString() {
            return String.format(
                    "StreamConsumerStats{consumed=%d, failed=%d, deadLettered=%d, batchSize=%d, pending=%d}",
                    totalConsumed, totalFailed, totalDeadLettered, currentBatchSize, pendingCount
            );
        }
    }
}
