package com.njydsz.pmis.common.audit.core;

import com.njydsz.pmis.common.audit.config.AuditProperties;
import com.njydsz.pmis.common.audit.domain.AuditLog;
import com.njydsz.pmis.common.audit.sharding.TableShardingStrategy;
import com.njydsz.pmis.common.util.concurrent.ExecutorUtils;
import com.njydsz.pmis.common.util.json.JsonUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.DisposableBean;

import javax.sql.DataSource;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * 异步批量审计记录器
 *
 * <p>使用 LinkedBlockingQueue 作为缓冲队列，通过 ScheduledExecutorService 定时批量写入数据库。
 * 相比同步写入，显著降低对主业务的性能影响。
 *
 * <p>特性：
 * <ul>
 *   <li>基于 LinkedBlockingQueue 的高性能缓冲队列（有界队列，支持背压控制）</li>
 *   <li>使用 ScheduledExecutorService 定时刷新，支持按阈值刷新和定时刷新双机制</li>
 *   <li>使用 JdbcTemplate.batchUpdate() 批量插入，减少数据库往返</li>
 *   <li>优雅停机时自动将队列剩余日志全部写入（通过 DisposableBean 接口）</li>
 *   <li>写入失败数据不丢失，保留在队列中下次重试</li>
 *   <li>队列满时支持三种拒绝策略：DISCARD_OLDEST（丢弃最旧）、DISCARD_NEWEST（丢弃最新）、CALLER_RUNS（调用者阻塞）</li>
 *   <li>提供队列使用率监控指标，支持背压感知</li>
 *   <li>支持分表策略写入</li>
 * </ul>
 *
 * <p><b>队列满拒绝策略：</b></p>
 * <ul>
 *   <li>DISCARD_OLDEST（默认）：弹出队列中最旧的日志，尝试放入新日志</li>
 *   <li>DISCARD_NEWEST：直接丢弃最新日志</li>
 *   <li>CALLER_RUNS：调用者线程阻塞等待队列有空位（带超时）</li>
 * </ul>
 *
 * @author Marvin Lee
 * @email limw1888@126.com
 * @version 3.5.0
 */
public class AsyncAuditRecorder implements AuditRecorder, DisposableBean {

    private static final Logger log = LoggerFactory.getLogger(AsyncAuditRecorder.class);

    /** 批量写入分片大小（每批最多 500 条） */
    private static final int BATCH_SLICE_SIZE = 500;
    /** 优雅停机超时时间（秒） */
    private static final long SHUTDOWN_TIMEOUT_SECONDS = 30;
    /** CALLER_RUNS 策略阻塞等待超时时间（毫秒） */
    private static final long DEFAULT_BLOCK_TIMEOUT_MS = 3000L;

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

    /** 异步缓冲队列，有界队列支持背压控制 */
    private final LinkedBlockingQueue<AuditLog> queue;
    /** 数据源引用 */
    @SuppressWarnings("unused")
    private final DataSource dataSource;
    /** JDBC 模板，用于批量写入数据库 */
    private final org.springframework.jdbc.core.JdbcTemplate jdbcTemplate;
    /** 审计配置属性 */
    private final AuditProperties properties;
    /** 异步批量写入配置 */
    private final AuditProperties.AsyncProperties asyncProps;
    /** 定时调度线程池，用于周期性刷新队列 */
    private final ScheduledExecutorService scheduler;
    /** 记录器运行状态标志 */
    private final AtomicBoolean running = new AtomicBoolean(true);
    /** 分表策略（可为 null） */
    private final TableShardingStrategy shardingStrategy;
    /** 基础表名 */
    private final String baseTableName;

    /** 磁盘兜底是否已失效标志 */
    private volatile boolean diskFallbackFailed = false;
    /** 磁盘兜底文件目录 */
    private volatile String fallbackDir = System.getProperty("java.io.tmpdir") + "/audit-fallback";

    /** 队列满告警计数 */
    private final AtomicLong queueFullWarnCount = new AtomicLong(0);
    /** 上一次告警日志时间戳（用于节流） */
    private volatile long lastWarnLogTime = 0;
    /** 告警日志节流间隔（毫秒），避免频繁刷日志 */
    private static final long WARN_LOG_THROTTLE_MS = 10_000L;

    /** 防止并发刷新的锁对象 */
    private final Object flushLock = new Object();

    /**
     * 构造函数
     *
     * @param dataSource 数据源
     * @param properties 审计配置属性
     */
    public AsyncAuditRecorder(DataSource dataSource, AuditProperties properties) {
        this(dataSource, properties, null, BASE_TABLE_NAME);
    }

    /**
     * 构造函数 - 支持分表策略
     *
     * @param dataSource        数据源
     * @param properties        审计配置属性
     * @param shardingStrategy  分表策略（可为 null）
     * @param baseTableName     基础表名
     */
    public AsyncAuditRecorder(DataSource dataSource, AuditProperties properties,
                              TableShardingStrategy shardingStrategy, String baseTableName) {
        this.dataSource = Objects.requireNonNull(dataSource, "DataSource must not be null");
        this.properties = Objects.requireNonNull(properties, "AuditProperties must not be null");
        this.jdbcTemplate = new org.springframework.jdbc.core.JdbcTemplate(dataSource);
        this.asyncProps = properties.getAsync();
        this.queue = new LinkedBlockingQueue<>(asyncProps.getQueueCapacity());
        this.scheduler = ExecutorUtils.newScheduledThreadPool(1, "audit-scheduler");
        this.shardingStrategy = shardingStrategy;
        this.baseTableName = baseTableName != null ? baseTableName : BASE_TABLE_NAME;

        // 启动定时刷新任务
        scheduler.scheduleAtFixedRate(
                this::flushFromQueue,
                asyncProps.getBatchIntervalMillis(),
                asyncProps.getBatchIntervalMillis(),
                TimeUnit.MILLISECONDS
        );

        log.info("【异步审计记录器】启动成功, 队列容量={}, 批量阈值={}, 刷新间隔={}ms, 分表策略={}",
                asyncProps.getQueueCapacity(), asyncProps.getBatchSize(), asyncProps.getBatchIntervalMillis(),
                shardingStrategy != null ? shardingStrategy.getShardType() : "DISABLED");
    }

    @Override
    public void record(AuditLog auditLog) {
        recordAsync(auditLog);
    }

    @Override
    public void recordAsync(AuditLog auditLog) {
        if (auditLog == null) {
            log.warn("【异步审计记录器】审计日志为空, 跳过记录");
            return;
        }

        if (!running.get()) {
            log.warn("【异步审计记录器】记录器已停止, 尝试同步写入");
            try {
                saveSingle(auditLog);
            } catch (Exception e) {
                log.error("【异步审计记录器】同步写入失败", e);
            }
            return;
        }

        boolean offered = queue.offer(auditLog);
        if (!offered) {
            handleQueueFull(auditLog);
            return;
        }

        // 当队列大小达到批量阈值时，立即触发一次刷新
        if (queue.size() >= asyncProps.getBatchSize()) {
            triggerFlush();
        }
    }

    /**
     * 触发一次非阻塞刷新（如果当前没有在刷新）
     */
    private void triggerFlush() {
        try {
            scheduler.submit(this::flushFromQueue);
        } catch (RejectedExecutionException e) {
            log.debug("【异步审计记录器】刷新任务已被拒绝，可能调度器已关闭");
        }
    }

    /**
     * 处理队列已满的情况
     *
     * @param auditLog 待写入的审计日志
     */
    private void handleQueueFull(AuditLog auditLog) {
        String strategy = properties.getAsyncRejectPolicy();
        long warnCount = queueFullWarnCount.incrementAndGet();
        logQueueFullWarn(auditLog, strategy, warnCount);

        if ("DISCARD_OLDEST".equalsIgnoreCase(strategy)) {
            queue.poll();
            boolean offered = queue.offer(auditLog);
            if (!offered) {
                log.error("【异步审计记录器】队列已满, 丢弃最旧日志后仍然无法入队, id={}", auditLog.getId());
            }
            return;
        }

        if ("DISCARD_NEWEST".equalsIgnoreCase(strategy)) {
            log.error("【异步审计记录器】队列已满({}), 最新审计日志将被丢弃, id={}",
                    asyncProps.getQueueCapacity(), auditLog.getId());
            return;
        }

        if ("CALLER_RUNS".equalsIgnoreCase(strategy)) {
            try {
                boolean success = queue.offer(auditLog, DEFAULT_BLOCK_TIMEOUT_MS, TimeUnit.MILLISECONDS);
                if (!success) {
                    log.error("【异步审计记录器】阻塞等待超时({}ms), 队列仍未空出位置, 日志将被丢弃, id={}",
                            DEFAULT_BLOCK_TIMEOUT_MS, auditLog.getId());
                    writeToFallback(auditLog);
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                log.warn("【异步审计记录器】阻塞等待被中断, 尝试磁盘兜底, id={}", auditLog.getId());
                writeToFallback(auditLog);
            }
            return;
        }

        log.error("【异步审计记录器】未知队列满策略: {}, 默认丢弃最新日志, id={}", strategy, auditLog.getId());
    }

    /**
     * 队列满告警日志（带节流，避免频繁刷日志）
     */
    private void logQueueFullWarn(AuditLog auditLog, String strategy, long warnCount) {
        long now = System.currentTimeMillis();
        long elapsed = now - lastWarnLogTime;
        if (elapsed >= WARN_LOG_THROTTLE_MS || warnCount <= 5) {
            lastWarnLogTime = now;
            double usageRatio = getQueueUsageRatio();
            log.error("【异步审计记录器】队列已满! 容量={}, 当前={}, 使用率={}%, 策略={}, 累计触发={}",
                    asyncProps.getQueueCapacity(), queue.size(),
                    String.format("%.1f", usageRatio * 100), strategy, warnCount);
        }
    }

    /**
     * 获取队列使用率（队列当前大小 / 容量比率）
     *
     * @return 使用率，范围 [0.0, 1.0]
     */
    public double getQueueUsageRatio() {
        int capacity = asyncProps.getQueueCapacity();
        if (capacity <= 0) {
            return 0.0;
        }
        return (double) queue.size() / capacity;
    }

    /**
     * 获取当前队列大小
     *
     * @return 队列中待写入的审计日志数量
     */
    public int getQueueSize() {
        return queue.size();
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
     * 将审计日志序列化为 JSON 写入本地文件
     *
     * @param auditLog 待写入的审计日志
     */
    private void writeToFallback(AuditLog auditLog) {
        if (diskFallbackFailed) {
            log.error("【异步审计记录器】磁盘兜底已失效, 审计日志将丢失, id={}", auditLog.getId());
            return;
        }

        try {
            Path dir = Paths.get(fallbackDir);
            if (!Files.exists(dir)) {
                Files.createDirectories(dir);
            }

            String dateStr = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd"));
            Path logFile = dir.resolve("audit_fallback_" + dateStr + ".json");

            String jsonLine = JsonUtils.toJson(auditLog) + "\n";

            Files.write(logFile, jsonLine.getBytes(), StandardOpenOption.CREATE, StandardOpenOption.APPEND);

            log.warn("【异步审计记录器】队列已满, 审计日志已写入磁盘兜底, file={}, id={}", logFile, auditLog.getId());
        } catch (IOException e) {
            diskFallbackFailed = true;
            log.error("【异步审计记录器】磁盘兜底写入失败, 审计日志将丢失, id={}, error={}", auditLog.getId(), e.getMessage(), e);
        }
    }

    /**
     * 从磁盘恢复审计日志到队列
     *
     * <p>扫描 fallbackDir 下的 JSON 文件，逐行读取并反序列化为 AuditLog，
     * 在队列有空间时将日志重新放入队列。已成功恢复的文件会被删除。
     */
    public void recoverFromFallback() {
        Path dir = Paths.get(fallbackDir);
        if (!Files.exists(dir) || !Files.isDirectory(dir)) {
            return;
        }

        try {
            List<Path> fallbackFiles;
            try (Stream<Path> stream = Files.list(dir)) {
                fallbackFiles = stream
                        .filter(p -> p.getFileName().toString().startsWith("audit_fallback_") &&
                                p.getFileName().toString().endsWith(".json"))
                        .sorted()
                        .collect(Collectors.toList());
            }

            if (fallbackFiles.isEmpty()) {
                return;
            }

            log.info("【异步审计记录器】开始恢复磁盘兜底日志, 文件数={}", fallbackFiles.size());

            for (Path file : fallbackFiles) {
                List<String> lines = Files.readAllLines(file);
                int recovered = 0;
                int failed = 0;

                for (String line : lines) {
                    if (line == null || line.trim().isEmpty()) {
                        continue;
                    }

                    try {
                        AuditLog auditLog = JsonUtils.fromJson(line.trim(), AuditLog.class);
                        if (auditLog != null) {
                            boolean offered = queue.offer(auditLog);
                            if (offered) {
                                recovered++;
                            } else {
                                log.warn("【异步审计记录器】恢复时队列已满, 停止恢复, file={}", file);
                                break;
                            }
                        }
                    } catch (Exception e) {
                        failed++;
                        log.warn("【异步审计记录器】恢复日志行失败, file={}, error={}", file, e.getMessage());
                    }
                }

                try {
                    Files.delete(file);
                    log.info("【异步审计记录器】磁盘兜底文件已恢复并删除, file={}, recovered={}, failed={}",
                            file, recovered, failed);
                } catch (IOException e) {
                    log.warn("【异步审计记录器】删除磁盘兜底文件失败, file={}, error={}", file, e.getMessage(), e);
                }
            }

            diskFallbackFailed = false;
        } catch (IOException e) {
            log.error("【异步审计记录器】扫描磁盘兜底目录失败, dir={}", fallbackDir, e);
        }
    }

    @Override
    public void recordBatch(List<AuditLog> auditLogs) {
        if (auditLogs == null || auditLogs.isEmpty()) {
            return;
        }

        if (!running.get()) {
            log.warn("【异步审计记录器】记录器已停止, 尝试同步批量写入, count={}", auditLogs.size());
            try {
                saveBatchDirect(auditLogs);
            } catch (Exception e) {
                log.error("【异步审计记录器】同步批量写入失败", e);
            }
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
        return "AsyncAuditRecorder";
    }

    /**
     * 设置队列满时的兜底策略
     *
     * @param strategy DISCARD_OLDEST（丢弃最旧）| DISCARD_NEWEST（丢弃最新）| CALLER_RUNS（调用者阻塞）
     */
    public void setRejectPolicy(String strategy) {
        if (strategy == null || strategy.isEmpty()) {
            throw new IllegalArgumentException("策略不能为空");
        }
        String upper = strategy.toUpperCase();
        if (!"DISCARD_OLDEST".equals(upper) && !"DISCARD_NEWEST".equals(upper) && !"CALLER_RUNS".equals(upper)) {
            throw new IllegalArgumentException("策略必须为 DISCARD_OLDEST、DISCARD_NEWEST 或 CALLER_RUNS");
        }
        properties.setAsyncRejectPolicy(upper);
        log.info("【异步审计记录器】队列满策略已设置为: {}", upper);
    }

    /**
     * 设置磁盘兜底路径
     *
     * @param path 磁盘文件路径
     */
    public void setDiskFallbackPath(String path) {
        this.fallbackDir = path;
        this.diskFallbackFailed = false;
        log.info("【异步审计记录器】磁盘兜底路径已设置为: {}", path);
    }

    /**
     * 从队列中批量取出审计日志并写入数据库。
     * 此方法由定时调度任务和队列满阈值触发调用。
     */
    private void flushFromQueue() {
        if (!running.get()) {
            return;
        }

        // 防止并发刷新
        synchronized (flushLock) {
            List<AuditLog> batch = new ArrayList<>(asyncProps.getBatchSize());
            queue.drainTo(batch, asyncProps.getBatchSize());

            if (batch.isEmpty()) {
                return;
            }

            flushBatch(batch);
        }
    }

    /**
     * 批量写入数据库
     *
     * @param batch 待写入的审计日志列表
     */
    private void flushBatch(List<AuditLog> batch) {
        if (batch == null || batch.isEmpty()) {
            return;
        }

        int total = batch.size();
        int successCount = 0;

        try {
            if (shardingStrategy != null) {
                flushBatchWithSharding(batch);
            } else {
                for (int offset = 0; offset < total; offset += BATCH_SLICE_SIZE) {
                    int end = Math.min(offset + BATCH_SLICE_SIZE, total);
                    List<AuditLog> slice = batch.subList(offset, end);
                    saveBatchDirect(slice);
                    successCount += slice.size();
                }
            }
            log.debug("【异步审计记录器】批量写入成功, total={}, success={}", total, successCount);
        } catch (Exception e) {
            log.warn("【异步审计记录器】批量写入失败, count={}, 尝试磁盘兜底写入", total, e);
            writeBatchToFallback(batch);
        }
    }

    /**
     * 批量写入失败时，将失败的批次写入磁盘兜底文件；
     * 若磁盘兜底也已失效，则尝试将日志重新放回队列尾部，避免审计日志永久丢失。
     *
     * @param batch 写入失败的审计日志批次
     */
    private void writeBatchToFallback(List<AuditLog> batch) {
        // 磁盘兜底未失效时，尝试逐条写入磁盘兜底文件
        if (!diskFallbackFailed) {
            for (AuditLog auditLog : batch) {
                writeToFallback(auditLog);
                // writeToFallback 内部写入失败时会将 diskFallbackFailed 置为 true
                if (diskFallbackFailed) {
                    break;
                }
            }
        }

        // 磁盘兜底已失效，尝试将日志重新放回队列尾部，避免数据永久丢失
        if (diskFallbackFailed) {
            log.warn("【异步审计记录器】磁盘兜底已失效, 尝试将失败批次重新放回队列尾部, count={}", batch.size());
            for (AuditLog auditLog : batch) {
                boolean offered = queue.offer(auditLog);
                if (!offered) {
                    log.error("【异步审计记录器】磁盘兜底失效且队列已满, 审计日志将丢失, id={}", auditLog.getId());
                }
            }
        }
    }

    /**
     * 分表模式批量写入：按分表分组后分别写入
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
     * 根据审计日志解析目标表名
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
     * 直接批量保存（不经过队列）
     *
     * @param auditLogs 审计日志列表
     */
    private void saveBatchDirect(List<AuditLog> auditLogs) {
        saveBatchDirectToTable(baseTableName, auditLogs);
    }

    /**
     * 直接批量保存到指定表
     *
     * @param tableName 表名
     * @param auditLogs 审计日志列表
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
     *
     * @param auditLog 审计日志
     */
    private void saveSingle(AuditLog auditLog) {
        String tableName = resolveTableName(auditLog);
        String sql = buildInsertSql(tableName);
        jdbcTemplate.update(sql, createPreparedStatementSetter(auditLog));
    }

    /**
     * 创建 PreparedStatement 设置器
     *
     * @param auditLog 审计日志
     * @return PreparedStatementSetter
     */
    private org.springframework.jdbc.core.PreparedStatementSetter createPreparedStatementSetter(AuditLog auditLog) {
        return ps -> setPreparedStatementParams(ps, auditLog);
    }

    /**
     * 设置 PreparedStatement 参数
     *
     * @param ps PreparedStatement
     * @param auditLog 审计日志
     * @throws SQLException SQL 异常
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
     * 停止接收新日志，等待队列中剩余日志全部写入
     */
    public void shutdown() {
        if (!running.compareAndSet(true, false)) {
            log.warn("【异步审计记录器】记录器已处于停止状态");
            return;
        }

        log.info("【异步审计记录器】开始优雅停机...");
        scheduler.shutdown();

        try {
            if (!scheduler.awaitTermination(SHUTDOWN_TIMEOUT_SECONDS, TimeUnit.SECONDS)) {
                log.warn("【异步审计记录器】优雅停机超时({}s), 强制退出", SHUTDOWN_TIMEOUT_SECONDS);
                scheduler.shutdownNow();
            } else {
                // 确保队列中剩余日志全部写入
                flushRemaining();
                log.info("【异步审计记录器】优雅停机完成, 队列剩余日志已全部写入");
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            scheduler.shutdownNow();
            log.error("【异步审计记录器】优雅停机被中断", e);
        }
    }

    /**
     * 写入队列中剩余的日志
     */
    private void flushRemaining() {
        List<AuditLog> remaining = new ArrayList<>();
        queue.drainTo(remaining);
        if (!remaining.isEmpty()) {
            log.info("【异步审计记录器】写入剩余 {} 条审计日志", remaining.size());
            flushBatch(remaining);
        }
    }

    /**
     * 实现 DisposableBean 接口，在 Spring 容器关闭时自动调用
     */
    @Override
    public void destroy() throws Exception {
        shutdown();
    }
}
