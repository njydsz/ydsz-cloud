package com.njydsz.pmis.common.queue.metrics;

import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.LongAdder;

/**
 * 消息队列监控指标
 *
 * <p>提供消息发布和消费的实时监控指标，包括：
 * <ul>
 *   <li>发布 QPS / 消费 QPS</li>
 *   <li>发布延迟 / 消费延迟</li>
 *   <li>消息积压量</li>
 *   <li>成功/失败次数</li>
 * </ul>
 *
 * <p><b>使用示例：</b>
 * <pre>{@code
 * MessageMetrics metrics = new MessageMetrics("my-queue");
 *
 * // 在发布时记录
 * metrics.recordPublish(success, latencyMs);
 *
 * // 在消费时记录
 * metrics.recordConsume(success, latencyMs);
 *
 * // 获取监控数据
 * log.info("Publish QPS: {}", metrics.getAvgPublishQps());
 * log.info("Consume QPS: {}", metrics.getAvgConsumeQps());
 * }</pre>
 *
 * @author ydsz-pmis-team
 * @since 1.0.0
 * @since 1.0.0
 */
public class MessageMetrics {

    private final String queueName;
    private final String queueType;

    // 发布指标
    private final LongAdder publishSuccessCount = new LongAdder();
    private final LongAdder publishFailCount = new LongAdder();
    private final AtomicLong publishLatencySum = new AtomicLong(0);
    private final AtomicLong publishMaxLatency = new AtomicLong(0);

    // 消费指标
    private final LongAdder consumeSuccessCount = new LongAdder();
    private final LongAdder consumeFailCount = new LongAdder();
    private final AtomicLong consumeLatencySum = new AtomicLong(0);
    private final AtomicLong consumeMaxLatency = new AtomicLong(0);

    // 积压量
    private volatile long backlogCount = 0;

    // 启动时间
    private final long startTime = System.currentTimeMillis();

    /**
     * 构造函数
     *
     * @param queueName 队列名称
     * @param queueType 队列类型（如 redis, kafka 等）
     */
    public MessageMetrics(String queueName, String queueType) {
        this.queueName = queueName;
        this.queueType = queueType;
    }

    /**
     * 记录一次消息发布
     *
     * @param success 是否发布成功
     * @param latencyMs 发布延迟（毫秒）
     */
    public void recordPublish(boolean success, long latencyMs) {
        if (success) {
            publishSuccessCount.increment();
        } else {
            publishFailCount.increment();
        }
        publishLatencySum.addAndGet(latencyMs);
        updateMaxLatency(publishMaxLatency, latencyMs);
    }

    /**
     * 记录一次消息消费
     *
     * @param success 是否消费成功
     * @param latencyMs 消费延迟（毫秒）
     */
    public void recordConsume(boolean success, long latencyMs) {
        if (success) {
            consumeSuccessCount.increment();
        } else {
            consumeFailCount.increment();
        }
        consumeLatencySum.addAndGet(latencyMs);
        updateMaxLatency(consumeMaxLatency, latencyMs);
    }

    /**
     * 更新消息积压量
     *
     * @param count 当前积压数量
     */
    public void updateBacklog(long count) {
        this.backlogCount = count;
    }

    /**
     * 获取发布成功总数
     *
     * @return 发布成功数量
     */
    public long getPublishSuccessCount() {
        return publishSuccessCount.sum();
    }

    /**
     * 获取发布失败总数
     *
     * @return 发布失败数量
     */
    public long getPublishFailCount() {
        return publishFailCount.sum();
    }

    /**
     * 获取消费成功总数
     *
     * @return 消费成功数量
     */
    public long getConsumeSuccessCount() {
        return consumeSuccessCount.sum();
    }

    /**
     * 获取消费失败总数
     *
     * @return 消费失败数量
     */
    public long getConsumeFailCount() {
        return consumeFailCount.sum();
    }

    /**
     * 获取平均发布延迟（毫秒）
     *
     * @return 平均延迟
     */
    public double getAvgPublishLatency() {
        long total = publishSuccessCount.sum() + publishFailCount.sum();
        return total == 0 ? 0.0 : (double) publishLatencySum.get() / total;
    }

    /**
     * 获取最大发布延迟（毫秒）
     *
     * @return 最大延迟
     */
    public long getMaxPublishLatency() {
        return publishMaxLatency.get();
    }

    /**
     * 获取平均消费延迟（毫秒）
     *
     * @return 平均延迟
     */
    public double getAvgConsumeLatency() {
        long total = consumeSuccessCount.sum() + consumeFailCount.sum();
        return total == 0 ? 0.0 : (double) consumeLatencySum.get() / total;
    }

    /**
     * 获取最大消费延迟（毫秒）
     *
     * @return 最大延迟
     */
    public long getMaxConsumeLatency() {
        return consumeMaxLatency.get();
    }

    /**
     * 获取消息积压量
     *
     * @return 积压数量
     */
    public long getBacklogCount() {
        return backlogCount;
    }

    /**
     * 获取平均发布 QPS
     *
     * @return QPS
     */
    public double getAvgPublishQps() {
        long elapsedSeconds = getElapsedSeconds();
        return elapsedSeconds == 0 ? 0.0 : (double) publishSuccessCount.sum() / elapsedSeconds;
    }

    /**
     * 获取平均消费 QPS
     *
     * @return QPS
     */
    public double getAvgConsumeQps() {
        long elapsedSeconds = getElapsedSeconds();
        return elapsedSeconds == 0 ? 0.0 : (double) consumeSuccessCount.sum() / elapsedSeconds;
    }

    /**
     * 获取队列名称
     *
     * @return 队列名称
     */
    public String getQueueName() {
        return queueName;
    }

    /**
     * 获取队列类型
     *
     * @return 队列类型
     */
    public String getQueueType() {
        return queueType;
    }

    /**
     * 获取运行时间（秒）
     *
     * @return 运行秒数
     */
    public long getElapsedSeconds() {
        return (System.currentTimeMillis() - startTime) / 1000;
    }

    /**
     * 获取完整的指标摘要
     *
     * @return 指标摘要字符串
     */
    public String getSummary() {
        return String.format(
                "QueueMetrics[%s:%s] pub(success=%d,fail=%d,qps=%.1f,latencyAvg=%.1fms,latencyMax=%dms) " +
                        "cons(success=%d,fail=%d,qps=%.1f,latencyAvg=%.1fms,latencyMax=%dms) backlog=%d uptime=%ds",
                queueName, queueType,
                publishSuccessCount.sum(), publishFailCount.sum(), getAvgPublishQps(),
                getAvgPublishLatency(), getMaxPublishLatency(),
                consumeSuccessCount.sum(), consumeFailCount.sum(), getAvgConsumeQps(),
                getAvgConsumeLatency(), getMaxConsumeLatency(),
                backlogCount, getElapsedSeconds()
        );
    }

    /**
     * 重置所有指标
     */
    public void reset() {
        publishSuccessCount.reset();
        publishFailCount.reset();
        publishLatencySum.set(0);
        publishMaxLatency.set(0);
        consumeSuccessCount.reset();
        consumeFailCount.reset();
        consumeLatencySum.set(0);
        consumeMaxLatency.set(0);
        backlogCount = 0;
    }

    private void updateMaxLatency(AtomicLong maxLatency, long currentLatency) {
        long current;
        do {
            current = maxLatency.get();
            if (currentLatency <= current) {
                return;
            }
        } while (!maxLatency.compareAndSet(current, currentLatency));
    }
}
