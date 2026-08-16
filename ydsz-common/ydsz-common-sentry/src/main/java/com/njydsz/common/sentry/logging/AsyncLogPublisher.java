package com.njydsz.common.sentry.logging;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.TimeUnit;

import com.njydsz.common.sentry.domain.LogEvent;
import com.njydsz.common.sentry.spi.LogPublisher;

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
 *   <li>优雅关闭：{@code close()} 时 drain 剩余队列并 flush</li>
 * </ul>
 *
 * @author ydsz-team
 * @since 1.0.0
 */
@Slf4j
public class AsyncLogPublisher implements LogPublisher, AutoCloseable {

    /** 令牌桶补充周期（毫秒） */
    private static final long TOKEN_REFILL_PERIOD_MILLIS = 1000;

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
    /** 当前可用令牌数 */
    private final AtomicLong availableTokens;
    /** 上次补充时间 */
    private volatile long lastRefillTime;

    public AsyncLogPublisher(LogPublisher delegate, int queueCapacity, int batchSize,
                             long flushIntervalMillis, int maxRatePerSecond) {
        this.delegate = delegate;
        this.queue = new ArrayBlockingQueue<>(queueCapacity);
        this.batchSize = batchSize;
        this.flushIntervalMillis = flushIntervalMillis;
        this.maxRatePerSecond = maxRatePerSecond;
        this.availableTokens = new AtomicLong(maxRatePerSecond);
        this.lastRefillTime = System.currentTimeMillis();

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
     * 消费循环：批量出队 + 批量推送（委托 delegate.publishBatch 实现真正批量 HTTP）
     */
    private void consumeLoop() {
        List<LogEvent> batch = new ArrayList<>(batchSize);
        while (running || !queue.isEmpty()) {
            try {
                // 阻塞等待第一条
                LogEvent first = queue.poll(flushIntervalMillis, TimeUnit.MILLISECONDS);
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
                    try {
                        if (delegate.publishBatch(batch)) {
                            totalPublished.addAndGet(batch.size());
                        }
                    } catch (Exception e) {
                        log.debug("[Sentry] 异步日志批量发送失败: {}", e.getMessage());
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
     * 令牌桶限流等待。
     *
     * <p>采用配额借贷模式：允许短期突发超过速率上限（透支下一周期配额），
     * 避免消费线程自旋阻塞；同时设置最大等待时间防止无限等待。
     *
     * @param needed 所需令牌数
     */
    private void waitForTokens(int needed) {
        long maxWaitMillis = TOKEN_REFILL_PERIOD_MILLIS * 2;
        long deadline = System.currentTimeMillis() + maxWaitMillis;
        while (running) {
            refillTokens();
            long current = availableTokens.get();
            if (current >= needed) {
                if (availableTokens.compareAndSet(current, current - needed)) {
                    return;
                }
                // CAS 失败说明令牌被其他线程取走，继续重试
                continue;
            }
            // 配额不足，计算还需等待的时间并阻塞等待
            long waitTime = TOKEN_REFILL_PERIOD_MILLIS / maxRatePerSecond;
            if (waitTime <= 0) {
                waitTime = 1;
            }
            if (System.currentTimeMillis() + waitTime > deadline) {
                // 超过最大等待时间，允许透支发送
                availableTokens.addAndGet(-needed);
                return;
            }
            try {
                TimeUnit.MILLISECONDS.sleep(waitTime);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return;
            }
        }
    }

    /**
     * 补充令牌：按经过的时间计算应补充的数量
     */
    private void refillTokens() {
        long now = System.currentTimeMillis();
        long elapsed = now - lastRefillTime;
        if (elapsed >= TOKEN_REFILL_PERIOD_MILLIS) {
            long periods = elapsed / TOKEN_REFILL_PERIOD_MILLIS;
            long toAdd = periods * maxRatePerSecond;
            long newTokens = Math.min(availableTokens.get() + toAdd, maxRatePerSecond);
            availableTokens.set(newTokens);
            lastRefillTime += periods * TOKEN_REFILL_PERIOD_MILLIS;
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
     *
     * @return 累计丢弃数量
     */
    public long getDroppedCount() {
        return droppedCount.get();
    }

    /**
     * 获取已发布的日志数
     *
     * @return 累计发布数量
     */
    public long getTotalPublished() {
        return totalPublished.get();
    }

    /**
     * 获取当前队列积压数
     *
     * @return 队列大小
     */
    public int getQueueSize() {
        return queue.size();
    }

    /**
     * 获取被包装的发布器
     *
     * @return 委托发布器
     */
    public LogPublisher getDelegate() {
        return delegate;
    }

    /**
     * 关闭发布器，drain 剩余队列并 flush。
     *
     * <p>先标记停止标志，中断消费线程，然后由当前线程（通常为容器 shutdown hook）
     * 将队列剩余日志全部 drain 并发送，确保缓冲中的日志不丢失。
     */
    @Override
    public void close() {
        running = false;
        consumerThread.interrupt();
        try {
            consumerThread.join(2000);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        // drain 剩余队列
        List<LogEvent> remaining = new ArrayList<>();
        queue.drainTo(remaining);
        if (!remaining.isEmpty()) {
            log.info("[Sentry] AsyncLogPublisher 关闭中，drain 剩余 {} 条日志", remaining.size());
            try {
                if (delegate.publishBatch(remaining)) {
                    totalPublished.addAndGet(remaining.size());
                }
            } catch (Exception e) {
                log.debug("[Sentry] drain 日志发送失败: {}", e.getMessage());
            }
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
