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
import com.njydsz.common.seata.api.TccTransactionDialectProvider;
import com.njydsz.common.seata.api.TccTransactionLog;
import com.njydsz.common.seata.api.TccTransactionLogStore;

/**
 * 基于数据库（JDBC）的 TCC 事务日志存储
 *
 * <p>使用关系型数据库表 {@code tcc_transaction_log} 持久化 TCC 分支事务状态， 适用于以下生产场景：
 *
 * <ul>
 *   <li><b>无 Redis 环境</b>：团队不具备 Redis 基础设施但需要持久化事务日志
 *   <li><b>强持久化需求</b>：事务日志需要落盘且可追溯，满足合规审计要求
 *   <li><b>跨服务共享</b>：多服务实例共享数据库，任一实例均可执行 Confirm/Cancel 恢复
 * </ul>
 *
 * <p><b>P0-3 修复</b>：引入 {@link TccTransactionDialectProvider} 接口适配多数据库， 解决原 MySQL 专有语法与"兼容
 * PG"声明矛盾的问题。 仅当选择 {@code db} 且类路径存在 {@code JdbcTemplate} 时注册。
 *
 * <p><div> MySQL / PostgreSQL 兼容 DDL 见 {@link #getMysqlDdl()} / {@link #getPostgresqlDdl()}。 </div>
 *
 * <p><b>实现要点：</b>
 *
 * <ul>
 *   <li>使用方言提供者适配不同数据库的 UPSERT 语法（MySQL ON DUPLICATE KEY UPDATE / PG ON CONFLICT）
 *   <li>查询超时记录利用索引 {@code idx_status_updated} 加速扫描
 *   <li>终态日志保留用于审计，需定期清理（建议通过定时任务删除 {@code finished_at < NOW() - 90d} 的日志）
 * </ul>
 *
 * <p><b>配置方式：</b>
 *
 * <pre>{@code
 * ydsz:
 *   seata:
 *     tcc-log-store: db
 *     tcc-log-db-table: tcc_transaction_log
 *     tcc-log-db-dialect: mysql  # mysql / postgresql，默认自动检测
 * }</pre>
 *
 * @author ydsz-team
 * @since 1.0.0
 */
public class DbTccTransactionLogStore implements TccTransactionLogStore {

  private static final Logger LOG = LoggerFactory.getLogger(DbTccTransactionLogStore.class);

  private final JdbcTemplate jdbcTemplate;
  private final String tableName;
  private final TccTransactionDialectProvider dialectProvider;

  private final RowMapper<TccTransactionLog> rowMapper = (rs, rowNum) -> mapRow(rs);

  /**
   * 构造 DB 版 TCC 事务日志存储（自动检测数据库方言）
   *
   * @param jdbcTemplate JDBC 模板（不能为 null）
   * @param tableName 表名（可为 null，默认 {@code tcc_transaction_log}）
   */
  public DbTccTransactionLogStore(JdbcTemplate jdbcTemplate, String tableName) {
    this(jdbcTemplate, tableName, (TccTransactionDialectProvider) null);
  }

  /**
   * 构造 DB 版 TCC 事务日志存储（指定方言提供者）
   *
   * @param jdbcTemplate JDBC 模板（不能为 null）
   * @param tableName 表名（可为 null，默认 {@code tcc_transaction_log}）
   * @param dialect 数据库方言字符串（mysql / postgresql），为空则自动检测
   */
  public DbTccTransactionLogStore(JdbcTemplate jdbcTemplate, String tableName, String dialect) {
    this(jdbcTemplate, tableName, createDialectFromString(dialect, jdbcTemplate, tableName));
  }

  /**
   * 构造 DB 版 TCC 事务日志存储（指定方言提供者）
   *
   * @param jdbcTemplate JDBC 模板（不能为 null）
   * @param tableName 表名（可为 null，默认 {@code tcc_transaction_log}）
   * @param dialectProvider 数据库方言提供者（可为 null，默认自动检测）
   */
  public DbTccTransactionLogStore(
      JdbcTemplate jdbcTemplate, String tableName, TccTransactionDialectProvider dialectProvider) {
    this.jdbcTemplate = jdbcTemplate;
    this.tableName = (tableName == null || tableName.isBlank()) ? "tcc_transaction_log" : tableName;
    this.dialectProvider =
        dialectProvider != null ? dialectProvider : detectDialect(jdbcTemplate, this.tableName);
  }

  /** 从字符串创建方言提供者 */
  private static TccTransactionDialectProvider createDialectFromString(
      String dialect, JdbcTemplate jdbcTemplate, String tableName) {
    if (dialect == null || dialect.isBlank()) {
      return detectDialect(jdbcTemplate, tableName);
    }
    switch (dialect.toLowerCase()) {
      case "postgresql":
        return new PostgresqlTransactionDialectProvider(tableName);
      case "mysql":
        return new MysqlTransactionDialectProvider(tableName);
      default:
        LOG.warn("Unknown dialect: {}, fallback to auto detection", dialect);
        return detectDialect(jdbcTemplate, tableName);
    }
  }

  /**
   * 自动检测数据库方言
   *
   * <p>通过 {@link JdbcTemplate} 获取数据源元数据，判断数据库类型并返回对应的方言提供者。
   *
   * @param jdbcTemplate JDBC 模板
   * @param tableName 表名
   * @return 对应的数据库方言提供者
   */
  private static TccTransactionDialectProvider detectDialect(
      JdbcTemplate jdbcTemplate, String tableName) {
    try {
      return jdbcTemplate.execute(
          (org.springframework.jdbc.core.ConnectionCallback<TccTransactionDialectProvider>)
              conn -> {
                String databaseProductName = conn.getMetaData().getDatabaseProductName();
                LOG.info(
                    "Detected database product: {}, selecting dialect provider",
                    databaseProductName);
                if (databaseProductName != null
                    && databaseProductName.toLowerCase().contains("postgresql")) {
                  return new PostgresqlTransactionDialectProvider(tableName);
                }
                // 默认使用 MySQL 方言
                return new MysqlTransactionDialectProvider(tableName);
              });
    } catch (Exception e) {
      LOG.warn("Failed to detect database dialect, fallback to MySQL: {}", e.getMessage());
      return new MysqlTransactionDialectProvider(tableName);
    }
  }

  /** 获取 MySQL DDL（供初始化脚本参考） */
  public static String getMysqlDdl() {
    return """
                CREATE TABLE tcc_transaction_log (
                  id              BIGINT       AUTO_INCREMENT PRIMARY KEY,
                  xid             VARCHAR(128) NOT NULL,
                  branch_id       VARCHAR(128) NOT NULL,
                  transaction_name VARCHAR(255),
                  status          VARCHAR(32)  NOT NULL,
                  context_snapshot TEXT,
                  try_started_at   TIMESTAMP    NULL,
                  try_completed_at TIMESTAMP    NULL,
                  finished_at      TIMESTAMP    NULL,
                  retry_count      INT          NOT NULL DEFAULT 0,
                  last_error       VARCHAR(1024),
                  created_at       TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
                  updated_at       TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
                  UNIQUE KEY uk_xid_branch (xid, branch_id)
                );
                CREATE INDEX idx_status_updated ON tcc_transaction_log (status, updated_at);
                """;
  }

  /** 获取 PostgreSQL DDL（供初始化脚本参考） */
  public static String getPostgresqlDdl() {
    return """
                CREATE TABLE tcc_transaction_log (
                  id              BIGSERIAL    PRIMARY KEY,
                  xid             VARCHAR(128) NOT NULL,
                  branch_id       VARCHAR(128) NOT NULL,
                  transaction_name VARCHAR(255),
                  status          VARCHAR(32)  NOT NULL,
                  context_snapshot TEXT,
                  try_started_at   TIMESTAMP    NULL,
                  try_completed_at TIMESTAMP    NULL,
                  finished_at      TIMESTAMP    NULL,
                  retry_count      INT          NOT NULL DEFAULT 0,
                  last_error       VARCHAR(1024),
                  created_at       TIMESTAMP    NOT NULL DEFAULT NOW(),
                  updated_at       TIMESTAMP    NOT NULL DEFAULT NOW(),
                  CONSTRAINT uk_xid_branch UNIQUE (xid, branch_id)
                );
                CREATE INDEX idx_status_updated ON tcc_transaction_log (status, updated_at);
                """;
  }

  /**
   * 保存事务日志（Try 前调用）
   *
   * <p>使用方言提供者提供的 UPSERT SQL 保证幂等性， 若已存在该分支记录，则更新状态和时间戳。
   *
   * @param txLog 事务日志
   */
  @Override
  public void save(TccTransactionLog txLog) {
    String sql = dialectProvider.getUpsertSql();
    jdbcTemplate.update(
        sql,
        txLog.getXid(),
        txLog.getBranchId(),
        txLog.getTransactionName(),
        txLog.getStatus() == null ? TccBranchStatus.INIT.name() : txLog.getStatus().name(),
        txLog.getContextSnapshot(),
        toTimestamp(txLog.getTryStartedAt()),
        toTimestamp(txLog.getTryCompletedAt()),
        toTimestamp(txLog.getFinishedAt()),
        txLog.getRetryCount(),
        truncate(txLog.getLastError(), 1024));
    if (LOG.isDebugEnabled()) {
      LOG.debug(
          "TCC log saved: xid={}, branchId={}, status={}",
          txLog.getXid(),
          txLog.getBranchId(),
          txLog.getStatus());
    }
  }

  /**
   * 更新分支事务状态
   *
   * @param xid 全局事务 ID
   * @param branchId 分支事务 ID
   * @param status 新状态
   */
  @Override
  public void updateStatus(String xid, String branchId, TccBranchStatus status) {
    String sql = dialectProvider.getUpdateStatusSql();
    Timestamp finishedAt = status.isFinal() ? Timestamp.valueOf(LocalDateTime.now()) : null;
    jdbcTemplate.update(sql, status.name(), finishedAt, xid, branchId);
    if (LOG.isDebugEnabled()) {
      LOG.debug("TCC log status updated: xid={}, branchId={}, status={}", xid, branchId, status);
    }
  }

  /**
   * 根据 XID 和分支 ID 查询事务日志
   *
   * @param xid 全局事务 ID
   * @param branchId 分支事务 ID
   * @return 事务日志（Optional）
   */
  @Override
  public Optional<TccTransactionLog> findByXidAndBranchId(String xid, String branchId) {
    String sql = dialectProvider.getFindByXidBranchSql();
    List<TccTransactionLog> results = jdbcTemplate.query(sql, rowMapper, xid, branchId);
    return results.isEmpty() ? Optional.empty() : Optional.of(results.get(0));
  }

  /**
   * 查询超时未完成的分支事务（用于恢复扫描）
   *
   * <p>扫描策略：查找状态为 {@code TRIED} 且 {@code try_completed_at < threshold} 的记录。 利用数据库索引 {@code
   * idx_status_updated} 加速查询，避免全表扫描。
   *
   * @param threshold 超时阈值，早于此时间的 TRIED 状态分支需要恢复
   * @return 超时分支列表
   */
  @Override
  public List<TccTransactionLog> findTimeoutPending(LocalDateTime threshold) {
    String sql = dialectProvider.getFindTimeoutPendingSql();
    return jdbcTemplate.query(
        sql, rowMapper, TccBranchStatus.TRIED.name(), Timestamp.valueOf(threshold));
  }

  /**
   * 分页查询超时未完成的分支事务（P1-2 新增）
   *
   * <p>使用 LIMIT/OFFSET 分页，避免一次性加载全部超时事务到内存。
   *
   * @param threshold 超时阈值
   * @param limit 单次返回最大记录数
   * @return 超时分支列表
   */
  @Override
  public List<TccTransactionLog> findTimeoutPendingPaged(LocalDateTime threshold, int limit) {
    String sql = dialectProvider.getFindTimeoutPendingPagedSql();
    return jdbcTemplate.query(
        sql, rowMapper, TccBranchStatus.TRIED.name(), Timestamp.valueOf(threshold), limit);
  }

  /**
   * 查询超时未完成的分支事务数量（高效计数，不加载完整日志）
   *
   * <p>使用 {@code SELECT COUNT(*)} 直接获取计数，避免加载完整事务日志到内存。
   *
   * @param threshold 超时阈值，早于此时间的 TRIED 状态分支需要恢复
   * @return 超时未完成的分支事务数量
   */
  @Override
  public long countTimeoutPending(LocalDateTime threshold) {
    String sql = dialectProvider.getCountTimeoutPendingSql();
    Long count =
        jdbcTemplate.queryForObject(
            sql, Long.class, TccBranchStatus.TRIED.name(), Timestamp.valueOf(threshold));
    return count != null ? count : 0L;
  }

  /**
   * 删除已完成的分支事务日志（终态清理）
   *
   * @param xid 全局事务 ID
   * @param branchId 分支事务 ID
   */
  @Override
  public void delete(String xid, String branchId) {
    String sql = dialectProvider.getDeleteSql();
    jdbcTemplate.update(sql, xid, branchId);
    if (LOG.isDebugEnabled()) {
      LOG.debug("TCC log deleted: xid={}, branchId={}", xid, branchId);
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
        LOG.warn("Unknown TCC branch status in DB: {}, fallback to INIT", status);
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
