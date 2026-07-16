package com.njydsz.pmis.common.sentry.logging;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.atomic.AtomicLong;

import com.njydsz.pmis.common.sentry.domain.LogEvent;
import com.njydsz.pmis.common.sentry.spi.LogPublisher;

import lombok.extern.slf4j.Slf4j;

/**
 * 异步日志发布器
 *
 * <p>通过有界队列解耦业务线程与日志发送线程，避免日志上报阻塞业务请求。
 *
 * <p>核心策略：
 * <ul>
 *   <li>有界队列：默认容量 8192，队列满时丢弃最旧日志（背压降级）</li>
 *   <li>批量发送：每 {@code batchSize} 条或每 {@code flushIntervalMillis} 毫秒批量发送</li>
 *   <li>令牌桶限流：限制每秒最大发送量，防止日志风暴打爆下游</li>
 *   <li>优雅关闭：{@code close()} 时等待剩余日志发送完毕（最多 5 秒）</li>
 * </ul>
 *
 * @author ydsz-pmis-team
 * @since 1.5.0
 */
@Slf4j
public class AsyncLogPublisher implements LogPublisher, AutoCloseable {

    private final LogPublisher delegate;
    private final BlockingQueue<LogEvent> queue;
    private final int batchSize;
    private final long flushIntervalMillis;
    private final Thread consumerThread;
    private final AtomicLong droppedCount = new AtomicLong(0);
    private final AtomicLong totalPublished = new AtomicLong(0);
    private volatile boolean running = true;

    /** 令牌桶限流（每秒最大发送条数，0 表示不限流） */
    private final int maxRatePerSecond;
    private volatile long tokensAvailable;
    private volatile long lastTokenRefillTime;

    public AsyncLogPublisher(LogPublisher delegate, int queueCapacity, int batchSize,
                             long flushIntervalMillis, int maxRatePerSecond) {
        this.delegate = delegate;
        this.queue = new ArrayBlockingQueue<>(queueCapacity);
        this.batchSize = batchSize;
        this.flushIntervalMillis = flushIntervalMillis;
        this.maxRatePerSecond = maxRatePerSecond;
        this.tokensAvailable = maxRatePerSecond;
        this.lastTokenRefillTime = System.currentTimeMillis();

        this.consumerThread = new Thread(this::consumeLoop, "sentry-async-log-publisher");
        this.consumerThread.setDaemon(true);
        this.consumerThread.start();
        log.info("[Sentry] AsyncLogPublisher 初始化: delegate={}, queueCapacity={}, batchSize={}, " +
                        "flushInterval={}ms, maxRate={}/s",
                delegate.getName(), queueCapacity, batchSize, flushIntervalMillis,
                maxRatePerSecond > 0 ? maxRatePerSecond : "unlimited");
    }

    @Override
    public boolean publish(LogEvent event) {
        if (!running) {
            return false;
        }
        // 非阻塞入队，队列满时丢弃最旧日志
        if (!queue.offer(event)) {
            LogEvent dropped = queue.poll();
            if (dropped != null) {
                droppedCount.incrementAndGet();
            }
            queue.offer(event);
        }
        return true;
    }

    /**
     * 消费循环：批量取 + 批量发
     */
    private void consumeLoop() {
        List<LogEvent> batch = new ArrayList<>(batchSize);
        while (running || !queue.isEmpty()) {
            try {
                // 阻塞等待第一条
                LogEvent first = queue.poll(flushIntervalMillis, java.util.concurrent.TimeUnit.MILLISECONDS);
                if (first != null) {
                    batch.add(first);
                    // 尝试批量取更多（非阻塞）
                    queue.drainTo(batch, batchSize - 1);
                }

                if (!batch.isEmpty()) {
                    // 令牌桶限流
                    if (maxRatePerSecond > 0) {
                        waitForTokens(batch.size());
                    }
                    // 批量发送
                    for (LogEvent event : batch) {
                        try {
                            delegate.publish(event);
                            totalPublished.incrementAndGet();
                        } catch (Exception e) {
                            log.debug("[Sentry] 异步日志发送失败: {}", e.getMessage());
                        }
                    }
                    batch.clear();
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            } catch (Exception e) {
                log.debug("[Sentry] 异步日志消费异常: {}", e.getMessage());
                batch.clear();
            }
        }
        log.info("[Sentry] AsyncLogPublisher 消费线程退出, 剩余队列: {}", queue.size());
    }

    /**
     * 令牌桶限流等待
     */
    private void waitForTokens(int needed) {
        refillTokens();
        while (tokensAvailable < needed && running) {
            try {
                Thread.sleep(10);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return;
            }
            refillTokens();
        }
        if (tokensAvailable >= needed) {
            tokensAvailable -= needed;
        }
    }

    private void refillTokens() {
        long now = System.currentTimeMillis();
        long elapsed = now - lastTokenRefillTime;
        if (elapsed >= 1000) {
            long refill = (elapsed / 1000) * maxRatePerSecond;
            tokensAvailable = Math.min(tokensAvailable + refill, maxRatePerSecond);
            lastTokenRefillTime = now;
        }
    }

    @Override
    public boolean isAvailable() {
        return running && delegate.isAvailable();
    }

    @Override
    public String getName() {
        return "async-" + delegate.getName();
    }

    @Override
    public String getScheme() {
        return delegate.getScheme();
    }

    /**
     * 获取丢弃的日志数
     */
    public long getDroppedCount() {
        return droppedCount.get();
    }

    /**
     * 获取已发布的日志数
     */
    public long getTotalPublished() {
        return totalPublished.get();
    }

    /**
     * 获取当前队列积压数
     */
    public int getQueueSize() {
        return queue.size();
    }

    /**
     * 获取被包装的发布器
     */
    public LogPublisher getDelegate() {
        return delegate;
    }

    @Override
    public void close() {
        running = false;
        consumerThread.interrupt();
        try {
            consumerThread.join(5000);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        if (delegate instanceof AutoCloseable closeable) {
            try {
                closeable.close();
            } catch (Exception e) {
                log.debug("[Sentry] 委托发布器关闭异常: {}", e.getMessage());
            }
        }
        log.info("[Sentry] AsyncLogPublisher 已关闭, 丢弃总数={}, 发布总数={}",
                droppedCount.get(), totalPublished.get());
    }
}
