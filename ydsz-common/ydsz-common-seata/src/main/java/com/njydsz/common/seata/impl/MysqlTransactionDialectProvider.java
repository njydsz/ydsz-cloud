package com.njydsz.common.seata.impl;

import com.njydsz.common.seata.api.TccTransactionDialectProvider;

/**
 * MySQL 数据库方言提供者
 *
 * <p>使用 MySQL 专有的 {@code INSERT ... ON DUPLICATE KEY UPDATE} 语法实现 UPSERT， 性能优于标准 SQL 的 {@code MERGE
 * INTO}。
 *
 * @author ydsz-team
 * @since 1.0.0
 */
public class MysqlTransactionDialectProvider implements TccTransactionDialectProvider {

  private final String tableName;

  /**
   * 构造 MySQL 方言提供者
   *
   * @param tableName 事务日志表名
   */
  public MysqlTransactionDialectProvider(String tableName) {
    this.tableName = tableName;
  }

  /**
   * 获取 MySQL 风格的 UPSERT SQL
   *
   * <p>使用 {@code INSERT ... ON DUPLICATE KEY UPDATE} 语法， 需保证表上有 UNIQUE KEY (xid, branch_id)。
   */
  @Override
  public String getUpsertSql() {
    return String.format(
        """
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
                """,
        tableName);
  }

  /** 获取更新状态的 SQL */
  @Override
  public String getUpdateStatusSql() {
    return String.format(
        """
                UPDATE %s SET status = ?, finished_at = ?, updated_at = CURRENT_TIMESTAMP
                WHERE xid = ? AND branch_id = ?
                """,
        tableName);
  }

  /** 获取按 XID 和 branch_id 查询的 SQL */
  @Override
  public String getFindByXidBranchSql() {
    return String.format(
        """
                SELECT xid, branch_id, transaction_name, status, context_snapshot,
                       try_started_at, try_completed_at, finished_at, retry_count, last_error
                FROM %s WHERE xid = ? AND branch_id = ?
                """,
        tableName);
  }

  /** 获取查询超时未完成事务的 SQL */
  @Override
  public String getFindTimeoutPendingSql() {
    return String.format(
        "SELECT xid, branch_id, transaction_name, status, context_snapshot, "
            + "       try_started_at, try_completed_at, finished_at, retry_count, last_error "
            + "FROM %s WHERE status = ? AND try_completed_at < ?",
        tableName);
  }

  /** 获取分页查询超时未完成事务的 SQL（MySQL LIMIT 语法） */
  @Override
  public String getFindTimeoutPendingPagedSql() {
    return String.format(
        "SELECT xid, branch_id, transaction_name, status, context_snapshot, "
            + "       try_started_at, try_completed_at, finished_at, retry_count, last_error "
            + "FROM %s WHERE status = ? AND try_completed_at < ? ORDER BY try_completed_at ASC LIMIT ?",
        tableName);
  }

  /** 获取统计超时未完成事务数量的 SQL */
  @Override
  public String getCountTimeoutPendingSql() {
    return String.format(
        """
                SELECT COUNT(*) FROM %s WHERE status = ? AND try_completed_at < ?
                """,
        tableName);
  }

  /** 获取删除事务日志的 SQL */
  @Override
  public String getDeleteSql() {
    return String.format(
        """
                DELETE FROM %s WHERE xid = ? AND branch_id = ?
                """,
        tableName);
  }
}
