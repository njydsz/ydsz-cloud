package com.njydsz.common.seata.impl;

import com.njydsz.common.seata.api.TccTransactionDialectProvider;

/**
 * PostgreSQL 数据库方言提供者（9.5+）
 *
 * <p>使用 PostgreSQL 专有的 {@code INSERT ... ON CONFLICT (columns) DO UPDATE SET} 语法实现 UPSERT，
 * 兼容 PostgreSQL 9.5 及以上版本。
 *
 * <p><b>注意</b>：此实现为项目 PostgreSQL 16+ 环境优化，需配合唯一约束
 * {@code UNIQUE (xid, branch_id)} 使用。
 *
 * @author ydsz-team
 * @since 1.3.0
 */
public class PostgresqlTransactionDialectProvider implements TccTransactionDialectProvider {

    private final String tableName;

    /**
     * 构造 PostgreSQL 方言提供者
     *
     * @param tableName 事务日志表名
     */
    public PostgresqlTransactionDialectProvider(String tableName) {
        this.tableName = tableName;
    }

    /**
     * 获取 PostgreSQL 风格的 UPSERT SQL
     *
     * <p>使用 {@code INSERT ... ON CONFLICT (xid, branch_id) DO UPDATE SET} 语法，
     * 需保证表上有 UNIQUE (xid, branch_id) 约束。
     */
    @Override
    public String getUpsertSql() {
        return String.format(
            "INSERT INTO %s (xid, branch_id, transaction_name, status, context_snapshot, " +
            "                try_started_at, try_completed_at, finished_at, retry_count, last_error, " +
            "                created_at, updated_at) " +
            "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, NOW(), NOW()) " +
            "ON CONFLICT (xid, branch_id) DO UPDATE SET " +
            "    status = EXCLUDED.status, " +
            "    context_snapshot = EXCLUDED.context_snapshot, " +
            "    try_started_at = EXCLUDED.try_started_at, " +
            "    try_completed_at = EXCLUDED.try_completed_at, " +
            "    finished_at = EXCLUDED.finished_at, " +
            "    retry_count = EXCLUDED.retry_count, " +
            "    last_error = EXCLUDED.last_error, " +
            "    updated_at = NOW()",
            tableName);
    }

    /**
     * 获取更新状态的 SQL
     */
    @Override
    public String getUpdateStatusSql() {
        return String.format(
            "UPDATE %s SET status = ?, finished_at = ?, updated_at = NOW() WHERE xid = ? AND branch_id = ?",
            tableName);
    }

    /**
     * 获取按 XID 和 branch_id 查询的 SQL
     */
    @Override
    public String getFindByXidBranchSql() {
        return String.format(
            "SELECT xid, branch_id, transaction_name, status, context_snapshot, " +
            "       try_started_at, try_completed_at, finished_at, retry_count, last_error " +
            "FROM %s WHERE xid = ? AND branch_id = ?",
            tableName);
    }

    /**
     * 获取查询超时未完成事务的 SQL
     */
    @Override
    public String getFindTimeoutPendingSql() {
        return String.format(
            "SELECT xid, branch_id, transaction_name, status, context_snapshot, " +
            "       try_started_at, try_completed_at, finished_at, retry_count, last_error " +
            "FROM %s WHERE status = ? AND try_completed_at < ?",
            tableName);
    }

    /**
     * 获取分页查询超时未完成事务的 SQL（PostgreSQL LIMIT 语法）
     */
    @Override
    public String getFindTimeoutPendingPagedSql() {
        return String.format(
            "SELECT xid, branch_id, transaction_name, status, context_snapshot, " +
            "       try_started_at, try_completed_at, finished_at, retry_count, last_error " +
            "FROM %s WHERE status = ? AND try_completed_at < ? ORDER BY try_completed_at ASC LIMIT ?",
            tableName);
    }

    /**
     * 获取统计超时未完成事务数量的 SQL
     */
    @Override
    public String getCountTimeoutPendingSql() {
        return String.format(
            "SELECT COUNT(*) FROM %s WHERE status = ? AND try_completed_at < ?",
            tableName);
    }

    /**
     * 获取删除事务日志的 SQL
     */
    @Override
    public String getDeleteSql() {
        return String.format(
            "DELETE FROM %s WHERE xid = ? AND branch_id = ?",
            tableName);
    }
}
