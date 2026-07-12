package com.njydsz.pmis.common.audit;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 高性能审计日志异步写入器
 *
 * <p>使用无锁队列 + 定时批量消费，避免并发 IO 瓶颈。
 *
 * <h3>性能特征</h3>
 * <ul>
 *   <li>ConcurrentLinkedQueue 无锁队列</li>
 *   <li>每 1 秒或每 100 条批量写入</li>
 *   <li>队列满时丢弃最旧记录（防 OOM）</li>
 *   <li>单线程消费，避免数据库连接竞争</li>
 * </ul>
 *
 * @author ydsz-pmis-team
 * @since 1.5.0
 */
@Slf4j
@Component
public class AsyncAuditAppender {

    private static final int MAX_QUEUE_SIZE = 10_000;
    private static final int BATCH_SIZE = 100;
    private static final long FLUSH_INTERVAL_SECONDS = 1;

    private final ConcurrentLinkedQueue<AuditEvent> queue = new ConcurrentLinkedQueue<>();
    private final AtomicLong droppedCount = new AtomicLong(0);
    private final AtomicLong totalCount = new AtomicLong(0);
    private ScheduledExecutorService scheduler;

    private final AuditLogWriter auditLogWriter;

    @Autowired
    public AsyncAuditAppender(@Autowired(required = false) AuditLogWriter auditLogWriter) {
        this.auditLogWriter = auditLogWriter;
    }

    @PostConstruct
    public void init() {
        scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "pmis-audit-flusher");
            t.setDaemon(true);
            return t;
        });
        scheduler.scheduleAtFixedRate(this::flush, FLUSH_INTERVAL_SECONDS, FLUSH_INTERVAL_SECONDS, TimeUnit.SECONDS);
        log.info("[AsyncAudit] 初始化完成, flushInterval={}s, batchSize={}", FLUSH_INTERVAL_SECONDS, BATCH_SIZE);
    }

    @PreDestroy
    public void shutdown() {
        if (scheduler != null) {
            scheduler.shutdown();
            try {
                scheduler.awaitTermination(5, TimeUnit.SECONDS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
        // 刷入剩余日志
        flush();
        log.info("[AsyncAudit] 已关闭, total={}, dropped={}", totalCount.get(), droppedCount.get());
    }

    /**
     * 异步发布审计日志
     *
     * @param operation 操作类型
     * @param userId    用户 ID
     * @param detail    操作详情
     */
    public void publish(String operation, String userId, String detail) {
        if (queue.size() >= MAX_QUEUE_SIZE) {
            queue.poll();
            long dropped = droppedCount.incrementAndGet();
            if (dropped % 1000 == 1) {
                log.warn("[AsyncAudit] 队列满, 丢弃旧审计日志 count={}", dropped);
            }
        }
        AuditEvent event = new AuditEvent();
        event.setOperation(operation);
        event.setUserId(userId);
        event.setDetail(detail);
        event.setTimestamp(LocalDateTime.now());
        queue.offer(event);
        totalCount.incrementAndGet();
    }

    /**
     * 批量刷新审计日志到存储
     */
    private void flush() {
        if (queue.isEmpty()) return;
        List<AuditEvent> batch = new ArrayList<>(BATCH_SIZE);
        while (!queue.isEmpty() && batch.size() < BATCH_SIZE) {
            AuditEvent event = queue.poll();
            if (event != null) {
                batch.add(event);
            }
        }
        if (batch.isEmpty()) return;

        if (auditLogWriter != null) {
            try {
                auditLogWriter.batchWrite(batch);
            } catch (Exception e) {
                log.error("[AsyncAudit] 批量写入失败: {}", e.getMessage());
            }
        } else {
            batch.forEach(e -> log.info("[AUDIT] op={} user={} detail={}",
                    e.getOperation(), e.getUserId(), e.getDetail()));
        }
    }

    /**
     * 审计事件
     */
    @Data
    public static class AuditEvent {
        private String operation;
        private String userId;
        private String detail;
        private LocalDateTime timestamp;
    }

    /**
     * 审计日志写入器接口（由业务模块实现）
     */
    public interface AuditLogWriter {
        /**
         * 单条写入
         */
        default void write(AuditEvent event) {}

        /**
         * 批量写入
         */
        void batchWrite(List<AuditEvent> events);
    }
}
