package com.remisoft.common.audit.core;

import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Collectors;

import javax.sql.DataSource;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.PreparedStatementSetter;
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
import com.remisoft.common.audit.config.AuditProperties;
import com.remisoft.common.audit.domain.AuditLog;
import com.remisoft.common.audit.sharding.TableShardingStrategy;

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
 *   <li>支持分表策略写入</li>
 *   <li>优雅停机时自动刷新剩余日志</li>
 *   <li>批量写入失败时磁盘兜底，不丢失审计日志</li>
 *   <li>队列满计数监控指标</li>
 *   <li>WaitStrategy 可配置（blocking / sleeping / yielding）</li>
 * </ul>
 *
 * @author remi-team
 * @since 1.0.0
 */
public class DisruptorAuditRecorder implements AuditRecorder, DisposableBean {

    private static final Logger log = LoggerFactory.getLogger(DisruptorAuditRecorder.class);

    /** 批量写入分片大小（每批最多 500 条） */
    private static final int BATCH_SLICE_SIZE = 500;

    /** 优雅停机超时时间（秒） */
    private static final long SHUTDOWN_TIMEOUT_SECONDS = 30;

    /** 默认基础表名 */
    private static final String BASE_TABLE_NAME = "sys_audit_log";

    /** INSERT 语句列定义 */
    private static final String INSERT_COLUMNS =
            "(id, audit_type, action, status, module, content, " +
            "business_no, operator_id, operator_code, operator_name, ip_address, ip_location, " +
            "user_agent, request_params, response_result, error_message, cost_time, " +
            "app_id, app_code, app_name, extra_info, operation_time, created_at)";

    /** INSERT 语句占位符值 */
    private static final String INSERT_VALUES =
            "(?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";

    /** Disruptor 实例 */
    private final Disruptor<AuditLogEvent> disruptor;

    /** RingBuffer 引用 */
    private final RingBuffer<AuditLogEvent> ringBuffer;

    /** JDBC 模板 */
    private final JdbcTemplate jdbcTemplate;

    /** 异步批量写入配置 */
    private final AuditProperties.AsyncProperties asyncProps;

    /** 分表策略 */
    private final TableShardingStrategy shardingStrategy;

    /** 基础表名 */
    private final String baseTableName;

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
     * @param dataSource       数据源
     * @param properties       审计配置属性
     * @param shardingStrategy 分表策略（可为 null）
     * @param baseTableName    基础表名
     */
    public DisruptorAuditRecorder(DataSource dataSource, AuditProperties properties,
                                   TableShardingStrategy shardingStrategy, String baseTableName) {
        this(dataSource, properties, shardingStrategy, baseTableName, "blocking");
    }

    /**
     * 构造函数 — 支持自定义 WaitStrategy
     *
     * @param dataSource       数据源
     * @param properties       审计配置属性
     * @param shardingStrategy 分表策略（可为 null）
     * @param baseTableName    基础表名
     * @param waitStrategyName  等待策略名称（blocking / sleeping / yielding）
     */
    public DisruptorAuditRecorder(DataSource dataSource, AuditProperties properties,
                                   TableShardingStrategy shardingStrategy, String baseTableName,
                                   String waitStrategyName) {
        Objects.requireNonNull(dataSource, "DataSource must not be null");
        Objects.requireNonNull(properties, "AuditProperties must not be null");
        this.jdbcTemplate = new JdbcTemplate(dataSource);
        this.asyncProps = properties.getAsync();
        this.shardingStrategy = shardingStrategy;
        this.baseTableName = baseTableName != null ? baseTableName : BASE_TABLE_NAME;
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

        log.info("【Disruptor审计记录器】启动成功, RingBuffer容量={}, 批量阈值={}, 分表策略={}, WaitStrategy={}",
                asyncProps.getExecutorQueueCapacity(), asyncProps.getBatchSize(),
                shardingStrategy != null ? shardingStrategy.getShardType() : "DISABLED", waitStrategyName);
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
                saveSingle(auditLog);
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
            if (shardingStrategy != null) {
                flushBatchWithSharding(batch);
            } else {
                for (int offset = 0; offset < total; offset += BATCH_SLICE_SIZE) {
                    int end = Math.min(offset + BATCH_SLICE_SIZE, total);
                    List<AuditLog> slice = batch.subList(offset, end);
                    saveBatchDirect(slice);
                }
            }
            successCount.addAndGet(total);
            log.debug("【Disruptor审计记录器】批量写入成功, total={}", total);
        } catch (Exception e) {
            failureCount.addAndGet(total);
            log.error("【Disruptor审计记录器】批量写入失败, count={}, 尝试磁盘兜底", total, e);
            fallbackWriter.writeBatchToFallback(batch);
        }
    }

    /**
     * 分表模式批量写入
     */
    private void flushBatchWithSharding(List<AuditLog> batch) {
        Map<String, List<AuditLog>> grouped = batch.stream()
                .collect(Collectors.groupingBy(this::resolveTableName));

        for (Map.Entry<String, List<AuditLog>> entry : grouped.entrySet()) {
            List<AuditLog> slice = entry.getValue();
            for (int offset = 0; offset < slice.size(); offset += BATCH_SLICE_SIZE) {
                int end = Math.min(offset + BATCH_SLICE_SIZE, slice.size());
                List<AuditLog> subSlice = slice.subList(offset, end);
                saveBatchDirectToTable(entry.getKey(), subSlice);
            }
        }
    }

    /**
     * 解析目标表名
     */
    private String resolveTableName(AuditLog auditLog) {
        if (shardingStrategy == null) {
            return baseTableName;
        }
        LocalDateTime time = auditLog.getOperationTime();
        if (time == null) {
            time = auditLog.getCreatedAt();
        }
        if (time == null) {
            time = LocalDateTime.now();
        }
        return shardingStrategy.getTableName(baseTableName, time);
    }

    /**
     * 构建 INSERT SQL
     */
    private String buildInsertSql(String tableName) {
        return "INSERT INTO " + tableName + " " + INSERT_COLUMNS + " VALUES " + INSERT_VALUES;
    }

    /**
     * 直接批量保存
     */
    private void saveBatchDirect(List<AuditLog> auditLogs) {
        saveBatchDirectToTable(baseTableName, auditLogs);
    }

    /**
     * 直接批量保存到指定表
     */
    private void saveBatchDirectToTable(String tableName, List<AuditLog> auditLogs) {
        if (auditLogs == null || auditLogs.isEmpty()) {
            return;
        }

        String sql = buildInsertSql(tableName);

        int total = auditLogs.size();
        for (int offset = 0; offset < total; offset += BATCH_SLICE_SIZE) {
            int end = Math.min(offset + BATCH_SLICE_SIZE, total);
            List<AuditLog> slice = auditLogs.subList(offset, end);

            jdbcTemplate.batchUpdate(sql, slice, slice.size(),
                    (ps, auditLog) -> setPreparedStatementParams(ps, auditLog));
        }
    }

    /**
     * 保存单条审计日志
     */
    private void saveSingle(AuditLog auditLog) {
        String tableName = resolveTableName(auditLog);
        String sql = buildInsertSql(tableName);
        jdbcTemplate.update(sql, createPreparedStatementSetter(auditLog));
    }

    /**
     * 创建 PreparedStatement 设置器
     */
    private PreparedStatementSetter createPreparedStatementSetter(AuditLog auditLog) {
        return ps -> setPreparedStatementParams(ps, auditLog);
    }

    /**
     * 设置 PreparedStatement 参数
     */
    private void setPreparedStatementParams(PreparedStatement ps, AuditLog auditLog) throws SQLException {
        int i = 1;
        ps.setString(i++, auditLog.getId());
        ps.setObject(i++, auditLog.getAuditType());
        ps.setObject(i++, auditLog.getAction());
        ps.setObject(i++, auditLog.getStatus());
        ps.setString(i++, auditLog.getModule());
        ps.setString(i++, auditLog.getContent());
        ps.setString(i++, auditLog.getBusinessNo());
        ps.setString(i++, auditLog.getOperatorId());
        ps.setString(i++, auditLog.getOperatorCode());
        ps.setString(i++, auditLog.getOperatorName());
        ps.setString(i++, auditLog.getIpAddress());
        ps.setString(i++, auditLog.getIpLocation());
        ps.setString(i++, auditLog.getUserAgent());
        ps.setString(i++, auditLog.getRequestParams());
        ps.setString(i++, auditLog.getResponseResult());
        ps.setString(i++, auditLog.getErrorMessage());
        ps.setObject(i++, auditLog.getCostTime());
        ps.setString(i++, auditLog.getAppId());
        ps.setString(i++, auditLog.getAppCode());
        ps.setString(i++, auditLog.getAppName());
        ps.setString(i++, auditLog.getExtraInfo());
        ps.setTimestamp(i++, auditLog.getOperationTime() != null
                ? Timestamp.valueOf(auditLog.getOperationTime()) : new Timestamp(System.currentTimeMillis()));
        ps.setTimestamp(i, auditLog.getCreatedAt() != null
                ? Timestamp.valueOf(auditLog.getCreatedAt()) : new Timestamp(System.currentTimeMillis()));
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
