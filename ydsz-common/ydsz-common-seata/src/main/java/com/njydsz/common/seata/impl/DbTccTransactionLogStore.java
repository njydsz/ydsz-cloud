package com.njydsz.common.seata.impl;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;

import com.njydsz.common.seata.api.TccBranchStatus;
import com.njydsz.common.seata.api.TccTransactionLog;
import com.njydsz.common.seata.api.TccTransactionLogStore;

/**
 * 基于数据库（JDBC）的 TCC 事务日志存储
 *
 * <p>使用关系型数据库表 {@code tcc_transaction_log} 持久化 TCC 分支事务状态，
 * 适用于以下生产场景：
 * <ul>
 *   <li><b>无 Redis 环境</b>：团队不具备 Redis 基础设施但需要持久化事务日志</li>
 *   <li><b>强持久化需求</b>：事务日志需要落盘且可追溯，满足合规审计要求</li>
 *   <li><b>跨服务共享</b>：多服务实例共享数据库，任一实例均可执行 Confirm/Cancel 恢复</li>
 * </ul>
 *
 * <p><b>表结构 DDL（MySQL / PostgreSQL / H2 兼容）：</b>
 * <pre>{@code
 * CREATE TABLE tcc_transaction_log (
 *   id              BIGINT       AUTO_INCREMENT PRIMARY KEY,
 *   xid             VARCHAR(128) NOT NULL,
 *   branch_id       VARCHAR(128) NOT NULL,
 *   transaction_name VARCHAR(255),
 *   status          VARCHAR(32)  NOT NULL,
 *   context_snapshot TEXT,
 *   try_started_at   TIMESTAMP    NULL,
 *   try_completed_at TIMESTAMP    NULL,
 *   finished_at      TIMESTAMP    NULL,
 *   retry_count      INT          NOT NULL DEFAULT 0,
 *   last_error       VARCHAR(1024),
 *   created_at       TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
 *   updated_at       TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
 *   UNIQUE KEY uk_xid_branch (xid, branch_id)
 * );
 * CREATE INDEX idx_status_updated ON tcc_transaction_log (status, updated_at);
 * }</pre>
 *
 * <p><b>实现要点：</b>
 * <ul>
 *   <li>使用 INSERT ON DUPLICATE KEY UPDATE（MySQL）或 UPSERT（PG 兼容写法通过先查后插）保证幂等</li>
 *   <li>查询超时记录利用索引 {@code idx_status_updated} 加速扫描</li>
 *   <li>终态日志保留用于审计，需定期清理（建议通过定时任务删除 {@code finished_at < NOW() - 90d} 的日志）</li>
 * </ul>
 *
 * <p><b>配置方式：</b>
 * <pre>{@code
 * ydsz:
 *   seata:
 *     tcc-log-store: db
 *     tcc-log-db-table: tcc_transaction_log
 * }</pre>
 *
 * @author ydsz-team
 * @since 1.2.0
 */
public class DbTccTransactionLogStore implements TccTransactionLogStore {

    private static final Logger log = LoggerFactory.getLogger(DbTccTransactionLogStore.class);

    private final JdbcTemplate jdbcTemplate;
    private final String tableName;

    private static final String SQL_UPSERT = """
            INSERT INTO %s (xid, branch_id, transaction_name, status, context_snapshot,
                            try_started_at, try_completed_at, finished_at, retry_count, last_error,
                            created_at, updated_at)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
            ON DUPLICATE KEY UPDATE
                status = VALUES(status),
                context_snapshot = VALUES(context_snapshot),
                try_started_at = VALUES(try_started_at),
                try_completed_at = VALUES(try_completed_at),
                finished_at = VALUES(finished_at),
                retry_count = VALUES(retry_count),
                last_error = VALUES(last_error),
                updated_at = CURRENT_TIMESTAMP
            """;

    private static final String SQL_UPDATE_STATUS = """
            UPDATE %s SET status = ?, finished_at = ?, updated_at = CURRENT_TIMESTAMP
            WHERE xid = ? AND branch_id = ?
            """;

    private static final String SQL_FIND_BY_XID_BRANCH = """
            SELECT xid, branch_id, transaction_name, status, context_snapshot,
                   try_started_at, try_completed_at, finished_at, retry_count, last_error
            FROM %s WHERE xid = ? AND branch_id = ?
            """;

    private static final String SQL_FIND_TIMEOUT_PENDING = """
            SELECT xid, branch_id, transaction_name, status, context_snapshot,
                   try_started_at, try_completed_at, finished_at, retry_count, last_error
            FROM %s WHERE status = ? AND try_completed_at < ?
            """;

    private static final String SQL_DELETE = """
            DELETE FROM %s WHERE xid = ? AND branch_id = ?
            """;

    private final RowMapper<TccTransactionLog> rowMapper = (rs, rowNum) -> mapRow(rs);

    /**
     * 构造 DB 版 TCC 事务日志存储
     *
     * @param jdbcTemplate JDBC 模板（不能为 null）
     * @param tableName    表名（可为 null，默认 {@code tcc_transaction_log}）
     */
    public DbTccTransactionLogStore(JdbcTemplate jdbcTemplate, String tableName) {
        this.jdbcTemplate = jdbcTemplate;
        this.tableName = (tableName == null || tableName.isBlank()) ? "tcc_transaction_log" : tableName;
    }

    /**
     * 保存事务日志（Try 前调用）
     *
     * <p>使用 INSERT ... ON DUPLICATE KEY UPDATE 保证幂等性，
     * 若已存在该分支记录，则更新状态和时间戳。
     *
     * @param txLog 事务日志
     */
    @Override
    public void save(TccTransactionLog txLog) {
        String sql = String.format(SQL_UPSERT, tableName);
        jdbcTemplate.update(sql,
                txLog.getXid(),
                txLog.getBranchId(),
                txLog.getTransactionName(),
                txLog.getStatus() == null ? TccBranchStatus.INIT.name() : txLog.getStatus().name(),
                txLog.getContextSnapshot(),
                toTimestamp(txLog.getTryStartedAt()),
                toTimestamp(txLog.getTryCompletedAt()),
                toTimestamp(txLog.getFinishedAt()),
                txLog.getRetryCount(),
                truncate(txLog.getLastError(), 1024)
        );
        if (log.isDebugEnabled()) {
            log.debug("TCC log saved: xid={}, branchId={}, status={}",
                    txLog.getXid(), txLog.getBranchId(), txLog.getStatus());
        }
    }

    /**
     * 更新分支事务状态
     *
     * @param xid      全局事务 ID
     * @param branchId 分支事务 ID
     * @param status   新状态
     */
    @Override
    public void updateStatus(String xid, String branchId, TccBranchStatus status) {
        String sql = String.format(SQL_UPDATE_STATUS, tableName);
        Timestamp finishedAt = status.isFinal() ? Timestamp.valueOf(LocalDateTime.now()) : null;
        jdbcTemplate.update(sql, status.name(), finishedAt, xid, branchId);
        if (log.isDebugEnabled()) {
            log.debug("TCC log status updated: xid={}, branchId={}, status={}", xid, branchId, status);
        }
    }

    /**
     * 根据 XID 和分支 ID 查询事务日志
     *
     * @param xid      全局事务 ID
     * @param branchId 分支事务 ID
     * @return 事务日志（Optional）
     */
    @Override
    public Optional<TccTransactionLog> findByXidAndBranchId(String xid, String branchId) {
        String sql = String.format(SQL_FIND_BY_XID_BRANCH, tableName);
        List<TccTransactionLog> results = jdbcTemplate.query(sql, rowMapper, xid, branchId);
        return Response.isEmpty() ? Optional.empty() : Optional.of(Response.get(0));
    }

    /**
     * 查询超时未完成的分支事务（用于恢复扫描）
     *
     * <p>扫描策略：查找状态为 {@code TRIED} 且 {@code try_completed_at < threshold} 的记录。
     * 利用数据库索引 {@code idx_status_updated} 加速查询，避免全表扫描。
     *
     * @param threshold 超时阈值，早于此时间的 TRIED 状态分支需要恢复
     * @return 超时分支列表
     */
    @Override
    public List<TccTransactionLog> findTimeoutPending(LocalDateTime threshold) {
        String sql = String.format(SQL_FIND_TIMEOUT_PENDING, tableName);
        return jdbcTemplate.query(sql, rowMapper,
                TccBranchStatus.TRIED.name(), Timestamp.valueOf(threshold));
    }

    /**
     * 删除已完成的分支事务日志（终态清理）
     *
     * @param xid      全局事务 ID
     * @param branchId 分支事务 ID
     */
    @Override
    public void delete(String xid, String branchId) {
        String sql = String.format(SQL_DELETE, tableName);
        jdbcTemplate.update(sql, xid, branchId);
        if (log.isDebugEnabled()) {
            log.debug("TCC log deleted: xid={}, branchId={}", xid, branchId);
        }
    }

    // ============= 私有辅助方法 =============

    private TccTransactionLog mapRow(ResultSet rs) throws SQLException {
        String xid = rs.getString("xid");
        String branchId = rs.getString("branchId");
        String transactionName = rs.getString("transaction_name");
        TccTransactionLog logEntry = new TccTransactionLog(xid, branchId, transactionName);

        String status = rs.getString("status");
        if (status != null) {
            try {
                logEntry.setStatus(TccBranchStatus.valueOf(status));
            } catch (IllegalArgumentException e) {
                log.warn("Unknown TCC branch status in DB: {}, fallback to INIT", status);
            }
        }

        logEntry.setContextSnapshot(rs.getString("context_snapshot"));
        logEntry.setTryStartedAt(toLocalDateTime(rs.getTimestamp("try_started_at")));
        logEntry.setTryCompletedAt(toLocalDateTime(rs.getTimestamp("try_completed_at")));
        logEntry.setFinishedAt(toLocalDateTime(rs.getTimestamp("finished_at")));

        int retryCount = rs.getInt("retry_count");
        for (int i = 0; i < retryCount; i++) {
            logEntry.incrementRetryCount();
        }

        logEntry.setLastError(rs.getString("last_error"));
        return logEntry;
    }

    private static Timestamp toTimestamp(LocalDateTime time) {
        return time == null ? null : Timestamp.valueOf(time);
    }

    private static LocalDateTime toLocalDateTime(Timestamp ts) {
        return ts == null ? null : ts.toLocalDateTime();
    }

    private static String truncate(String s, int maxLength) {
        if (s == null) return null;
        return s.length() > maxLength ? s.substring(0, maxLength) : s;
    }
}
