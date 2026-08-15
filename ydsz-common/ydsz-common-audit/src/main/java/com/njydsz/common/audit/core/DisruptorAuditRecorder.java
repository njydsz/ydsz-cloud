package com.njydsz.common.audit.core;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.scheduling.concurrent.CustomizableThreadFactory;

import com.lmax.disruptor.BlockingWaitStrategy;
import com.lmax.disruptor.EventFactory;
import com.lmax.disruptor.EventHandler;
import com.lmax.disruptor.RingBuffer;
import com.lmax.disruptor.SleepingWaitStrategy;
import com.lmax.disruptor.WaitStrategy;
import com.lmax.disruptor.YieldingWaitStrategy;
import com.lmax.disruptor.dsl.Disruptor;
import com.lmax.disruptor.dsl.ProducerType;
import com.njydsz.common.audit.config.AuditProperties;
import com.njydsz.common.audit.domain.AuditLog;

/**
 * 基于 LMAX Disruptor 的高性能异步审计记录器
 *
 * <p>使用 Disruptor RingBuffer 作为缓冲队列，相比 LinkedBlockingQueue 具有更高的吞吐量：
 * <ul>
 *   <li>无锁设计：使用 CAS 操作，避免线程竞争</li>
 *   <li>预分配内存：RingBuffer 预先分配固定大小内存，避免 GC 压力</li>
 *   <li>批量处理：支持批量消费，减少数据库往返</li>
 *   <li>低延迟：单线程生产/消费场景下可达微秒级延迟</li>
 * </ul>
 *
 * <p>特性：
 * <ul>
 *   <li>支持多生产者并发写入（ProducerType.MULTI）</li>
 *   <li>支持批量消费（batch handler）</li>
 *   <li>写入操作委托给 {@link AuditWriter}，支持 JDBC、消息队列等多种后端</li>
 *   <li>优雅停机时自动刷新剩余日志</li>
 *   <li>批量写入失败时磁盘兜底，不丢失审计日志</li>
 *   <li>队列满计数监控指标</li>
 *   <li>WaitStrategy 可配置（blocking / sleeping / yielding）</li>
 * </ul>
 *
 * @author ydsz-team
 * @since 1.0.0
 *
 */
public class DisruptorAuditRecorder implements AuditRecorder, DisposableBean {

    private static final Logger log = LoggerFactory.getLogger(DisruptorAuditRecorder.class);

    /** 优雅停机超时时间（秒） */
    private static final long SHUTDOWN_TIMEOUT_SECONDS = 30;

    /** Disruptor 实例 */
    private final Disruptor<AuditLogEvent> disruptor;

    /** RingBuffer 引用 */
    private final RingBuffer<AuditLogEvent> ringBuffer;

    /** 审计写入器，负责实际的持久化操作 */
    private final AuditWriter auditWriter;

    /** 异步批量写入配置 */
    private final AuditProperties.AsyncProperties asyncProps;

    /** 运行状态标志 */
    private final AtomicBoolean running = new AtomicBoolean(true);

    /** 批量消费缓冲区 */
    private final List<AuditLog> batchBuffer;

    /** 上次刷新时间戳 */
    private volatile long lastFlushTime = System.currentTimeMillis();

    /** 磁盘兜底写入器 */
    private final AuditFallbackWriter fallbackWriter = new AuditFallbackWriter();

    /** 队列满告警计数 */
    private final AtomicLong queueFullWarnCount = new AtomicLong(0);

    /** 累计写入成功计数 */
    private final AtomicLong successCount = new AtomicLong(0);

    /** 累计写入失败计数 */
    private final AtomicLong failureCount = new AtomicLong(0);

    /**
     * 构造函数（使用默认 BlockingWaitStrategy）
     *
     * @param auditWriter 审计写入器
     * @param properties  审计配置属性
     */
    public DisruptorAuditRecorder(AuditWriter auditWriter, AuditProperties properties) {
        this(auditWriter, properties, "blocking");
    }

    /**
     * 构造函数 — 支持自定义 WaitStrategy
     *
     * @param auditWriter      审计写入器
     * @param properties       审计配置属性
     * @param waitStrategyName 等待策略名称（blocking / sleeping / yielding）
     */
    public DisruptorAuditRecorder(AuditWriter auditWriter, AuditProperties properties, String waitStrategyName) {
        Objects.requireNonNull(auditWriter, "AuditWriter must not be null");
        Objects.requireNonNull(properties, "AuditProperties must not be null");
        this.auditWriter = auditWriter;
        this.asyncProps = properties.getAsync();
        this.batchBuffer = new ArrayList<>(asyncProps.getBatchSize());

        // 创建线程工厂
        ThreadFactory threadFactory = new CustomizableThreadFactory("audit-disruptor-");

        // 根据配置选择 WaitStrategy
        WaitStrategy waitStrategy = resolveWaitStrategy(waitStrategyName);

        // 创建 Disruptor
        this.disruptor = new Disruptor<>(
                AuditLogEvent.FACTORY,
                asyncProps.getExecutorQueueCapacity(),
                threadFactory,
                ProducerType.MULTI,
                waitStrategy
        );

        // 设置批量事件处理器
        disruptor.handleEventsWith(new BatchEventHandler());

        // 启动 Disruptor
        disruptor.start();
        this.ringBuffer = disruptor.getRingBuffer();

        log.info("【Disruptor审计记录器】启动成功, RingBuffer容量={}, 批量阈值={}, WaitStrategy={}, 写入器={}",
                asyncProps.getExecutorQueueCapacity(), asyncProps.getBatchSize(),
                waitStrategyName, auditWriter.getName());
    }

    /**
     * 根据策略名称解析 WaitStrategy
     *
     * @param name 策略名称（blocking / sleeping / yielding）
     * @return WaitStrategy 实例
     */
    private WaitStrategy resolveWaitStrategy(String name) {
        if (name == null || name.isEmpty()) {
            return new BlockingWaitStrategy();
        }
        return switch (name.toLowerCase()) {
            case "sleeping" -> new SleepingWaitStrategy();
            case "yielding" -> new YieldingWaitStrategy();
            default -> new BlockingWaitStrategy();
        };
    }

    @Override
    public void record(AuditLog auditLog) {
        recordAsync(auditLog);
    }

    @Override
    public void recordAsync(AuditLog auditLog) {
        if (auditLog == null) {
            log.warn("【Disruptor审计记录器】审计日志为空, 跳过记录");
            return;
        }

        if (!running.get()) {
            log.warn("【Disruptor审计记录器】记录器已停止, 尝试同步写入");
            try {
                auditWriter.write(auditLog);
            } catch (Exception e) {
                log.error("【Disruptor审计记录器】同步写入失败", e);
                fallbackWriter.writeToFallback(auditLog);
            }
            return;
        }

        // 发布事件到 RingBuffer
        long sequence = ringBuffer.next();
        try {
            AuditLogEvent event = ringBuffer.get(sequence);
            event.setAuditLog(auditLog);
        } finally {
            ringBuffer.publish(sequence);
        }
    }

    @Override
    public void recordBatch(List<AuditLog> auditLogs) {
        if (auditLogs == null || auditLogs.isEmpty()) {
            return;
        }

        for (AuditLog auditLog : auditLogs) {
            if (auditLog != null) {
                recordAsync(auditLog);
            }
        }
    }

    @Override
    public String getName() {
        return "DisruptorAuditRecorder";
    }

    /**
     * 获取队列满告警累计次数
     *
     * @return 队列满告警累计触发次数
     */
    public long getQueueFullWarnCount() {
        return queueFullWarnCount.get();
    }

    /**
     * 获取累计写入成功次数
     *
     * @return 成功次数
     */
    public long getSuccessCount() {
        return successCount.get();
    }

    /**
     * 获取累计写入失败次数
     *
     * @return 失败次数
     */
    public long getFailureCount() {
        return failureCount.get();
    }

    /**
     * 批量事件处理器
     */
    private class BatchEventHandler implements EventHandler<AuditLogEvent> {

        @Override
        public void onEvent(AuditLogEvent event, long sequence, boolean endOfBatch) {
            AuditLog auditLog = event.getAuditLog();
            if (auditLog == null) {
                return;
            }

            batchBuffer.add(auditLog);

            // 检查是否需要刷新
            boolean shouldFlush = batchBuffer.size() >= asyncProps.getBatchSize()
                    || (endOfBatch && !batchBuffer.isEmpty())
                    || (System.currentTimeMillis() - lastFlushTime) >= asyncProps.getBatchIntervalMillis();

            if (shouldFlush) {
                flushBatch();
            }
        }
    }

    /**
     * 批量写入数据库
     */
    private void flushBatch() {
        if (batchBuffer.isEmpty()) {
            return;
        }

        List<AuditLog> batch = new ArrayList<>(batchBuffer);
        batchBuffer.clear();
        lastFlushTime = System.currentTimeMillis();

        int total = batch.size();

        try {
            auditWriter.writeBatch(batch);
            successCount.addAndGet(total);
            log.debug("【Disruptor审计记录器】批量写入成功, total={}", total);
        } catch (Exception e) {
            failureCount.addAndGet(total);
            log.error("【Disruptor审计记录器】批量写入失败, count={}, 尝试磁盘兜底", total, e);
            fallbackWriter.writeBatchToFallback(batch);
        }
    }

    /**
     * 优雅停机
     */
    public void shutdown() {
        if (!running.compareAndSet(true, false)) {
            log.warn("【Disruptor审计记录器】记录器已处于停止状态");
            return;
        }

        log.info("【Disruptor审计记录器】开始优雅停机...");

        try {
            // 等待 RingBuffer 中所有事件被处理
            disruptor.shutdown(SHUTDOWN_TIMEOUT_SECONDS, TimeUnit.SECONDS);

            // 刷新剩余的批量缓冲区
            flushBatch();

            log.info("【Disruptor审计记录器】优雅停机完成, 累计成功={}, 累计失败={}",
                    successCount.get(), failureCount.get());
        } catch (Exception e) {
            log.error("【Disruptor审计记录器】优雅停机失败", e);
            disruptor.halt();
        }
    }

    @Override
    public void destroy() throws Exception {
        shutdown();
    }

    /**
     * Disruptor 事件载体
     */
    public static class AuditLogEvent {

        private AuditLog auditLog;

        public AuditLog getAuditLog() {
            return auditLog;
        }

        public void setAuditLog(AuditLog auditLog) {
            this.auditLog = auditLog;
        }

        /**
         * 清空事件承载的审计日志引用。
         *
         * <p>Disruptor 事件对象为 RingBuffer 中预分配并循环复用的槽位，消费后必须释放对
         * {@link AuditLog} 的引用，避免大对象被槽位长期持有导致内存泄漏（GC 无法回收）。
         */
        public void clear() {
            this.auditLog = null;
        }

        /** 事件工厂 */
        public static final EventFactory<AuditLogEvent> FACTORY = AuditLogEvent::new;
    }
}
